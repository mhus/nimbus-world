package de.mhus.nimbus.world.control.security;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    public ControlAccessFilter(JwtService jwtService, WSessionService sessionService, AccessSettings accessProperties, RegionSettings regionProperties) {
        super(jwtService, sessionService, regionProperties);
        this.accessProperties = accessProperties;
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
        // PLAYER actors can only access /control/player/** endpoints
        if ("PLAYER".equalsIgnoreCase(claims.role())) {
            boolean isPlayerPath = requestUri.startsWith("/control/player/");
            if (!isPlayerPath) {
                log.debug("PLAYER actor attempted to access non-player endpoint: userId={}, path={}",
                        claims.userId(), requestUri);
            }
            return isPlayerPath;
        }

        // All other actors (EDITOR, ADMIN, etc.) have unrestricted access
        return true;
    }
}
