package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WChestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate chests from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateChestsService implements DuplicateToWorld {

    private final WChestService chestService;

    @Override
    public String name() {
        return "chests";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating chests from world {} to {}", sourceWorldId, targetWorldId);
        chestService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
