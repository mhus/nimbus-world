package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.world.shared.access.AccessService;
import de.mhus.nimbus.world.shared.dto.DevAgentLoginRequest;
import de.mhus.nimbus.world.shared.dto.DevLoginResponse;
import de.mhus.nimbus.world.shared.dto.DevSessionLoginRequest;
import de.mhus.nimbus.world.shared.dto.WorldInfoDto;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Authentication, Authorization, and Access (AAA).
 * Provides development login endpoints for session and agent access.
 */
@RestController
@RequestMapping("/control/aaa")
@RequiredArgsConstructor
@Slf4j
public class ControlAaaController extends BaseEditorController {

    private final AccessService accessService;

    // ===== Request/Response DTOs =====

    /**
     * Request DTO for dev login endpoint.
     * Supports both session and agent login modes.
     */
    public record DevLoginRequest(
            String worldId,
            String userId,
            Boolean agent,       // Optional, defaults to false
            String characterId,  // Required when agent=false
            ActorRoles actor,    // Required when agent=false
            String entryPoint    // Optional: "last", "grid:q,r", or "world"
    ) {
        public boolean isAgent() {
            return agent != null && agent;
        }
    }

    // ===== GET /control/aaa/devlogin =====

    /**
     * Get worlds for dev login UI.
     * Supports optional search filter to limit results.
     *
     * @param search Optional search query for world names
     * @param limit Maximum number of results (default 100)
     * @return List of worlds matching the search criteria
     */
    @GetMapping("/devlogin")
    public ResponseEntity<?> getDevLoginData(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        log.debug("GET /control/aaa/devlogin - search={}, limit={}", search, limit);

        try {
            List<WorldInfoDto> worlds = accessService.getWorlds(search, limit);
            log.debug("Returning {} worlds for dev login", worlds.size());
            return ResponseEntity.ok(worlds);

        } catch (Exception e) {
            log.error("Failed to load worlds for dev login", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to load worlds: " + e.getMessage()));
        }
    }

    // ===== GET /control/aaa/devlogin/users =====

    /**
     * Get users for dev login UI.
     * Supports search filter to limit results.
     *
     * @param search Search query for usernames (required, min 2 chars)
     * @param limit Maximum number of results (default 100)
     * @return List of users matching the search criteria
     */
    @GetMapping("/devlogin/users")
    public ResponseEntity<?> getDevLoginUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        log.debug("GET /control/aaa/devlogin/users - search={}, limit={}", search, limit);

        try {
            var users = accessService.getUsers(search, limit);
            log.debug("Returning {} users for dev login", users.size());
            return ResponseEntity.ok(users);

        } catch (Exception e) {
            log.error("Failed to load users for dev login", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to load users: " + e.getMessage()));
        }
    }

    // ===== GET /control/aaa/devlogin/characters =====

    /**
     * Get characters for a user in a world.
     *
     * @param userId User ID
     * @param worldId World ID
     * @return List of characters for the user in the world
     */
    @GetMapping("/devlogin/characters")
    public ResponseEntity<?> getDevLoginCharacters(
            @RequestParam String userId,
            @RequestParam String worldId
    ) {
        log.debug("GET /control/aaa/devlogin/characters - userId={}, worldId={}", userId, worldId);

        // Validate parameters
        ResponseEntity<?> validation = validateId(userId, "userId");
        if (validation != null) return validation;

        validation = validateId(worldId, "worldId");
        if (validation != null) return validation;

        try {
            var characters = accessService.getCharactersForUserInWorld(userId, worldId);
            log.debug("Returning {} characters for user={} in world={}",
                characters.size(), userId, worldId);
            return ResponseEntity.ok(characters);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to load characters: {}", e.getMessage());
            return notFound(e.getMessage());

        } catch (Exception e) {
            log.error("Failed to load characters for dev login", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to load characters: " + e.getMessage()));
        }
    }

    // ===== POST /control/aaa/devlogin =====

    /**
     * Execute dev login and create access token.
     * Supports both session login (with character) and agent login (without character).
     *
     * @param request DevLoginRequest with login parameters
     * @return DevLoginResponse with access token and URLs
     */
    @PostMapping("/devlogin")
    public ResponseEntity<?> devLogin(@RequestBody DevLoginRequest request) {
        log.debug("POST /control/aaa/devlogin - worldId={}, userId={}, agent={}, characterId={}, actor={}",
                request.worldId(), request.userId(), request.agent(),
                request.characterId(), request.actor());

        // ===== Validation =====

        // Validate worldId
        ResponseEntity<?> validation = validateId(request.worldId(), "worldId");
        if (validation != null) return validation;

        // Validate userId
        validation = validateId(request.userId(), "userId");
        if (validation != null) return validation;

        // Validate session-specific fields
        if (!request.isAgent()) {
            if (blank(request.characterId())) {
                return bad("characterId is required for session login");
            }
            if (request.actor() == null) {
                return bad("actor is required for session login");
            }
            // Validate actor is valid enum value
            if (!List.of(ActorRoles.PLAYER, ActorRoles.EDITOR, ActorRoles.SUPPORT)
                    .contains(request.actor())) {
                return bad("actor must be PLAYER, EDITOR, or SUPPORT");
            }
        }

        // ===== Execute Login =====

        try {
            DevLoginResponse response;

            if (request.isAgent()) {
                log.debug("Executing agent login for user={} in world={}",
                        request.userId(), request.worldId());

                DevAgentLoginRequest agentRequest = DevAgentLoginRequest.builder()
                        .worldId(request.worldId())
                        .userId(request.userId())
                        .build();

                response = accessService.devAgentLogin(agentRequest);

            } else {
                log.debug("Executing session login for user={} character={} actor={} entryPoint={} in world={}",
                        request.userId(), request.characterId(), request.actor(), request.entryPoint(), request.worldId());

                DevSessionLoginRequest sessionRequest = DevSessionLoginRequest.builder()
                        .worldId(request.worldId())
                        .userId(request.userId())
                        .characterId(request.characterId())
                        .actor(request.actor())
                        .entryPoint(request.entryPoint())
                        .build();

                response = accessService.devSessionLogin(sessionRequest);
            }

            log.info("Dev login successful - worldId={}, userId={}, agent={}",
                    request.worldId(), request.userId(), request.isAgent());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Business validation failures (world not found, user not found, etc.)
            log.warn("Dev login validation failed: {}", e.getMessage());
            return notFound(e.getMessage());

        } catch (IllegalStateException e) {
            // Access denied, permission issues
            log.warn("Dev login access denied: {}", e.getMessage());
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            // Unexpected errors
            log.error("Dev login failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ===== GET /control/aaa/authorize =====

    /**
     * Authorize with access token and set session cookies.
     * Validates the access token and creates long-lived session cookies.
     *
     * @param token Access token from /devlogin
     * @param response HttpServletResponse for cookie setting
     * @return 200 OK if successful
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam String token,
            HttpServletResponse response
    ) {
        log.debug("GET /control/aaa/authorize - validating token");

        try {
            accessService.authorizeWithToken(token, response);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            // Token validation failures
            log.warn("Token validation failed: {}", e.getMessage(), e);
            return unauthorized("Invalid or expired token");

        } catch (IllegalStateException e) {
            // Session/access validation failures
            log.warn("Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            // Unexpected errors
            log.error("Authorization failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ===== POST /control/aaa/status =====

    /**
     * Get current session status from sessionToken cookie.
     * Returns user info, world info, and URLs for logout.
     *
     * @return Status response with user data and URLs
     */
    @PostMapping("/status")
    public ResponseEntity<?> getStatus(jakarta.servlet.http.HttpServletRequest request) {
        log.debug("POST /control/aaa/status");

        try {
            var statusResponse = accessService.getSessionStatus(request);
            return ResponseEntity.ok(statusResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Status check failed: {}", e.getMessage());
            return unauthorized("Not authenticated or invalid token");

        } catch (Exception e) {
            log.error("Status check failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ===== DELETE /control/aaa/authorize =====

    /**
     * Logout by removing session cookies (DELETE login).
     * Clears the sessionToken cookie by setting MaxAge=0.
     *
     * @param response HttpServletResponse for cookie clearing
     * @return 200 OK
     */
    @DeleteMapping("/authorize")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        log.debug("DELETE /control/aaa/authorize");

        try {
            accessService.logout(response);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }
}
