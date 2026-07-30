package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete item positions for a given world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteItemPositionsService implements DeleteWorldResources {

    private final WItemPositionService itemPositionService;

    @Override
    public String name() {
        return "itemPositions";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting item positions for world {}", worldId);
        int deleted = itemPositionService.deleteAllByWorldId(worldId);
        log.info("Deleted {} item positions for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return itemPositionService.findDistinctWorldIds();
    }
}
