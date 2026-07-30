package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WHexGridService;
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

    private final WHexGridService hexGridService;

    @Override
    public String name() {
        return "hexGrids";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting hex grids for world {}", worldId);

        int deleted = hexGridService.deleteAllByWorldId(worldId);

        log.info("Deleted {} hex grids for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return hexGridService.findDistinctWorldIds();
    }
}
