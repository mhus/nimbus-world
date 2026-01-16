package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to duplicate hex grids from source world to target world.
 * Hex grids define hexagonal areas in the world with parameters and area data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateHexGridService implements DuplicateToWorld {

    private final WHexGridRepository hexGridRepository;

    @Override
    public String name() {
        return "hexGrids";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating hex grids from world {} to {}", sourceWorldId, targetWorldId);

        List<WHexGrid> sourceHexGrids = hexGridRepository.findByWorldId(sourceWorldId);
        log.info("Found {} hex grids in source world {}", sourceHexGrids.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WHexGrid sourceHexGrid : sourceHexGrids) {
            WHexGrid targetHexGrid = WHexGrid.builder()
                    .worldId(targetWorldId)
                    .position(sourceHexGrid.getPosition())
                    .publicData(sourceHexGrid.getPublicData())
                    .parameters(sourceHexGrid.getParameters())
                    .areas(sourceHexGrid.getAreas())
                    .enabled(sourceHexGrid.isEnabled())
                    .build();

            targetHexGrid.touchCreate();
            hexGridRepository.save(targetHexGrid);
            duplicatedCount++;
        }

        log.info("Duplicated {} hex grids from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
