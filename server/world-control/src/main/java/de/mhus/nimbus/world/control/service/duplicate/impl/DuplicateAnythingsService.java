package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service to duplicate WAnything entries from source world to target world.
 * Uses MongoTemplate with raw Documents to preserve the dynamic 'data' field structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateAnythingsService implements DuplicateToWorld {

    private static final String COLLECTION_NAME = "w_anything";

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "anythings";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating anythings from world {} to {}", sourceWorldId, targetWorldId);

        Query query = new Query(Criteria.where("worldId").is(sourceWorldId));
        List<Document> sourceDocuments = mongoTemplate.find(query, Document.class, COLLECTION_NAME);
        log.info("Found {} anythings in source world {}", sourceDocuments.size(), sourceWorldId);

        int duplicatedCount = 0;
        Instant now = Instant.now();

        for (Document source : sourceDocuments) {
            Document target = new Document(source);
            target.remove("_id");
            target.put("worldId", targetWorldId);
            target.put("createdAt", now);
            target.put("updatedAt", now);

            mongoTemplate.save(target, COLLECTION_NAME);
            duplicatedCount++;
        }

        log.info("Duplicated {} anythings from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
