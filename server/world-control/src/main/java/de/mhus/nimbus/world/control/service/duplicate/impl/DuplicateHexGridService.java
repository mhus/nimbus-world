package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate hex grids from source world to target world.
 * Hex grids define hexagonal areas in the world with parameters and area data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateHexGridService implements DuplicateToWorld {

    private final WHexGridService hexGridService;

    @Override
    public String name() {
        return "hexGrids";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating hex grids from world {} to {}", sourceWorldId, targetWorldId);

        int duplicatedCount = hexGridService.duplicateToWorld(sourceWorldId, targetWorldId);

        log.info("Duplicated {} hex grids from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
