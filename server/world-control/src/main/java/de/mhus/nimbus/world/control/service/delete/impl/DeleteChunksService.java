package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete chunks for a given world.
 * Delegates to WChunkService (the owner of WChunk) so external storage and the
 * associated WChunkInfo documents are cleaned up consistently, instead of
 * touching the WChunk repository / MongoTemplate directly (data ownership).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteChunksService implements DeleteWorldResources {

    private final WChunkService chunkService;

    @Override
    public String name() {
        return "chunks";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting chunks for world {}", worldId);
        int deleted = chunkService.deleteAllByWorldId(worldId);
        log.info("Deleted {} chunks for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return chunkService.findDistinctWorldIds();
    }
}
