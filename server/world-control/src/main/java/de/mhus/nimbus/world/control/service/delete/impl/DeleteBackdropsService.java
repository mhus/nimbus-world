package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WBackdropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteBackdropsService implements DeleteWorldResources {

    private final WBackdropService backdropService;

    @Override
    public String name() {
        return "backdrops";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting backdrops for world {}", worldId);
        int deleted = backdropService.deleteByWorldId(worldId);
        log.info("Deleted {} backdrops for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return backdropService.findDistinctWorldIds();
    }
}
