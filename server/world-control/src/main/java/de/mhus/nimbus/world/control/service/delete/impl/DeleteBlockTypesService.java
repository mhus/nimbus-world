package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete block types for a given world.
 * Only deletes world-specific block types (where worldId matches).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteBlockTypesService implements DeleteWorldResources {

    private final WBlockTypeService blockTypeService;

    @Override
    public String name() {
        return "blockTypes";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting block types for world {}", worldId);

        int deleted = blockTypeService.deleteAllByWorldId(worldId);

        log.info("Deleted {} block types for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return blockTypeService.findDistinctWorldIds();
    }
}
