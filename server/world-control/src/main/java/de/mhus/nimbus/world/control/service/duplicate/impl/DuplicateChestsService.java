package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to duplicate chests from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateChestsService implements DuplicateToWorld {

    private final WChestRepository chestRepository;

    @Override
    public String name() {
        return "chests";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating chests from world {} to {}", sourceWorldId, targetWorldId);

        List<WChest> sourceChests = chestRepository.findByWorldId(sourceWorldId);
        log.info("Found {} chests in source world {}", sourceChests.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WChest source : sourceChests) {
            WChest target = WChest.builder()
                    .worldId(targetWorldId)
                    .name(source.getName())
                    .title(source.getTitle())
                    .description(source.getDescription())
                    .playerId(source.getPlayerId())
                    .type(source.getType())
                    .pin(source.getPin())
                    .capacity(source.getCapacity())
                    .keyId(source.getKeyId())
                    .lockPickingDifficulty(source.getLockPickingDifficulty())
                    .items(source.getItems() != null ? new ArrayList<>(source.getItems()) : new ArrayList<>())
                    .enabled(source.isEnabled())
                    .build();

            target.touchCreate();
            chestRepository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} chests from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
