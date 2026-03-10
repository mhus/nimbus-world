package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.layer.WDirtyChunk;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheDirty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes transient cache data: dirty chunks, edit cache, and edit cache dirty markers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteCacheService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "cache";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting cache data for world {}", worldId);
        Query query = new Query(Criteria.where("worldId").is(worldId));

        var dirtyChunks = mongoTemplate.remove(query, WDirtyChunk.class);
        var editCache = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WEditCache.class);
        var editCacheDirty = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WEditCacheDirty.class);

        log.info("Deleted cache for world {}: {} dirty chunks, {} edit cache, {} edit cache dirty",
                worldId, dirtyChunks.getDeletedCount(), editCache.getDeletedCount(), editCacheDirty.getDeletedCount());
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WDirtyChunk.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WEditCache.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WEditCacheDirty.class, String.class));
        return worldIds.stream().sorted().toList();
    }
}
