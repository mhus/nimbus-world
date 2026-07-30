package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WEditCacheDirtyService;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes transient cache data: dirty chunks, edit cache, and edit cache dirty markers.
 * Delegates all data access to the owner services (data ownership).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteCacheService implements DeleteWorldResources {

    private final WDirtyChunkService dirtyChunkService;
    private final WEditCacheService editCacheService;
    private final WEditCacheDirtyService editCacheDirtyService;

    @Override
    public String name() {
        return "cache";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting cache data for world {}", worldId);

        long dirtyChunks = dirtyChunkService.deleteByWorldId(worldId);
        long editCache = editCacheService.deleteByWorldId(worldId);
        long editCacheDirty = editCacheDirtyService.deleteByWorldId(worldId);

        log.info("Deleted cache for world {}: {} dirty chunks, {} edit cache, {} edit cache dirty",
                worldId, dirtyChunks, editCache, editCacheDirty);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(dirtyChunkService.findDistinctWorldIds());
        worldIds.addAll(editCacheService.findDistinctWorldIds());
        worldIds.addAll(editCacheDirtyService.findDistinctWorldIds());
        return worldIds.stream().sorted().toList();
    }
}
