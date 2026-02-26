package de.mhus.nimbus.world.generator.security;

import de.mhus.nimbus.shared.security.JwtService;
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

    public GeneratorAccessFilter(JwtService jwtService, WSessionService sessionService, AccessSettings accessProperties, RegionSettings regionProperties, SSettingsService settingsService) {
        super(jwtService, sessionService, regionProperties);
        this.accessProperties = accessProperties;
        this.settingsService = settingsService;
    }

    @PostConstruct
    private void init() {
        settingMcpToken = settingsService.getString("mcp.token", "");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // MCP endpoints: check MCP token instead of session auth
        if (requestUri.startsWith("/sse") || requestUri.startsWith("/mcp/")) {
            String mcpToken = settingMcpToken.get();

            if (Strings.isBlank(mcpToken)) {
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.equals("Bearer " + mcpToken)) {
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
