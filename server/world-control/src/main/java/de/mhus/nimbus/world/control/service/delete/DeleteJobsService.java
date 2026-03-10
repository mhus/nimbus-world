package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.workflow.WWorkflowJournalRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes jobs and workflow journal records for a world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteJobsService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "jobs";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting jobs and workflow records for world {}", worldId);
        Query query = new Query(Criteria.where("worldId").is(worldId));

        var jobs = mongoTemplate.remove(query, WJob.class);
        var records = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WWorkflowJournalRecord.class);

        log.info("Deleted for world {}: {} jobs, {} workflow records",
                worldId, jobs.getDeletedCount(), records.getDeletedCount());
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WJob.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WWorkflowJournalRecord.class, String.class));
        return worldIds.stream().sorted().toList();
    }
}
