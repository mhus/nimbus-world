package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Utility class for extracting and validating access control information from HTTP requests.
 *
 * This class provides methods to check roles, authentication status, and extract user/world
 * information from request attributes that were set by AccessFilterBase.
 *
 * Business Logic Layer:
 * - Extracts authentication data from request attributes
 * - Validates roles and permissions
 * - Provides type-safe access to user/world identifiers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessValidator {

    private final RUserService userService;
    private final WWorldService worldService;

    // ===== Authentication Status =====

    /**
     * Checks if the request is authenticated.
     *
     * @param request The HTTP request
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated(HttpServletRequest request) {
        Boolean authenticated = (Boolean) request.getAttribute(AccessFilterBase.ATTR_IS_AUTHENTICATED);
        return authenticated != null && authenticated;
    }

    /**
     * Checks if the request is from an agent (non-session authentication).
     *
     * @param request The HTTP request
     * @return true if agent authentication, false otherwise
     */
    public boolean isAgent(HttpServletRequest request) {
        Boolean agent = (Boolean) request.getAttribute(AccessFilterBase.ATTR_IS_AGENT);
        return agent != null && agent;
    }

    /**
     * Checks if the request has a session (non-agent authentication).
     *
     * @param request The HTTP request
     * @return true if session exists, false otherwise
     */
    public boolean hasSession(HttpServletRequest request) {
        return isAuthenticated(request) && !isAgent(request);
    }

    // ===== User Information =====

    /**
     * Gets the user ID from the request.
     *
     * @param request The HTTP request
     * @return UserId or Optional.empty() if not present or invalid
     */
    public Optional<UserId> getUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (userId == null) {
            return Optional.empty();
        }
        return UserId.of(userId);
    }

    /**
     * Gets the character ID from the request.
     *
     * @param request The HTTP request
     * @return Character ID string or null if not present
     */
    public String getCharacterId(HttpServletRequest request) {
        return (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
    }

    /**
     * Gets the player ID from the request (format: @userId:characterId).
     *
     * @param request The HTTP request
     * @return PlayerId or Optional.empty() if not present or invalid
     */
    public Optional<PlayerId> getPlayerId(HttpServletRequest request) {
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (userId == null || characterId == null) {
            return Optional.empty();
        }

        return PlayerId.of("@" + userId + ":" + characterId);
    }

    /**
     * Gets the session ID from the request.
     *
     * @param request The HTTP request
     * @return Session ID string or null if not present
     */
    public String getSessionId(HttpServletRequest request) {
        return (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
    }

    // ===== World Information =====

    /**
     * Gets the world ID from the request.
     *
     * @param request The HTTP request
     * @return WorldId or Optional.empty() if not present or invalid
     */
    public Optional<WorldId> getWorldId(HttpServletRequest request) {
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        if (worldId == null) {
            return Optional.empty();
        }
        return WorldId.of(worldId);
    }

    /**
     * Checks if the world is an instance.
     *
     * @param request The HTTP request
     * @return true if world is an instance, false otherwise
     */
    public boolean isWorldInstance(HttpServletRequest request) {
        return getWorldId(request)
                .map(WorldId::isInstance)
                .orElse(false);
    }

    /**
     * Checks if the world is a zone.
     *
     * @param request The HTTP request
     * @return true if world is a zone, false otherwise
     */
    public boolean isWorldZone(HttpServletRequest request) {
        return getWorldId(request)
                .map(WorldId::isZone)
                .orElse(false);
    }

    /**
     * Checks if the world is a collection.
     *
     * @param request The HTTP request
     * @return true if world is a collection, false otherwise
     */
    public boolean isWorldCollection(HttpServletRequest request) {
        return getWorldId(request)
                .map(WorldId::isCollection)
                .orElse(false);
    }

    /**
     * Checks if the world is a main world (no zone, branch, or instance).
     *
     * @param request The HTTP request
     * @return true if world is main, false otherwise
     */
    public boolean isWorldSet(HttpServletRequest request) {
        return getWorldId(request)
                .map(WorldId::isMain)
                .orElse(false);
    }

    // ===== Role Checks =====

    /**
     * Checks if the user has a specific sector role.
     *
     * @param request The HTTP request
     * @param role The sector role to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasSectorRole(HttpServletRequest request, SectorRoles role) {
        if (!isAuthenticated(request)) {
            return false;
        }

        Optional<UserId> userIdOpt = getUserId(request);
        if (userIdOpt.isEmpty()) {
            return false;
        }

        try {
            var userRoles = userService.getRoles(userIdOpt.get().getId());
            return userRoles.contains(SectorRoles.ADMIN) || userRoles.contains(role);
        } catch (Exception e) {
            log.error("Error checking sector role for user {}: {}", userIdOpt.get().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the user has a specific world role.
     *
     * @param request The HTTP request
     * @param role The world role to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasWorldRole(HttpServletRequest request, WorldRoles role) {
        if (!isAuthenticated(request)) {
            return false;
        }

        Optional<UserId> userIdOpt = getUserId(request);
        Optional<WorldId> worldIdOpt = getWorldId(request);

        if (userIdOpt.isEmpty() || worldIdOpt.isEmpty()) {
            return false;
        }

        try {
            Optional<WWorld> worldOpt = worldService.getByWorldId(worldIdOpt.get().getId());
            if (worldOpt.isEmpty()) {
                log.warn("World not found: {}", worldIdOpt.get().getId());
                return false;
            }

            WWorld world = worldOpt.get();
            List<WorldRoles> userRoles = world.getRolesForUser(userIdOpt.get());
            return userRoles.contains(role);
        } catch (Exception e) {
            log.error("Error checking world role for user {} in world {}: {}",
                    userIdOpt.get().getId(), worldIdOpt.get().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the user is the world owner.
     *
     * @param request The HTTP request
     * @return true if user is world owner, false otherwise
     */
    public boolean isWorldOwner(HttpServletRequest request) {
        if (!isAuthenticated(request)) {
            return false;
        }

        Optional<UserId> userIdOpt = getUserId(request);
        Optional<WorldId> worldIdOpt = getWorldId(request);

        if (userIdOpt.isEmpty() || worldIdOpt.isEmpty()) {
            return false;
        }

        try {
            Optional<WWorld> worldOpt = worldService.getByWorldId(worldIdOpt.get().getId());
            if (worldOpt.isEmpty()) {
                return false;
            }

            WWorld world = worldOpt.get();
            return world.isOwnerAllowed(userIdOpt.get());
        } catch (Exception e) {
            log.error("Error checking world owner for user {} in world {}: {}",
                    userIdOpt.get().getId(), worldIdOpt.get().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the user is a world editor.
     *
     * @param request The HTTP request
     * @return true if user is world editor, false otherwise
     */
    public boolean isWorldEditor(HttpServletRequest request) {
        if (!isAuthenticated(request)) {
            return false;
        }

        Optional<UserId> userIdOpt = getUserId(request);
        Optional<WorldId> worldIdOpt = getWorldId(request);

        if (userIdOpt.isEmpty() || worldIdOpt.isEmpty()) {
            return false;
        }

        try {
            Optional<WWorld> worldOpt = worldService.getByWorldId(worldIdOpt.get().getId());
            if (worldOpt.isEmpty()) {
                return false;
            }

            WWorld world = worldOpt.get();
            return world.isEditorAllowed(userIdOpt.get());
        } catch (Exception e) {
            log.error("Error checking world editor for user {} in world {}: {}",
                    userIdOpt.get().getId(), worldIdOpt.get().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the user is a world player.
     *
     * @param request The HTTP request
     * @return true if user is world player, false otherwise
     */
    public boolean isWorldPlayer(HttpServletRequest request) {
        return hasWorldRole(request, WorldRoles.PLAYER);
    }
}
