package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

/**
 * Service to duplicate items from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateItemsService implements DuplicateToWorld {

    private final WItemRepository itemRepository;

    @Override
    public String name() {
        return "items";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating items from world {} to {}", sourceWorldId, targetWorldId);

        List<WItem> sourceItems = itemRepository.findByWorldId(sourceWorldId);
        log.info("Found {} items in source world {}", sourceItems.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WItem source : sourceItems) {
            WItem target = WItem.builder()
                    .worldId(targetWorldId)
                    .itemId(source.getItemId())
                    .publicData(source.getPublicData())
                    .server(source.getServer() != null ? new HashMap<>(source.getServer()) : null)
                    .enabled(source.isEnabled())
                    .build();

            target.touchCreate();
            itemRepository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} items from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
