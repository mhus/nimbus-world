package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to duplicate chunks from source world to target world.
 * Also duplicates associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateChunksService implements DuplicateToWorld {

    private final WChunkRepository chunkRepository;
    private final StorageService storageService;

    @Override
    public String name() {
        return "chunks";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating chunks from world {} to {}", sourceWorldId, targetWorldId);

        List<WChunk> sourceChunks = chunkRepository.findByWorldId(sourceWorldId);
        log.info("Found {} chunks in source world {}", sourceChunks.size(), sourceWorldId);

        int duplicatedCount = 0;
        int storageCount = 0;

        for (WChunk sourceChunk : sourceChunks) {
            WChunk targetChunk = WChunk.builder()
                    .worldId(targetWorldId)
                    .chunk(sourceChunk.getChunk())
                    .compressed(sourceChunk.isCompressed())
                    .infoServer(sourceChunk.getInfoServer())
                    .build();

            // Duplicate storage data if present
            if (sourceChunk.getStorageId() != null) {
                String newStorageId = storageService.duplicate(sourceChunk.getStorageId(), targetWorldId);
                targetChunk.setStorageId(newStorageId);
                storageCount++;
            }

            targetChunk.touchCreate();
            chunkRepository.save(targetChunk);
            duplicatedCount++;
        }

        log.info("Duplicated {} chunks (including {} storage items) from world {} to {}",
                duplicatedCount, storageCount, sourceWorldId, targetWorldId);
    }
}
