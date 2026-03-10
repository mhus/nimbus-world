package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WItem;
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
public class DeleteItemsService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "items";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting items for world {}", worldId);
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WItem.class
        );
        log.info("Deleted {} items for world {}", result.getDeletedCount(), worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return mongoTemplate.findDistinct(new Query(), "worldId", WItem.class, String.class);
    }
}
