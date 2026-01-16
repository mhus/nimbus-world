package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete hex grids for a given world.
 * Hex grids define hexagonal areas in the world with parameters and area data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteHexGridService implements DeleteWorldResources {

    private final WHexGridRepository hexGridRepository;

    @Override
    public String name() {
        return "hexGrids";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting hex grids for world {}", worldId);

        List<WHexGrid> hexGrids = hexGridRepository.findByWorldId(worldId);
        log.info("Found {} hex grids in world {}", hexGrids.size(), worldId);

        hexGridRepository.deleteAll(hexGrids);

        log.info("Deleted {} hex grids for world {}", hexGrids.size(), worldId);
    }
}
