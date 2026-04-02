package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WTrader;
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
public class DeleteTradersService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "traders";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting traders for world {}", worldId);
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WTrader.class
        );
        log.info("Deleted {} traders for world {}", result.getDeletedCount(), worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return mongoTemplate.findDistinct(new Query(), "worldId", WTrader.class, String.class);
    }
}
