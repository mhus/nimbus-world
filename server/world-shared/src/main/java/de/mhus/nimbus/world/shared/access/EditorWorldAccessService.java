package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that provides cached access checks for editor world authorization.
 * Caches the set of worldIds (including collection worldIds) a user is allowed to edit.
 *
 * Regular worlds: user must be owner or editor on the main world.
 * Collection worldIds (@region:X, @public:X): user must be region maintainer of region X.
 * Other collection worldIds (@shared:..., etc.): sector admin only (not cached here).
 *
 * Cache TTL: 5 minutes. Evicted on world/region permission changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditorWorldAccessService {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private final WWorldRepository worldRepository;
    private final RRegionService regionService;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Set<String> worldIds, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Returns the set of worldIds the user is allowed to edit.
     * Includes main worldIds (from owner/editor lists) and collection worldIds
     * (@region:X, @public:X for regions where user is maintainer).
     * Uses a cached lookup with TTL.
     */
    public Set<String> getAccessibleWorldIds(String userId) {
        var entry = cache.get(userId);
        if (entry != null && !entry.isExpired()) {
            return entry.worldIds();
        }

        // Regular worlds: owner or editor
        Set<String> worldIds = new HashSet<>(worldRepository.findByOwnerOrEditor(userId).stream()
                .map(WWorld::getWorldId)
                .map(wid -> WorldId.unchecked(wid).toMainWorld().getId())
                .collect(Collectors.toSet()));

        // Collection worldIds: @region:<name> and @public:<name> for maintained regions
        for (RRegion region : regionService.listAll()) {
            if (region.hasMaintainer(userId)) {
                worldIds.add(WorldId.COLLECTION_REGION + ":" + region.getName());
                worldIds.add(WorldId.COLLECTION_PUBLIC + ":" + region.getName());
            }
        }

        cache.put(userId, new CacheEntry(worldIds, Instant.now().plusMillis(CACHE_TTL_MS)));
        log.debug("Cached {} accessible worlds for user {} (incl. collections)", worldIds.size(), userId);
        return worldIds;
    }

    /**
     * Checks if the user has editor/owner access to the given worldId.
     * For regular worlds: resolves to main worldId (regionId:worldName).
     * For collection worldIds (@...): checks directly against cached set.
     */
    public boolean hasWorldAccess(String userId, String worldId) {
        if (worldId.startsWith("@")) {
            // Collection worldId — check directly
            return getAccessibleWorldIds(userId).contains(worldId);
        }
        // Regular world — resolve to main world
        var mainWorldId = WorldId.unchecked(worldId).toMainWorld().getId();
        return getAccessibleWorldIds(userId).contains(mainWorldId);
    }

    /**
     * Evicts the cache for a specific user.
     */
    public void evictUser(String userId) {
        cache.remove(userId);
    }

    /**
     * Evicts the entire cache. Call when world permissions change.
     */
    public void evictAll() {
        cache.clear();
        log.debug("Editor world access cache cleared");
    }
}
