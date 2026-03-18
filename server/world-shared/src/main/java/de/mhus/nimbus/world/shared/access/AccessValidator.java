package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Patterns to extract worldId from URL path.
     * WORLD_PATH_PATTERN: matches /worlds/{worldId} or /world/{worldId} anywhere in the path.
     * EDITOR_PATH_PATTERN: matches /control/editor/{worldId}/... for EditorController.
     */
    private static final Pattern WORLD_PATH_PATTERN = Pattern.compile(".*/worlds?/([^/]+)(?:/.*)?$");
    private static final Pattern EDITOR_PATH_PATTERN = Pattern.compile("^/control/editor/([^/]+)(?:/.*)?$");

    /**
     * Pattern to extract regionId from URL path: /control/regions/{regionId}/...
     */
    private static final Pattern REGION_PATH_PATTERN = Pattern.compile("^/control/regions/([^/]+)(?:/.*)?$");

    private final RUserService userService;
    private final WWorldService worldService;
    private final EditorWorldAccessService editorWorldAccessService;
    private final RRegionService regionService;

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
     * Checks if the user has a specific world role for the world identified by the URL path.
     * Extracts worldId from the request URI (not from the session token).
     * Uses the cached EditorWorldAccessService for EDITOR role checks.
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
        if (userIdOpt.isEmpty()) {
            return false;
        }

        // Sector admins bypass world role checks
        if (isSectorAdmin(request)) {
            return true;
        }

        Optional<String> worldIdOpt = extractWorldIdFromPath(request);
        if (worldIdOpt.isEmpty()) {
            log.warn("No worldId found in request path: {}", request.getRequestURI());
            return false;
        }

        String userId = userIdOpt.get().getId();
        String worldId = worldIdOpt.get();

        try {
            // For EDITOR and OWNER role checks, use the cached access service
            if (role == WorldRoles.EDITOR || role == WorldRoles.OWNER) {
                return editorWorldAccessService.hasWorldAccess(userId, worldId);
            }

            // For other roles (PLAYER, SUPPORT), fall back to direct WWorld lookup.
            // Always check against main world (permissions are inherited from main world).
            String mainWorldId = WorldId.unchecked(worldId).toMainWorld().getId();
            Optional<WWorld> worldOpt = worldService.getByWorldId(mainWorldId);
            if (worldOpt.isEmpty()) {
                log.warn("Main world not found: {}", mainWorldId);
                return false;
            }

            WWorld world = worldOpt.get();
            List<WorldRoles> userRoles = world.getRolesForUser(userIdOpt.get());
            return userRoles.contains(role);
        } catch (Exception e) {
            log.error("Error checking world role for user {} in world {}: {}",
                    userId, worldId, e.getMessage());
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
        return hasWorldRole(request, WorldRoles.OWNER);
    }

    /**
     * Checks if the user is a world editor (or owner).
     * Uses the cached EditorWorldAccessService.
     *
     * @param request The HTTP request
     * @return true if user is world editor, false otherwise
     */
    public boolean isWorldEditor(HttpServletRequest request) {
        return hasWorldRole(request, WorldRoles.EDITOR);
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

    // ===== Region Maintainer Check =====

    /**
     * Checks if the user is a maintainer of the region identified by the URL path.
     * Sector admins bypass this check.
     *
     * @param request The HTTP request
     * @return true if user is region maintainer or sector admin, false otherwise
     */
    public boolean isRegionMaintainer(HttpServletRequest request) {
        if (!isAuthenticated(request)) {
            return false;
        }

        Optional<UserId> userIdOpt = getUserId(request);
        if (userIdOpt.isEmpty()) {
            return false;
        }

        // Sector admins bypass region maintainer checks
        if (isSectorAdmin(request)) {
            return true;
        }

        Optional<String> regionIdOpt = extractRegionIdFromPath(request);
        if (regionIdOpt.isEmpty()) {
            log.warn("No regionId found in request path: {}", request.getRequestURI());
            return false;
        }

        try {
            Optional<RRegion> regionOpt = regionService.getById(regionIdOpt.get());
            if (regionOpt.isEmpty()) {
                // Try by name as fallback
                regionOpt = regionService.getByName(regionIdOpt.get());
            }
            if (regionOpt.isEmpty()) {
                log.warn("Region not found: {}", regionIdOpt.get());
                return false;
            }

            return regionOpt.get().hasMaintainer(userIdOpt.get().getId());
        } catch (Exception e) {
            log.error("Error checking region maintainer for user {} in region {}: {}",
                    userIdOpt.get().getId(), regionIdOpt.get(), e.getMessage());
            return false;
        }
    }

    // ===== Sector Admin Check =====

    /**
     * Checks if the current user is a sector admin.
     */
    public boolean isSectorAdmin(HttpServletRequest request) {
        return hasSectorRole(request, SectorRoles.ADMIN);
    }

    // ===== Explicit World Access Checks (for controllers without URL-path worldId) =====

    /**
     * Checks if the current user has editor access to the given worldId.
     * Resolves to main world for permission check. Sector admins always have access.
     *
     * @param request The HTTP request (for user identification)
     * @param worldId The worldId to check (can be zone, instance, or main)
     * @return true if user has editor access
     */
    public boolean hasEditorAccess(HttpServletRequest request, String worldId) {
        if (isSectorAdmin(request)) return true;
        String userId = getUserId(request).map(UserId::getId).orElse(null);
        if (userId == null) return false;
        return editorWorldAccessService.hasWorldAccess(userId, worldId);
    }

    /**
     * Checks if a world is accessible to the given user (for list result filtering).
     * Sector admins see all worlds.
     *
     * @param request The HTTP request (for user identification)
     * @param world The world entity to check
     * @return true if the world is accessible
     */
    public boolean isWorldAccessible(HttpServletRequest request, WWorld world) {
        if (isSectorAdmin(request)) return true;
        String userId = getUserId(request).map(UserId::getId).orElse(null);
        if (userId == null) return false;
        return editorWorldAccessService.hasWorldAccess(userId, world.getWorldId());
    }

    // ===== URL Path Extraction =====

    /**
     * Extracts worldId from the request URL path.
     * Tries multiple patterns: /worlds/{worldId}, /world/{worldId}, /control/editor/{worldId}
     */
    public Optional<String> extractWorldIdFromPath(HttpServletRequest request) {
        String uri = request.getRequestURI();

        Matcher matcher = WORLD_PATH_PATTERN.matcher(uri);
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }

        matcher = EDITOR_PATH_PATTERN.matcher(uri);
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    /**
     * Extracts regionId from the request URL path.
     * Pattern: /control/regions/{regionId}/...
     */
    public Optional<String> extractRegionIdFromPath(HttpServletRequest request) {
        Matcher matcher = REGION_PATH_PATTERN.matcher(request.getRequestURI());
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
