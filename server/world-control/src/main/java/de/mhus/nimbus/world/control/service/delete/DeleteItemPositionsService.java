package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionRepository;
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

    private final WItemPositionRepository itemPositionRepository;

    @Override
    public String name() {
        return "itemPositions";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting item positions for world {}", worldId);

        List<WItemPosition> itemPositions = itemPositionRepository.findByWorldId(worldId);
        log.info("Found {} item positions in world {}", itemPositions.size(), worldId);

        itemPositionRepository.deleteAll(itemPositions);

        log.info("Deleted {} item positions for world {}", itemPositions.size(), worldId);
    }
}
