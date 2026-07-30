package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate block types from source world to target world.
 * Only duplicates world-specific block types (where worldId matches source).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateBlockTypesService implements DuplicateToWorld {

    private final WBlockTypeService blockTypeService;

    @Override
    public String name() {
        return "blockTypes";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating block types from world {} to {}", sourceWorldId, targetWorldId);

        int duplicatedCount = blockTypeService.duplicateToWorld(sourceWorldId, targetWorldId);

        log.info("Duplicated {} block types from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
