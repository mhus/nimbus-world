package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteDocumentsService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "documents";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting documents for world {}", worldId);
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WDocument.class
        );
        log.info("Deleted {} documents for world {}", result.getDeletedCount(), worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return mongoTemplate.findDistinct(new Query(), "worldId", WDocument.class, String.class);
    }
}
