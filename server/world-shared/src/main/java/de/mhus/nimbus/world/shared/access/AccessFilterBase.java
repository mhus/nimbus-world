package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.security.KeyIntent;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.session.WSessionStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Base filter for access control using sessionToken cookies.
 *
 * Reads sessionToken from httpOnly cookie, validates it, and attaches
 * session information to the request for downstream processing.
 *
 * Currently in logging-only mode - does not block unauthorized requests.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AccessFilterBase extends OncePerRequestFilter {

    // Request attributes for downstream use
    public static final String ATTR_USER_ID = "accessUserId";
    public static final String ATTR_WORLD_ID = "accessWorldId";
    public static final String ATTR_CHARACTER_ID = "accessCharacterId";
    public static final String ATTR_ROLE = "accessRole";
    public static final String ATTR_SESSION_ID = "accessSessionId";
    public static final String ATTR_IS_AGENT = "accessIsAgent";
    public static final String ATTR_IS_AUTHENTICATED = "accessIsAuthenticated";

    private final JwtService jwtService;
    private final WSessionService sessionService;
    private final RegionSettings regionProperties;

    /**
     * Determines if the given request path requires authentication.
     * Subclasses can override this to define path-specific authentication rules.
     *
     * @param requestUri The request URI
     * @param method The HTTP method
     * @return true if authentication is required, false if the path is exempt
     */
    protected abstract boolean shouldRequireAuthentication(String requestUri, String method);

    /**
     * Determines if CLOSED sessions should be accepted for the given request path.
     * Subclasses can override this to allow CLOSED sessions for specific paths (e.g., logout, teleport).
     *
     * @param requestUri The request URI
     * @return true if CLOSED sessions are acceptable, false if they should be rejected
     */
    protected boolean shouldAcceptClosedSessions(String requestUri) {
        // Default: reject CLOSED sessions
        return false;
    }

    /**
     * Validates if the authenticated session claims are acceptable for this service.
     * Subclasses can override this to add additional validation (e.g., reject agent tokens).
     *
     * @param claims The validated session token claims
     * @return true if claims are acceptable, false if they should be rejected
     */
    protected boolean isClaimsAcceptable(SessionTokenClaims claims) {
        // Default: accept all valid claims
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        log.debug("AccessFilter processing request: {} {} from host: {}, origin: {}",
                request.getMethod(), request.getRequestURI(),
                request.getHeader("Host"), request.getHeader("Origin"));

        // Always allow OPTIONS requests (CORS preflight) - they don't have cookies
        if ("OPTIONS".equals(request.getMethod())) {
            log.debug("Allowing OPTIONS request (CORS preflight)");
            filterChain.doFilter(request, response);
            return;
        }

        boolean authenticated = false;

        try {
            // 1. Check for Bearer token (server-to-server authentication)
            String bearerToken = extractBearerToken(request);
            if (bearerToken != null) {
                log.debug("Bearer token found - validating server-to-server authentication");
                if (validateBearerToken(bearerToken, request)) {
                    authenticated = true;
                    log.info("Server-to-server authentication successful");
                } else {
                    log.warn("Invalid bearer token");
                    request.setAttribute(ATTR_IS_AUTHENTICATED, false);
                }
            }

            // 2. If no bearer token, try cookie-based authentication
            if (!authenticated) {
                String sessionToken = extractSessionTokenFromCookie(request);

                if (sessionToken == null) {
                    log.debug("No sessionToken cookie found");
                    request.setAttribute(ATTR_IS_AUTHENTICATED, false);
                } else {
                    // 3. Validate sessionToken
                    SessionTokenClaims claims = validateSessionToken(sessionToken);

                    if (claims == null) {
                        log.warn("Invalid or expired sessionToken");
                        request.setAttribute(ATTR_IS_AUTHENTICATED, false);
                    } else {
                        // 4. Check if claims are acceptable for this service (e.g., reject agent tokens)
                        if (!isClaimsAcceptable(claims)) {
                            log.warn("Claims not acceptable for this service - agent={}", claims.agent());
                            request.setAttribute(ATTR_IS_AUTHENTICATED, false);
                        } else {
                            // 5. If session login (agent=false), validate Redis session
                            if (!claims.agent() && claims.sessionId() != null) {
                                boolean sessionValid = validateRedisSession(claims, request.getRequestURI());
                                if (!sessionValid) {
                                    log.warn("Redis session validation failed - sessionId={}", claims.sessionId());
                                    request.setAttribute(ATTR_IS_AUTHENTICATED, false);
                                } else {
                                    authenticated = true;
                                }
                            } else {
                                authenticated = true;
                            }

                            if (authenticated) {
                                // 6. Attach claims to request attributes
                                request.setAttribute(ATTR_IS_AUTHENTICATED, true);
                                request.setAttribute(ATTR_IS_AGENT, claims.agent());
                                request.setAttribute(ATTR_USER_ID, claims.userId());
                                request.setAttribute(ATTR_WORLD_ID, claims.worldId());
                                request.setAttribute(ATTR_ROLE, claims.role());
                                request.setAttribute(ATTR_SESSION_ID, claims.sessionId());
                                request.setAttribute(ATTR_CHARACTER_ID, claims.characterId());

                                log.debug("Access validated - userId={}, worldId={}, agent={}, sessionId={}",
                                        claims.userId(), claims.worldId(), claims.agent(), claims.sessionId());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("AccessFilter error: {}", e.getMessage(), e);
            request.setAttribute(ATTR_IS_AUTHENTICATED, false);
            authenticated = false;
        }

        // Allow access to actuator health endpoint
        if (request.getRequestURI().startsWith("/actuator/")) {
            logger.debug("Allowing access to /actuator/ endpoint");
        } else
        if (!authenticated && shouldRequireAuthentication(request.getRequestURI(), request.getMethod())) {
            // Check if authentication is required for this path
            log.warn("Access denied - authentication required for: {} {}", request.getMethod(), request.getRequestURI());
            handleUnauthorized(request, response);
            return;
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Handles unauthorized access by returning a 401 error with HTML page.
     * Subclasses can override this to customize the error response.
     */
    protected void handleUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/html; charset=UTF-8");

        String loginUrl = getLoginUrl();
        String requestUri = request.getRequestURI();

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>401 Unauthorized</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        max-width: 600px;
                        margin: 100px auto;
                        padding: 20px;
                        text-align: center;
                    }
                    h1 { color: #d32f2f; }
                    .info { margin: 20px 0; color: #666; }
                    .login-link {
                        display: inline-block;
                        margin-top: 20px;
                        padding: 10px 20px;
                        background-color: #1976d2;
                        color: white;
                        text-decoration: none;
                        border-radius: 4px;
                    }
                    .login-link:hover { background-color: #1565c0; }
                </style>
            </head>
            <body>
                <h1>401 - Unauthorized</h1>
                <div class="info">
                    <p>You need to be authenticated to access this resource.</p>
                    <p><strong>Request:</strong> %s</p>
                </div>
                <a href="%s" class="login-link">Go to Login</a>
            </body>
            </html>
            """.formatted(requestUri, loginUrl);

        response.getWriter().write(html);
    }

    /**
     * Gets the login URL for unauthorized responses.
     * Subclasses can override this to provide a custom login URL.
     */
    protected abstract String getLoginUrl();

    /**
     * Extracts sessionToken from httpOnly cookie.
     */
    private String extractSessionTokenFromCookie(HttpServletRequest request) {
        // Try to get cookies from request
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            log.debug("Found {} cookies in request", cookies.length);
            for (Cookie cookie : cookies) {
                log.debug("Cookie: name='{}', value='{}...'",
                        cookie.getName(),
                        cookie.getValue() != null ? cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())) : "null");

                if ("sessionToken".equals(cookie.getName())) {
                    log.debug("sessionToken cookie found via getCookies()!");
                    return cookie.getValue();
                }
            }
            log.warn("sessionToken cookie NOT found among {} cookies", cookies.length);
        } else {
            // Fallback: Parse Cookie header manually if getCookies() returns null
            String cookieHeader = request.getHeader("Cookie");
            log.warn("request.getCookies() returned null, parsing Cookie header manually: {}", cookieHeader);

            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                return parseSessionTokenFromHeader(cookieHeader);
            }
        }

        return null;
    }

    /**
     * Manually parses sessionToken from Cookie header string.
     * Fallback when request.getCookies() returns null.
     */
    private String parseSessionTokenFromHeader(String cookieHeader) {
        // Cookie format: "name1=value1; name2=value2; ..."
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String trimmed = cookie.trim();
            if (trimmed.startsWith("sessionToken=")) {
                String value = trimmed.substring("sessionToken=".length());
                log.info("sessionToken found in Cookie header via manual parsing!");
                return value;
            }
        }
        log.warn("sessionToken NOT found in Cookie header");
        return null;
    }

    /**
     * Validates sessionToken JWT and extracts claims.
     */
    private SessionTokenClaims validateSessionToken(String token) {
        try {
            // Parse token to extract regionId (needed for validation)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid token format");
                return null;
            }

            // Decode payload to extract regionId
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            String regionId = extractRegionIdFromJson(payloadJson);

            // Validate token with regionId
            Optional<Jws<Claims>> jwsOpt = jwtService.validateTokenWithPublicKey(
                    token,
                    KeyType.REGION,
                    KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN)
            );

            if (jwsOpt.isEmpty()) {
                log.warn("Token validation failed");
                return null;
            }

            Claims claims = jwsOpt.get().getPayload();

            // Extract claims
            Boolean agent = claims.get("agent", Boolean.class);
            String worldId = claims.get("worldId", String.class);
            String userId = claims.get("userId", String.class);
            String characterId = claims.get("characterId", String.class);
            String role = claims.get("role", String.class);
            String sessionId = claims.get("sessionId", String.class);

            if (agent == null || worldId == null || userId == null) {
                log.warn("Token missing required claims");
                return null;
            }

            return new SessionTokenClaims(agent, worldId, userId, characterId, role, sessionId, regionId);

        } catch (Exception e) {
            log.warn("Token validation error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts regionId from JSON payload.
     */
    private String extractRegionIdFromJson(String json) {
        int regionIdIndex = json.indexOf("\"regionId\"");
        if (regionIdIndex == -1) {
            return null;
        }

        int valueStart = json.indexOf("\"", regionIdIndex + 11);
        int valueEnd = json.indexOf("\"", valueStart + 1);

        if (valueStart == -1 || valueEnd == -1) {
            return null;
        }

        return json.substring(valueStart + 1, valueEnd);
    }

    /**
     * Validates Redis session if this is a session login.
     */
    private boolean validateRedisSession(SessionTokenClaims claims, String requestUri) {
        try {
            // Get session from Redis
            Optional<WSession> sessionOpt = sessionService.get(claims.sessionId());
            if (sessionOpt.isEmpty()) {
                log.warn("Session not found in Redis - sessionId={}", claims.sessionId());
                return false;
            }

            WSession session = sessionOpt.get();

            // Check status is not CLOSED (unless allowed for this path)
            if (session.getStatus() == WSessionStatus.CLOSED) {
                if (!shouldAcceptClosedSessions(requestUri)) {
                    log.warn("Session is CLOSED - sessionId={}", claims.sessionId());
                    return false;
                }
                log.debug("Session is CLOSED but accepted for path: {}", requestUri);
            }

            // Check worldId matches
            if (!claims.worldId().equals(session.getWorldId())) {
                log.warn("Session worldId mismatch - expected={}, actual={}",
                        session.getWorldId(), claims.worldId());
                return false;
            }

            // Check playerId matches
            String expectedPlayerId = "@" + claims.userId() + ":" + claims.characterId();
            if (!expectedPlayerId.equals(session.getPlayerId())) {
                log.warn("Session playerId mismatch - expected={}, actual={}",
                        session.getPlayerId(), expectedPlayerId);
                return false;
            }

            log.debug("Redis session validated - sessionId={}, status={}",
                    claims.sessionId(), session.getStatus());
            return true;

        } catch (Exception e) {
            log.error("Redis session validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts Bearer token from Authorization header.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }

    /**
     * Validates Bearer token for server-to-server authentication.
     * Bearer tokens contain serviceName instead of userId and allow full access.
     */
    private boolean validateBearerToken(String token, HttpServletRequest request) {
        try {
            // Decode JWT payload to extract regionId
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid bearer token format");
                return false;
            }

            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            // Validate token with JWT service
            var intent = KeyIntent.of(regionProperties.getSectorServerId(), KeyIntent.REGION_SERVER_JWT_TOKEN);
            Optional<Jws<Claims>> jwsOpt = jwtService.validateTokenWithPublicKey(
                    token,
                    de.mhus.nimbus.shared.security.KeyType.REGION,
                    intent
            );

            if (jwsOpt.isEmpty()) {
                log.warn("Bearer token validation failed");
                return false;
            }

            Claims claims = jwsOpt.get().getPayload();

            // Check if this is a server-to-server token
            Boolean serverToServer = claims.get("serverToServer", Boolean.class);
            String serviceName = claims.get("serviceName", String.class);

            if (serverToServer == null || !serverToServer) {
                log.warn("Bearer token is not a server-to-server token");
                return false;
            }

            if (serviceName == null || serviceName.isBlank()) {
                log.warn("Bearer token missing serviceName");
                return false;
            }

            // Set server-to-server attributes
            request.setAttribute(ATTR_IS_AUTHENTICATED, true);
            request.setAttribute("serverToServer", true);
            request.setAttribute("serviceName", serviceName);

            log.info("Bearer token validated - serviceName={}", serviceName);
            return true;

        } catch (Exception e) {
            log.warn("Bearer token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Record for parsed session token claims.
     * Protected to allow subclasses to use it in isClaimsAcceptable().
     */
    protected record SessionTokenClaims(
            boolean agent,
            String worldId,
            String userId,
            String characterId,
            String role,
            String sessionId,
            String regionId
    ) {}
}
