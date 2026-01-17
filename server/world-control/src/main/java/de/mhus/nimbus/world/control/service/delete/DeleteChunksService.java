package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete chunks for a given world.
 * Also deletes associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteChunksService implements DeleteWorldResources {

    private final WChunkRepository chunkRepository;
    private final StorageService storageService;
    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "chunks";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting chunks for world {}", worldId);

        List<WChunk> chunks = chunkRepository.findByWorldId(worldId);
        log.info("Found {} chunks in world {}", chunks.size(), worldId);

        int deletedCount = 0;
        int storageCount = 0;

        for (WChunk chunk : chunks) {
            // Delete storage data if present
            if (chunk.getStorageId() != null) {
                try {
                    storageService.delete(chunk.getStorageId());
                    storageCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete storage {} for chunk {}: {}",
                            chunk.getStorageId(), chunk.getChunk(), e.getMessage());
                }
            }

            chunkRepository.delete(chunk);
            deletedCount++;
        }

        log.info("Deleted {} chunks (including {} storage items) for world {}",
                deletedCount, storageCount, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                WChunk.class,
                String.class
        );
    }
}
