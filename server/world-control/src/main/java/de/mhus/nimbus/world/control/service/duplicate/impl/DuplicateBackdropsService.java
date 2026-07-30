package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WBackdropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate backdrops from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateBackdropsService implements DuplicateToWorld {

    private final WBackdropService backdropService;

    @Override
    public String name() {
        return "backdrops";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating backdrops from world {} to {}", sourceWorldId, targetWorldId);
        int duplicatedCount = backdropService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} backdrops from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
