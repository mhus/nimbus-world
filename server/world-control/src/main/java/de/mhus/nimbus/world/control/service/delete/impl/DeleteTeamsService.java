package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.team.WTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteTeamsService implements DeleteWorldResources {

    private final WTeamService teamService;

    @Override
    public String name() {
        return "teams";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting teams for world {}", worldId);
        long deleted = teamService.deleteAllByWorldId(worldId);
        log.info("Deleted {} teams for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return teamService.findDistinctWorldIds();
    }
}
