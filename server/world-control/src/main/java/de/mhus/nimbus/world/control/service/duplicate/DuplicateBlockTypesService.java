package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service to duplicate block types from source world to target world.
 * Only duplicates world-specific block types (where worldId matches source).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateBlockTypesService implements DuplicateToWorld {

    private final WBlockTypeRepository blockTypeRepository;

    @Override
    public String name() {
        return "blockTypes";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating block types from world {} to {}", sourceWorldId, targetWorldId);

        List<WBlockType> sourceBlockTypes = blockTypeRepository.findByWorldId(sourceWorldId);
        log.info("Found {} block types in source world {}", sourceBlockTypes.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WBlockType sourceBlockType : sourceBlockTypes) {
            WBlockType targetBlockType = WBlockType.builder()
                    .blockId(sourceBlockType.getBlockId())
                    .publicData(sourceBlockType.getPublicData())
                    .worldId(targetWorldId)
                    .enabled(sourceBlockType.isEnabled())
                    .build();

            targetBlockType.touchCreate();

            blockTypeRepository.save(targetBlockType);
            duplicatedCount++;
        }

        log.info("Duplicated {} block types from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
