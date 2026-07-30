package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate chunks from source world to target world.
 * Also duplicates associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateChunksService implements DuplicateToWorld {

    private final WChunkService chunkService;

    @Override
    public String name() {
        return "chunks";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating chunks from world {} to {}", sourceWorldId, targetWorldId);
        chunkService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
