package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteAnythingsService implements DeleteWorldResources {

    private final WAnythingService anythingService;

    @Override
    public String name() {
        return "anythings";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting anythings for world {}", worldId);
        int deleted = anythingService.deleteAllByWorldId(worldId);
        log.info("Deleted {} anythings for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return anythingService.findDistinctWorldIds();
    }
}
