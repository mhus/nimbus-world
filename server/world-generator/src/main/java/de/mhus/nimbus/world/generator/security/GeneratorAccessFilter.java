package de.mhus.nimbus.world.generator.security;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.service.MetricService;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingString;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSessionService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Access filter for world-generator service.
 * Extends AccessFilterBase to validate sessionToken cookies and attach
 * session information to requests.
 *
 * MCP endpoints (/sse, /mcp/) are protected by a configurable API token
 * from SSettingsService ("mcp.token"). If the token is blank, MCP access
 * is unrestricted.
 *
 * All other endpoints require standard session authentication.
 */
@Component
@Slf4j
public class GeneratorAccessFilter extends AccessFilterBase {

    private final AccessSettings accessProperties;
    private final SSettingsService settingsService;

    private SettingString settingMcpToken;

    public GeneratorAccessFilter(JwtService jwtService, WSessionService sessionService, AccessSettings accessProperties, RegionSettings regionProperties, SSettingsService settingsService, MetricService metricService) {
        super(jwtService, sessionService, regionProperties, metricService);
        this.accessProperties = accessProperties;
        this.settingsService = settingsService;
    }

    @PostConstruct
    private void init() {
        settingMcpToken = settingsService.getString("mcp.token", "");
        if (Strings.isBlank(settingMcpToken.get())) {
            log.warn("SECURITY: 'mcp.token' is not set - MCP endpoints (/sse, /mcp/) are exposed "
                    + "WITHOUT authentication. Set 'mcp.token' or restrict network access to this port.");
        }
    }

    /**
     * Matches the MCP surface exactly ({@code /sse}, {@code /sse/...}, {@code /mcp},
     * {@code /mcp/...}) so unrelated paths like {@code /mcpanything} are not treated
     * as MCP endpoints and fall through to standard authentication.
     */
    private boolean isMcpPath(String uri) {
        return uri.equals("/sse") || uri.startsWith("/sse/")
                || uri.equals("/mcp") || uri.startsWith("/mcp/");
    }

    private boolean tokenMatches(String authHeader, String mcpToken) {
        if (authHeader == null) {
            return false;
        }
        byte[] provided = authHeader.getBytes(StandardCharsets.UTF_8);
        byte[] expected = ("Bearer " + mcpToken).getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison to avoid a timing side-channel on the token.
        return MessageDigest.isEqual(provided, expected);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // MCP endpoints: check MCP token instead of session auth
        if (isMcpPath(requestUri)) {
            String mcpToken = settingMcpToken.get();

            if (Strings.isBlank(mcpToken)) {
                log.warn("MCP: unauthenticated access to {} (mcp.token not set)", requestUri);
                filterChain.doFilter(request, response);
                return;
            }

            if (tokenMatches(request.getHeader("Authorization"), mcpToken)) {
                filterChain.doFilter(request, response);
                return;
            }

            log.warn("MCP: Unauthorized request to {}", requestUri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        // All other endpoints: standard session/JWT authentication
        super.doFilterInternal(request, response, filterChain);
    }

    @Override
    protected boolean shouldRequireAuthentication(String requestUri, String method) {
        // All non-MCP endpoints require authentication (MCP is handled in doFilterInternal)
        return true;
    }

    @Override
    protected String getLoginUrl() {
        return accessProperties.getLoginUrl();
    }
}
