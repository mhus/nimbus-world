package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WChestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteChestsService implements DeleteWorldResources {

    private final WChestService chestService;

    @Override
    public String name() {
        return "chests";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting chests for world {}", worldId);
        chestService.deleteAllByWorldId(worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return chestService.findDistinctWorldIds();
    }
}
