package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteItemsService implements DeleteWorldResources {

    private final WItemService itemService;

    @Override
    public String name() {
        return "items";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting items for world {}", worldId);
        int deleted = itemService.deleteAllByWorldId(worldId);
        log.info("Deleted {} items for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return itemService.findDistinctWorldIds();
    }
}
