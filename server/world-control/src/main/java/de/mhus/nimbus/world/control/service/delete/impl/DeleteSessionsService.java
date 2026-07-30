package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes world instances and player sessions for a world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSessionsService implements DeleteWorldResources {

    private final WPlayerSessionService playerSessionService;
    private final WWorldInstanceService worldInstanceService;

    @Override
    public String name() {
        return "sessions";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting sessions for world {}", worldId);

        int playerSessions = playerSessionService.deleteByWorldId(worldId);
        int worldInstances = worldInstanceService.deleteByWorldId(worldId);

        log.info("Deleted sessions for world {}: {} player sessions, {} world instances",
                worldId, playerSessions, worldInstances);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(playerSessionService.findDistinctWorldIds());
        worldIds.addAll(worldInstanceService.findDistinctWorldIds());
        return worldIds.stream().sorted().toList();
    }
}
