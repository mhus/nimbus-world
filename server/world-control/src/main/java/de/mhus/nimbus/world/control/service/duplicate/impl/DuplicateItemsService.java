package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate items from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateItemsService implements DuplicateToWorld {

    private final WItemService itemService;

    @Override
    public String name() {
        return "items";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating items from world {} to {}", sourceWorldId, targetWorldId);
        int duplicatedCount = itemService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} items from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
