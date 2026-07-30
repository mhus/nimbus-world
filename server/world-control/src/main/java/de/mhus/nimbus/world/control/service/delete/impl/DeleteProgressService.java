package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteProgressService implements DeleteWorldResources {

    private final WProgressService progressService;

    @Override
    public String name() {
        return "progress";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting progress for world {}", worldId);
        int deleted = progressService.deleteAllByWorldId(worldId);
        log.info("Deleted {} progress entries for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return progressService.findDistinctWorldIds();
    }
}
