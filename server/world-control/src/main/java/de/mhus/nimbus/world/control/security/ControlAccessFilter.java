package de.mhus.nimbus.world.control.security;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.security.KeyIntent;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Access filter for world-control service.
 * Extends AccessFilterBase to validate sessionToken cookies and attach
 * session information to requests.
 *
 * Enforces authentication for all endpoints except:
 * - /api/aaa/authorize (cookie setting endpoint)
 * - /api/aaa/devlogin (development login endpoint)
 * - /control/aaa/status (status check endpoint)
 * - /control/aaa/login DELETE (logout endpoint)
 */
@Component
@Slf4j
public class ControlAccessFilter extends AccessFilterBase {

    private final AccessSettings accessProperties;
    private final JwtService jwtService;
    private final SSettingsService settingsService;

    /**
     * Pattern for public asset paths: /control/worlds/{worldId}/assets/{p|rp}:**
     * - worldId is a single path segment without slashes (see WorldId class)
     * - Only 'p:' (public) and 'rp:' (readonly-public) prefixes are allowed
     * - Colon ':' is mandatory after prefix
     * - After colon, any path is allowed (with or without leading slash)
     */
    private static final Pattern PUBLIC_ASSET_PATTERN = Pattern.compile(
            "^/control/worlds/[^/]+/assets/(p|rp):.*$"
    );

    public ControlAccessFilter(JwtService jwtService, WSessionService sessionService, AccessSettings accessProperties, RegionSettings regionProperties, SSettingsService settingsService) {
        super(jwtService, sessionService, regionProperties);
        this.accessProperties = accessProperties;
        this.jwtService = jwtService;
        this.settingsService = settingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Universe-to-sector communication: validate Universe Bearer token separately
        if (request.getRequestURI().startsWith("/control/universe/") && !"OPTIONS".equals(request.getMethod())) {
            if (validateUniverseBearer(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            // Fall through to normal auth (allows admin UI access via session cookie)
        }
        super.doFilterInternal(request, response, filterChain);
    }

    private boolean validateUniverseBearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return false;
        String token = auth.substring(7).trim();
        String sectorName = settingsService.getStringValue("universe.name", "");
        if (sectorName.isBlank()) return false;
        KeyIntent universeIntent = KeyIntent.of(sectorName, KeyIntent.MAIN_JWT_TOKEN);
        Optional<Jws<Claims>> result = jwtService.validateTokenWithPublicKey(token, KeyType.UNIVERSE, universeIntent);
        if (result.isPresent()) {
            log.info("Universe bearer token validated for sector '{}'", sectorName);
            request.setAttribute(AccessFilterBase.ATTR_IS_AUTHENTICATED, true);
            request.setAttribute(AccessFilterBase.ATTR_IS_AGENT, true);
            request.setAttribute(AccessFilterBase.ATTR_USER_ID, "universe:" + sectorName);
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldRequireAuthentication(String requestUri, String method) {
        // Allow access to authentication-related endpoints
        if (requestUri.startsWith("/control/aaa/authorize")) {
            return false;
        }
        if (requestUri.startsWith("/control/aaa/devlogin")) {
            return false;
        }
        if (requestUri.startsWith("/control/aaa/status")) {
            return false;
        }
        // Allow access to public endpoints (session data is loaded but not validated strictly)
        if (requestUri.startsWith("/control/public/")) {
            return false;
        }

        // Allow public read-only access to public/readonly assets
        // Pattern: /control/worlds/{worldId}/assets/p:** or /control/worlds/{worldId}/assets/rp:**
        // Note: Colon ':' after p/rp is mandatory
        if (isPublicAssetPath(requestUri) && isReadOnlyMethod(method)) {
            log.debug("Allowing public read-only access to asset: {} {}", method, requestUri);
            return false;
        }

//        // Allow DELETE on /control/aaa/login (logout)
//        if (requestUri.startsWith("/control/aaa/login") && "DELETE".equals(method)) {
//            return false;
//        }

        // All other endpoints require authentication
        return true;
    }

    /**
     * Check if the request URI is a public asset path.
     * Uses regex pattern to ensure exact structure: /control/worlds/{worldId}/assets/{p|rp}:**
     *
     * Secure implementation that prevents path traversal attacks by:
     * - Ensuring worldId is a single path segment (no slashes)
     * - Validating exact path structure with regex
     * - Only allowing 'p:' and 'rp:' asset prefixes (colon is mandatory)
     *
     * @param requestUri The request URI to check
     * @return true if the URI matches the public asset pattern
     */
    private boolean isPublicAssetPath(String requestUri) {
        return PUBLIC_ASSET_PATTERN.matcher(requestUri).matches();
    }

    /**
     * Check if the HTTP method is read-only (GET, HEAD, OPTIONS).
     */
    private boolean isReadOnlyMethod(String method) {
        return "GET".equalsIgnoreCase(method)
            || "HEAD".equalsIgnoreCase(method)
            || "OPTIONS".equalsIgnoreCase(method);
    }

    @Override
    protected boolean shouldAcceptClosedSessions(String requestUri) {
        // Accept CLOSED sessions for public endpoints (e.g., teleport-login)
        if (requestUri.startsWith("/control/public/")) {
            return true;
        }
        return false;
    }

    @Override
    protected String getLoginUrl() {
        return accessProperties.getLoginUrl();
    }

    @Override
    protected boolean isPathAllowedForRole(String requestUri, SessionTokenClaims claims) {
        // PLAYER actors can only access /control/player/** and /control/aaa/** endpoints
        // /control/aaa/ is needed so players can re-login (devlogin, authorize, etc.)
        if ("PLAYER".equalsIgnoreCase(claims.role())) {
            boolean isAllowed = requestUri.startsWith("/control/player/")
                    || requestUri.startsWith("/control/aaa/")
                    || isPublicAssetPath(requestUri);
            if (!isAllowed) {
                log.debug("PLAYER actor attempted to access non-player endpoint: userId={}, path={}",
                        claims.userId(), requestUri);
            }
            return isAllowed;
        }

        // All other actors (EDITOR, ADMIN, etc.) have unrestricted access
        return true;
    }
}
