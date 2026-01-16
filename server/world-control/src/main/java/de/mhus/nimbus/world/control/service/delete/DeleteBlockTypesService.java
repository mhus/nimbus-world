package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeRepository;
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

    private final WBlockTypeRepository blockTypeRepository;

    @Override
    public String name() {
        return "blockTypes";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting block types for world {}", worldId);

        List<WBlockType> blockTypes = blockTypeRepository.findByWorldId(worldId);
        log.info("Found {} block types in world {}", blockTypes.size(), worldId);

        blockTypeRepository.deleteAll(blockTypes);

        log.info("Deleted {} block types for world {}", blockTypes.size(), worldId);
    }
}
