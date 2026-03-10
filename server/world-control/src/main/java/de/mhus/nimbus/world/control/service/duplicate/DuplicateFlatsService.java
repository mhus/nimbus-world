package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatRepository;
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
 * Service to duplicate flats from source world to target world.
 * Uses MongoTemplate with raw Documents to preserve byte arrays and nested structures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateFlatsService implements DuplicateToWorld {

    private static final String COLLECTION_NAME = "w_flats";

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "flats";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating flats from world {} to {}", sourceWorldId, targetWorldId);

        Query query = new Query(Criteria.where("worldId").is(sourceWorldId));
        List<Document> sourceDocuments = mongoTemplate.find(query, Document.class, COLLECTION_NAME);
        log.info("Found {} flats in source world {}", sourceDocuments.size(), sourceWorldId);

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

        log.info("Duplicated {} flats from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
