package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate item positions from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateItemPositionsService implements DuplicateToWorld {

    private final WItemPositionService itemPositionService;

    @Override
    public String name() {
        return "itemPositions";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating item positions from world {} to {}", sourceWorldId, targetWorldId);
        int duplicatedCount = itemPositionService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} item positions from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
