package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.session.WPlayerSession;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
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
 * Deletes world instances and player sessions for a world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSessionsService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "sessions";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting sessions for world {}", worldId);
        Query query = new Query(Criteria.where("worldId").is(worldId));

        var playerSessions = mongoTemplate.remove(query, WPlayerSession.class);
        var worldInstances = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WWorldInstance.class);

        log.info("Deleted sessions for world {}: {} player sessions, {} world instances",
                worldId, playerSessions.getDeletedCount(), worldInstances.getDeletedCount());
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WPlayerSession.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WWorldInstance.class, String.class));
        return worldIds.stream().sorted().toList();
    }
}
