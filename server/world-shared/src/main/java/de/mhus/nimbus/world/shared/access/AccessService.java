package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.security.Base64Service;
import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.security.KeyIntent;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.dto.*;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionStatus;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for world access management and development authentication.
 *
 * Business Logic Layer:
 * - Provides world and character information
 * - Manages role-based access control
 * - Creates development login tokens (session and agent)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessService {

    private final WWorldService worldService;
    private final RCharacterService characterService;
    private final de.mhus.nimbus.world.shared.sector.RUserService userService;
    private final de.mhus.nimbus.world.shared.session.WSessionService sessionService;
    private final de.mhus.nimbus.world.shared.world.WWorldInstanceService worldInstanceService;
    private final JwtService jwtService;
    private final AccessSettings properties;
    private final Base64Service base64Service;
    private final de.mhus.nimbus.shared.utils.LocationService locationService;
    private final RegionSettings regionProperties;

    // Cache for world token (server-to-server authentication)
    private volatile String cachedWorldToken;
    private volatile Instant cachedWorldTokenExpiry;
    private final Object worldTokenLock = new Object();

    // ===== 1. getWorlds =====

    /**
     * Retrieves worlds from the system with optional search filter.
     * Uses database-level filtering and pagination.
     *
     * @param searchQuery Optional search term to filter world names (can be null/empty)
     * @param limit Maximum number of results to return (default 100)
     * @return List of WorldInfoDto with basic world information
     */
    @Transactional(readOnly = true)
    public List<WorldInfoDto> getWorlds(String searchQuery, int limit) {
        log.debug("Fetching worlds with search='{}', limit={}", searchQuery, limit);

        // Use database-level filtering and pagination
        var result = worldService.searchWorlds(searchQuery, 0, limit);

        return result.worlds().stream()
                .map(this::mapToWorldInfoDto)
                .toList();
    }

    /**
     * Maps WWorld entity to WorldInfoDto.
     */
    private WorldInfoDto mapToWorldInfoDto(WWorld world) {
        return WorldInfoDto.builder()
                .worldId(world.getWorldId())
                .name(world.getName())
                .description(world.getDescription())
                .regionId(world.getRegionId())
                .enabled(world.isEnabled())
                .publicFlag(world.isPublicFlag())
                .build();
    }

    // ===== 2. getUsers =====

    /**
     * Retrieves users from the system with optional search filter.
     * Always limits results to prevent overwhelming responses.
     *
     * @param searchQuery Optional search term to filter usernames (can be null/empty)
     * @param limit Maximum number of results to return (default 100)
     * @return List of UserInfoDto with basic user information
     */
    @Transactional(readOnly = true)
    public List<UserInfoDto> getUsers(String searchQuery, int limit) {
        log.debug("Fetching users with search='{}', limit={}", searchQuery, limit);

        List<de.mhus.nimbus.world.shared.sector.RUser> users = userService.listAll();

        // Filter by search query if provided (searches in username and email)
        if (searchQuery != null && !searchQuery.isBlank()) {
            String queryLower = searchQuery.toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(queryLower)) ||
                                 (u.getEmail() != null && u.getEmail().toLowerCase().contains(queryLower)))
                    .collect(Collectors.toList());
        }

        // Always limit results (even without search)
        users = users.stream()
                .limit(limit)
                .collect(Collectors.toList());

        return users.stream()
                .map(this::mapToUserInfoDto)
                .collect(Collectors.toList());
    }

    /**
     * Maps RUser entity to UserInfoDto.
     */
    private UserInfoDto mapToUserInfoDto(de.mhus.nimbus.world.shared.sector.RUser user) {
        return UserInfoDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .build();
    }

    // ===== 3. getCharactersForUserInWorld =====

    /**
     * Retrieves all characters for a user in a specific world.
     *
     * @param userId User ID to search for
     * @param worldId World ID to search in
     * @return List of CharacterInfoDto
     * @throws IllegalArgumentException if world not found
     */
    @Transactional(readOnly = true)
    public List<CharacterInfoDto> getCharactersForUserInWorld(String userId, String worldId) {
        log.debug("Fetching characters for user={} in world={}", userId, worldId);

        // Validate world exists and get regionId
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        String regionId = world.getRegionId();
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalStateException("World has no regionId: " + worldId);
        }

        // Fetch characters for user in this region
        List<RCharacter> characters = characterService.listCharacters(userId, regionId);

        return characters.stream()
                .map(this::mapToCharacterInfoDto)
                .collect(Collectors.toList());
    }

    /**
     * Maps RCharacter entity to CharacterInfoDto.
     */
    private CharacterInfoDto mapToCharacterInfoDto(RCharacter character) {
        return CharacterInfoDto.builder()
                .id(character.getName())
                .name(character.getName())
                .display(character.getDisplay())
                .userId(character.getUserId())
                .regionId(character.getRegionId())
                .build();
    }

    // ===== 3. getRolesForCharacterInWorld =====

    /**
     * Retrieves roles for a character in a specific world.
     *
     * @param characterId Character ID (RCharacter.id)
     * @param worldId World ID
     * @return WorldRoleDto with list of WorldRoles
     * @throws IllegalArgumentException if world or character not found
     */
    @Transactional(readOnly = true)
    public WorldRoleDto getRolesForCharacterInWorld(String characterId, String worldId) {
        log.debug("Fetching roles for character={} in world={}", characterId, worldId);

        // Get world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Get character to extract userId
        RCharacter character = characterService.listCharactersByRegion(world.getRegionId())
                .stream()
                .filter(c -> c.getId().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        // Get roles from world using userId
        UserId userId = UserId.of(character.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid userId: " + character.getUserId()));

        List<WorldRoles> roles = world.getRolesForUser(userId);

        return WorldRoleDto.builder()
                .characterId(characterId)
                .worldId(worldId)
                .roles(roles)
                .build();
    }

    // ===== 4. devSessionLogin =====

    /**
     * Creates a session-based access token for development purposes.
     *
     * @param request DevSessionLoginRequest with worldId, userId, characterId, actor
     * @return DevLoginResponse with token, URLs, sessionId, and playerId
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public DevLoginResponse devSessionLogin(DevSessionLoginRequest request) {
        log.info("Dev session login: user={}, world={}, character={}, actor={}",
                request.getUserId(), request.getWorldId(), request.getCharacterId(), request.getActor());

        WorldId worldId = WorldId.of(request.getWorldId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + request.getWorldId()));

        // Validate world exists
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + request.getWorldId()));

        // Validate character exists
        RCharacter character = characterService.getCharacter(request.getUserId(), world.getRegionId(), request.getCharacterId()).orElseThrow(
                () -> new IllegalArgumentException("Character not found: " + request.getCharacterId())
        );

        // Validate userId matches
        if (!character.getUserId().equals(request.getUserId())) {
            throw new IllegalArgumentException("Character does not belong to user: " + request.getUserId());
        }

        PlayerId playerId = PlayerId.of(request.getUserId(), request.getCharacterId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid userId or characterId"));

        // Determine effective worldId (might be an instanceId for instanceable worlds)
        String effectiveWorldId = worldId.getId();

        // Auto-create instance for PLAYER actors in instanceable worlds
        if (world.isInstanceable() && request.getActor() == ActorRoles.PLAYER) {
            // TODO check if user is in Team and maybe tehre is already a running instance for the team
            // if not add all team memebers to the new instance - requires a lock on instance creation
            // do this in the service layer
            WWorldInstance instance = worldInstanceService.createInstanceForPlayer(
                    worldId.getId(),
                    world.getName(),
                    playerId.getId(),
                    character.getDisplay()
            );

            effectiveWorldId = instance.getWorldWithInstanceId();
            log.info("Auto-created world instance for player: instanceId={}, worldId={}, playerId={}",
                    instance.getInstanceId(), worldId.getId(), playerId.getId());
        }

        // Create session with effective worldId (original worldId or instanceId)
        WorldId sessionWorldId = WorldId.unchecked(effectiveWorldId);
        WSession session = sessionService.create(sessionWorldId, playerId, String.valueOf(request.getActor()));

        // Set entry point if provided
        if (request.getEntryPoint() != null && !request.getEntryPoint().isBlank()) {
            sessionService.updateEntryPoint(session.getId(), request.getEntryPoint());
            log.debug("Entry point set for session: sessionId={}, entryPoint={}", session.getId(), request.getEntryPoint());
        }

        // Create JWT token
        String token = createSessionToken(
                world.getRegionId(),
                playerId.getUserId(),
                effectiveWorldId,
                playerId.getCharacterId(),
                request.getActor().name(),
                session.getId()
        );

        // Build response with configured URLs
        return DevLoginResponse.builder()
                .accessToken(token)
                .accessUrls(properties.getAccessUrls())
                .jumpUrl(findJumpUrl(properties, request, session.getId(), effectiveWorldId))
                .sessionId(session.getId())
                .playerId(playerId.getId())
                .build();
    }

    private String findJumpUrl(AccessSettings properties, DevSessionLoginRequest request, String sessionId, String effectiveWorldId) {
        var url = request.getActor() == ActorRoles.EDITOR ? properties.getJumpUrlEditor() : properties.getJumpUrlViewer();
        url = url.replace("{worldId}", effectiveWorldId);
        url = url.replace("{session}", sessionId);
        url = url.replace("{userId}", request.getUserId());
        url = url.replace("{characterId}", request.getCharacterId());
        return url;
    }

    private String findJumpUrl(AccessSettings properties, DevAgentLoginRequest request) {
        var url = properties.getJumpUrlAgent();
        url = url.replace("{worldId}", request.getWorldId());
        url = url.replace("{userId}", request.getUserId());
        return url;
    }

    /**
     * Creates a JWT token for session-based access (used by teleportation).
     *
     * @param regionId Region ID
     * @param userId User ID
     * @param worldId World ID (can include instance)
     * @param characterId Character ID
     * @param role Actor role
     * @param sessionId Session ID
     * @return JWT token
     */
    public String createSessionTokenForTeleport(String regionId, String userId, String worldId,
                                                 String characterId, String role, String sessionId) {
        return createSessionToken(regionId, userId, worldId, characterId, role, sessionId);
    }

    /**
     * Creates a JWT token for session-based access.
     */
    private String createSessionToken(String regionId, String userId, String worldId,
                                      String characterId, String role, String sessionId) {
        // Build claims map
        Map<String, Object> claims = new HashMap<>();
        claims.put("agent", false);
        claims.put("worldId", worldId);
        claims.put("userId", userId);
        claims.put("characterId", characterId);
        claims.put("role", role);
        claims.put("sessionId", sessionId);
        claims.put("regionId", regionId);

        // Calculate expiration (use sessionTokenTtlSeconds for session tokens)
        Instant expiresAt = Instant.now().plusSeconds(properties.getSessionTokenTtlSeconds());

        // Create token using JwtService
        return jwtService.createTokenWithPrivateKey(
                KeyType.REGION,
                KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN),
                userId,
                claims,
                expiresAt
        );
    }

    // ===== 5. devAgentLogin =====

    /**
     * Creates an agent access token for development purposes.
     * Agent tokens are not bound to a session or character.
     *
     * @param request DevAgentLoginRequest with worldId and userId
     * @return DevLoginResponse with token and URLs (no sessionId/playerId)
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional(readOnly = true)
    public DevLoginResponse devAgentLogin(DevAgentLoginRequest request) {
        log.info("Dev agent login: user={}, world={}", request.getUserId(), request.getWorldId());

        // Validate world exists
        WWorld world = worldService.getByWorldId(request.getWorldId())
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + request.getWorldId()));

        // Create JWT token
        String token = createAgentToken(
                world.getRegionId(),
                request.getUserId(),
                request.getWorldId()
        );

        // Build response with configured URLs (no sessionId/playerId)
        return DevLoginResponse.builder()
                .accessToken(token)
                .accessUrls(properties.getAccessUrls())
                .jumpUrl(findJumpUrl(properties,request))
                .sessionId(null)
                .playerId(null)
                .build();
    }

    /**
     * Creates a JWT token for agent access.
     */
    private String createAgentToken(String regionId, String userId, String worldId) {
        // Build claims map (agent mode - no characterId, role, or sessionId)
        Map<String, Object> claims = new HashMap<>();
        claims.put("agent", true);
        claims.put("worldId", worldId);
        claims.put("userId", userId);
        claims.put("regionId", regionId);

        // Calculate expiration (use agentTokenTtlSeconds for agent tokens)
        Instant expiresAt = Instant.now().plusSeconds(properties.getAgentTokenTtlSeconds());

        // Create token using JwtService
        return jwtService.createTokenWithPrivateKey(
                KeyType.REGION,
                KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN),
                userId,
                claims,
                expiresAt
        );
    }

    // ===== 6. authorizeWithToken =====

    /**
     * Internal record for parsed access token claims.
     */
    private record AccessTokenClaims(
            boolean agent,
            String worldId,
            String userId,
            String characterId,  // null for agent
            String role,         // null for agent
            String sessionId,    // null for agent
            String regionId
    ) {}

    /**
     * Validates access token and creates session cookies.
     *
     * @param accessToken The access token from /devlogin
     * @param response HttpServletResponse for cookie manipulation
     * @throws IllegalArgumentException if token invalid or world not found
     * @throws IllegalStateException if session invalid or access denied
     */
    @Transactional
    public void authorizeWithToken(String accessToken, HttpServletResponse response) {
        log.debug("Authorizing with access token");

        // 1. Validate and parse access token
        AccessTokenClaims claims = validateAndParseAccessToken(accessToken);

        // 2. Get world
        WWorld world = worldService.getByWorldId(claims.worldId())
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + claims.worldId()));

        // 3. Validate based on agent flag
        if (claims.agent()) {
            validateAgentAccess(claims.userId(), world);
        } else {
            validateSessionAccess(claims, world);
        }

        // 4. Create session token
        SessionTokenWithExpiry tokenWithExpiry = createSessionTokenFromAccess(claims, claims.regionId());

        // 5. Set cookies
        setCookies(response, tokenWithExpiry.token(), tokenWithExpiry.expiresAt(), claims);

        log.info("Authorization successful - worldId={}, userId={}, agent={}",
                claims.worldId(), claims.userId(), claims.agent());
    }

    /**
     * Validates access token and extracts claims.
     */
    private AccessTokenClaims validateAndParseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }

        // Parse token to extract regionId from claims (unverified)
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format");
        }

        // Decode payload to extract regionId
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        String regionId = extractRegionIdFromJson(payloadJson);

        // Now validate token with correct regionId
        Optional<Jws<Claims>> jwsOpt = jwtService.validateTokenWithPublicKey(
                token,
                KeyType.REGION,
                KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN)
        );

        if (jwsOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired access token");
        }

        Claims claims = jwsOpt.get().getPayload();

        // Extract claims
        Boolean agent = claims.get("agent", Boolean.class);
        String worldId = claims.get("worldId", String.class);
        String userId = claims.get("userId", String.class);
        String characterId = claims.get("characterId", String.class);
        String role = claims.get("role", String.class);
        String sessionId = claims.get("sessionId", String.class);

        // Validate required fields
        if (agent == null || worldId == null || userId == null) {
            throw new IllegalArgumentException("Access token missing required claims");
        }

        // Session tokens require additional fields
        if (!agent && (characterId == null || role == null || sessionId == null)) {
            throw new IllegalArgumentException("Session access token missing required claims");
        }

        return new AccessTokenClaims(agent, worldId, userId, characterId, role, sessionId, regionId);
    }

    /**
     * Extracts regionId from JSON payload (simple string matching).
     */
    private String extractRegionIdFromJson(String json) {
        int regionIdIndex = json.indexOf("\"regionId\"");
        if (regionIdIndex == -1) {
            throw new IllegalArgumentException("Token missing regionId claim");
        }

        int valueStart = json.indexOf("\"", regionIdIndex + 11);
        int valueEnd = json.indexOf("\"", valueStart + 1);

        if (valueStart == -1 || valueEnd == -1) {
            throw new IllegalArgumentException("Invalid regionId claim format");
        }

        return json.substring(valueStart + 1, valueEnd);
    }

    /**
     * Validates session access by checking Redis session.
     */
    private void validateSessionAccess(AccessTokenClaims claims, WWorld world) {
        log.debug("Validating session access - sessionId={}", claims.sessionId());

        // Get session from Redis
        WSession session = sessionService.get(claims.sessionId())
                .orElseThrow(() -> new IllegalStateException("Session not found: " + claims.sessionId()));

        // Check status = WAITING
        if (session.getStatus() != WSessionStatus.WAITING) {
            throw new IllegalStateException("Session must be WAITING, found: " + session.getStatus());
        }

        // Check worldId matches
        if (!claims.worldId().equals(session.getWorldId())) {
            throw new IllegalStateException("Session worldId mismatch");
        }

        // Check playerId matches
        String expectedPlayerId = "@" + claims.userId() + ":" + claims.characterId();
        if (!expectedPlayerId.equals(session.getPlayerId())) {
            throw new IllegalStateException("Session playerId mismatch");
        }

        log.debug("Session validated and activated - sessionId={}", claims.sessionId());
    }

    /**
     * Validates agent access by checking user permissions in world.
     */
    private void validateAgentAccess(String userId, WWorld world) {
        log.debug("Validating agent access - userId={}, worldId={}", userId, world.getWorldId());

        UserId userIdObj = UserId.of(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid userId: " + userId));

        var userOpt = userService.getByUsername(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        var user = userOpt.get();
        var worldIdOpt = WorldId.of(world.getWorldId());
        if (worldIdOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid worldId: " + world.getWorldId());
        }

        boolean hasAccess =
                user.isSectorAdmin() ||
                user.isRegionAdmin(worldIdOpt.get()) ||
                world.isOwnerAllowed(userIdObj) ||
                world.isEditorAllowed(userIdObj) ||
                world.isSupporterAllowed(userIdObj);

        if (!hasAccess) {
            throw new IllegalStateException("User not authorized for agent access to world: " + world.getWorldId());
        }

        log.debug("Agent access validated - userId={}", userId);
    }

    /**
     * Creates session token from validated access token claims.
     */
    private record SessionTokenWithExpiry(String token, Instant expiresAt) {}

    private SessionTokenWithExpiry createSessionTokenFromAccess(AccessTokenClaims claims, String regionId) {
        Map<String, Object> tokenClaims = new HashMap<>();
        tokenClaims.put("agent", claims.agent());
        tokenClaims.put("worldId", claims.worldId());
        tokenClaims.put("userId", claims.userId());
        tokenClaims.put("regionId", regionId);

        if (!claims.agent()) {
            tokenClaims.put("characterId", claims.characterId());
            tokenClaims.put("role", claims.role());
            tokenClaims.put("sessionId", claims.sessionId());
        }

        // TTL: 24h (session) or 1h (agent)
        long ttlSeconds = claims.agent()
                ? properties.getAgentTokenTtlSeconds()
                : properties.getSessionTokenTtlSeconds();

        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        String token = jwtService.createTokenWithPrivateKey(
                KeyType.REGION,
                KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN),
                claims.userId(),
                tokenClaims,
                expiresAt
        );

        return new SessionTokenWithExpiry(token, expiresAt);
    }

    /**
     * Sets two cookies: sessionToken (httpOnly) and sessionData (JS-accessible).
     */
    private void setCookies(HttpServletResponse response, String sessionToken, Instant expiresAt, AccessTokenClaims claims) {
        // Calculate maxAge from the difference between expiration and now (in UTC)
        long maxAgeSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();

        boolean secure = properties.isSecureCookies();
        String domain = properties.getCookieDomain();

        // Build Set-Cookie headers manually to include SameSite attribute
        String tokenCookieHeader = buildCookieHeader("sessionToken", sessionToken, secure, domain, (int) maxAgeSeconds, true);
        response.addHeader("Set-Cookie", tokenCookieHeader);

        // Cookie 2: sessionData (JS-accessible, Base64-encoded)
        String sessionData = buildSessionDataJson(claims);
        String encodedSessionData = base64Service.encode(sessionData);
        String dataCookieHeader = buildCookieHeader("sessionData", encodedSessionData, secure, domain, (int) maxAgeSeconds, false);
        response.addHeader("Set-Cookie", dataCookieHeader);

        log.info("Cookies set - sessionToken (httpOnly, secure={}, domain='{}', path='/', maxAge={}, expiresAt={}), sessionData (JS-accessible, Base64-encoded, same settings)",
                secure, domain != null ? domain : "(none)", maxAgeSeconds, expiresAt);
    }

    /**
     * Builds Set-Cookie header with SameSite attribute.
     */
    private String buildCookieHeader(String name, String value, boolean secure, String domain, int maxAge, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=/");
        cookie.append("; Max-Age=").append(maxAge);

        if (domain != null && !domain.isBlank()) {
            cookie.append("; Domain=").append(domain);
        }

        if (secure) {
            cookie.append("; Secure");
        }

        if (httpOnly) {
            cookie.append("; HttpOnly");
        }

        // SameSite=None allows cross-site cookies (required for CORS), but needs Secure=true
        // For development without HTTPS, use SameSite=Lax
        if (secure) {
            cookie.append("; SameSite=None");
        } else {
            cookie.append("; SameSite=Lax");
        }

        return cookie.toString();
    }

    /**
     * Builds JSON string for sessionData cookie.
     */
    private String buildSessionDataJson(AccessTokenClaims claims) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"worldId\":\"").append(claims.worldId()).append("\"");
        json.append(",\"userId\":\"").append(claims.userId()).append("\"");
        json.append(",\"agent\":").append(claims.agent());

        if (!claims.agent()) {
            json.append(",\"sessionId\":\"").append(claims.sessionId()).append("\"");
            json.append(",\"characterId\":\"").append(claims.characterId()).append("\"");
            json.append(",\"role\":\"").append(claims.role()).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    // ===== 7. getSessionStatus =====

    /**
     * Gets current authentication status from sessionToken cookie or Bearer token.
     * Returns user info, URLs for logout, and login URL.
     *
     * Supports three authentication types:
     * 1. Session tokens (agent=false) - via cookie
     * 2. Agent tokens (agent=true) - via cookie
     * 3. World tokens (serverToServer=true) - via Authorization Bearer header
     *
     * For agent tokens, characterId, role, and sessionId will be null.
     * For world tokens, all user-specific fields will be null.
     *
     * @param request HttpServletRequest containing sessionToken cookie or Bearer token
     * @return SessionStatusResponse with authentication info and URLs
     * @throws IllegalArgumentException if not authenticated or token invalid
     */
    public SessionStatusResponse getSessionStatus(HttpServletRequest request) {
        // First check for Bearer token (server-to-server)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearerToken = authHeader.substring(7);
            return getStatusFromBearerToken(bearerToken);
        }

        // Otherwise check for cookie-based token
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new IllegalArgumentException("No cookies or Authorization header found");
        }

        String sessionToken = null;
        for (Cookie cookie : cookies) {
            if ("sessionToken".equals(cookie.getName())) {
                sessionToken = cookie.getValue();
                break;
            }
        }

        if (sessionToken == null) {
            throw new IllegalArgumentException("Session token not found in cookies");
        }

        // Parse and validate token
        try {
            // Decode JWT payload to extract regionId
            String[] parts = sessionToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            String regionId = extractJsonField(payloadJson, "regionId");

            // Validate token with JWT service
            var jwsOpt = jwtService.validateTokenWithPublicKey(
                    sessionToken,
                    KeyType.REGION,
                    KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN)
            );

            if (jwsOpt.isEmpty()) {
                throw new IllegalArgumentException("Token validation failed");
            }

            Claims claims = jwsOpt.get().getPayload();

            // Extract claims
            Boolean agent = claims.get("agent", Boolean.class);
            String worldId = claims.get("worldId", String.class);
            String userId = claims.get("userId", String.class);
            String characterId = claims.get("characterId", String.class);
            String actor = claims.get("role", String.class); // "role" field contains actor
            String sessionId = claims.get("sessionId", String.class);

            // Load roles dynamically
            List<String> roles = loadUserRoles(userId, worldId);

            // Build logout URLs
            List<String> accessUrls = properties.getAccessUrls();

            return SessionStatusResponse.builder()
                    .authenticated(true)
                    .agent(agent != null && agent)
                    .worldId(worldId)
                    .userId(userId)
                    .characterId(characterId)
                    .actor(actor)
                    .roles(roles)
                    .sessionId(sessionId)
                    .accessUrls(accessUrls)
                    .loginUrl(properties.getLoginUrl())
                    .logoutUrl(properties.getLogoutUrl())
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse session token: " + e.getMessage(), e);
        }
    }

    /**
     * Gets status from Bearer token (server-to-server authentication).
     */
    private SessionStatusResponse getStatusFromBearerToken(String bearerToken) {
        try {
            // Decode JWT payload to extract regionId
            String[] parts = bearerToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid bearer token format");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            String regionId = extractJsonField(payloadJson, "regionId");

            // Validate token with JWT service
            var jwsOpt = jwtService.validateTokenWithPublicKey(
                    bearerToken,
                    KeyType.REGION,
                    KeyIntent.of(regionId, KeyIntent.REGION_JWT_TOKEN)
            );

            if (jwsOpt.isEmpty()) {
                throw new IllegalArgumentException("Bearer token validation failed");
            }

            Claims claims = jwsOpt.get().getPayload();

            // Check if this is a server-to-server token
            Boolean serverToServer = claims.get("serverToServer", Boolean.class);
            String serviceName = claims.get("serviceName", String.class);

            if (serverToServer == null || !serverToServer) {
                throw new IllegalArgumentException("Not a server-to-server token");
            }

            // Return status for server-to-server token
            // Note: No logout URLs for server tokens (they don't use cookies)
            return SessionStatusResponse.builder()
                    .authenticated(true)
                    .agent(false) // Not really agent, but serverToServer
                    .worldId(null)
                    .userId("service:" + serviceName)
                    .characterId(null)
                    .actor(null)
                    .roles(List.of("SERVER_TO_SERVER"))
                    .sessionId(null)
                    .accessUrls(List.of()) // No logout URLs for server tokens
                    .loginUrl(properties.getLoginUrl())
                    .logoutUrl(properties.getLogoutUrl())
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to validate bearer token: " + e.getMessage(), e);
        }
    }

    /**
     * Loads user roles from RUserService and WWorldService.
     * Combines sector roles and world-specific roles.
     */
    private List<String> loadUserRoles(String userId, String worldId) {
        List<String> allRoles = new java.util.ArrayList<>();

        try {
            // Get sector roles from RUserService
            var sectorRoles = userService.getRoles(userId);
            sectorRoles.forEach(role -> allRoles.add("SECTOR_" + role.name()));
        } catch (Exception e) {
            log.warn("Failed to load sector roles for user={}: {}", userId, e.getMessage());
        }

        try {
            // Get world roles from WWorldService
            if (worldId != null && !worldId.isBlank()) {
                var worldOpt = worldService.getByWorldId(worldId);
                if (worldOpt.isPresent()) {
                    var world = worldOpt.get();
                    var userIdOpt = UserId.of(userId);
                    if (userIdOpt.isPresent()) {
                        var worldRoles = world.getRolesForUser(userIdOpt.get());
                        worldRoles.forEach(role -> allRoles.add("WORLD_" + role.name()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load world roles for user={}, world={}: {}", userId, worldId, e.getMessage());
        }

        return allRoles;
    }

    /**
     * Simple JSON field extractor.
     */
    private String extractJsonField(String json, String fieldName) {
        int fieldIndex = json.indexOf("\"" + fieldName + "\"");
        if (fieldIndex == -1) {
            return null;
        }
        int valueStart = json.indexOf("\"", fieldIndex + fieldName.length() + 3);
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueStart == -1 || valueEnd == -1) {
            return null;
        }
        return json.substring(valueStart + 1, valueEnd);
    }

    // ===== 8. logout =====

    /**
     * Logout by clearing session cookies.
     * Sets Max-Age=0 on sessionToken and sessionData cookies.
     *
     * @param response HttpServletResponse for cookie clearing
     */
    public void logout(jakarta.servlet.http.HttpServletResponse response) {
        // Clear sessionToken cookie
        jakarta.servlet.http.Cookie tokenCookie = new jakarta.servlet.http.Cookie("sessionToken", "");
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(properties.isSecureCookies());
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0); // Delete cookie
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            tokenCookie.setDomain(properties.getCookieDomain());
        }
        response.addCookie(tokenCookie);

        // Clear sessionData cookie
        jakarta.servlet.http.Cookie dataCookie = new jakarta.servlet.http.Cookie("sessionData", "");
        dataCookie.setHttpOnly(false);
        dataCookie.setSecure(properties.isSecureCookies());
        dataCookie.setPath("/");
        dataCookie.setMaxAge(0); // Delete cookie
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            dataCookie.setDomain(properties.getCookieDomain());
        }
        response.addCookie(dataCookie);

        log.info("Session cookies cleared");
    }

    // ===== 9. getWorldToken (Server-to-Server) =====

    /**
     * Gets a world token for server-to-server authentication.
     * Token is cached and automatically renewed when expired.
     *
     * World tokens are different from user tokens:
     * - No worldId, userId, characterId
     * - Contains serviceName (from LocationService.applicationServiceName)
     * - Full access rights for inter-service communication
     * - TTL: 1 hour (agentTokenTtlSeconds)
     *
     * @return Bearer token for Authorization header
     */
    public String getWorldToken() {
        // Check if cached token is still valid
        if (cachedWorldToken != null && cachedWorldTokenExpiry != null) {
            // Renew if less than 5 minutes remaining
            if (Instant.now().plusSeconds(300).isBefore(cachedWorldTokenExpiry)) {
                log.debug("Using cached world token");
                return cachedWorldToken;
            }
        }

        // Generate new token (thread-safe)
        synchronized (worldTokenLock) {
            // Double-check after acquiring lock
            if (cachedWorldToken != null && cachedWorldTokenExpiry != null) {
                if (Instant.now().plusSeconds(300).isBefore(cachedWorldTokenExpiry)) {
                    return cachedWorldToken;
                }
            }

            // Get service name from LocationService
            String serviceName = locationService.getApplicationServiceName();

            log.info("Generating new world token for service: {}", serviceName);
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalStateException("Service name not configured (spring.application.name)");
            }

            // Build claims for world token
            Map<String, Object> claims = new HashMap<>();
            claims.put("serviceName", serviceName);
            claims.put("serverToServer", true);

            // Calculate expiration (1 hour)
            Instant expiresAt = Instant.now().plusSeconds(properties.getAgentTokenTtlSeconds());

            // Create token
            var intent = KeyIntent.of(regionProperties.getSectorServerId(), KeyIntent.REGION_SERVER_JWT_TOKEN);
            String token = jwtService.createTokenWithPrivateKey(
                    KeyType.REGION,
                    intent,
                    "service:" + serviceName,
                    claims,
                    expiresAt
            );

            // Cache token
            cachedWorldToken = token;
            cachedWorldTokenExpiry = expiresAt;

            log.info("World token generated - serviceName={}, expiresAt={}", serviceName, expiresAt);

            return token;
        }
    }
}
