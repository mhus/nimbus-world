package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.job.WJobService;
import de.mhus.nimbus.world.shared.workflow.WWorkflowJournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes jobs and workflow journal records for a world.
 * Delegates to the owner services (WJobService / WWorkflowJournalService) instead
 * of touching the collections / MongoTemplate directly (data ownership).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteJobsService implements DeleteWorldResources {

    private final WJobService jobService;
    private final WWorkflowJournalService workflowJournalService;

    @Override
    public String name() {
        return "jobs";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting jobs and workflow records for world {}", worldId);

        int jobs = jobService.deleteByWorldId(worldId);
        int records = workflowJournalService.deleteByWorldId(worldId);

        log.info("Deleted for world {}: {} jobs, {} workflow records",
                worldId, jobs, records);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(jobService.findDistinctWorldIds());
        worldIds.addAll(workflowJournalService.findDistinctWorldIds());
        return worldIds.stream().sorted().toList();
    }
}
