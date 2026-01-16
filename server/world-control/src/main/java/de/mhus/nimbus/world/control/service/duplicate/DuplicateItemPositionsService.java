package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to duplicate item positions from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateItemPositionsService implements DuplicateToWorld {

    private final WItemPositionRepository itemPositionRepository;

    @Override
    public String name() {
        return "itemPositions";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating item positions from world {} to {}", sourceWorldId, targetWorldId);

        List<WItemPosition> sourceItemPositions = itemPositionRepository.findByWorldId(sourceWorldId);
        log.info("Found {} item positions in source world {}", sourceItemPositions.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WItemPosition sourceItemPosition : sourceItemPositions) {
            WItemPosition targetItemPosition = WItemPosition.builder()
                    .worldId(targetWorldId)
                    .itemId(sourceItemPosition.getItemId())
                    .chunk(sourceItemPosition.getChunk())
                    .publicData(sourceItemPosition.getPublicData())
                    .enabled(sourceItemPosition.isEnabled())
                    .build();

            targetItemPosition.touchCreate();
            itemPositionRepository.save(targetItemPosition);
            duplicatedCount++;
        }

        log.info("Duplicated {} item positions from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
