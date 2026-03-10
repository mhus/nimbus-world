package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper for finding and removing duplicate documents based on a unique key.
 * Used by entity-specific ResourceRepairer implementations.
 */
@Slf4j
public final class DuplicateRepairHelper {

    private DuplicateRepairHelper() {}

    /**
     * Find and remove duplicate documents in a collection.
     *
     * @param mongoTemplate  MongoTemplate instance
     * @param collectionName MongoDB collection name
     * @param worldId        World ID to scope the repair
     * @param typeName       Type name for logging/result
     * @param keyExtractor   Function to extract the unique key from a document
     * @return ProcessResult with repair details
     */
    public static ResourceRepairService.ProcessResult repairDuplicates(
            MongoTemplate mongoTemplate,
            String collectionName,
            String worldId,
            String typeName,
            Function<Document, String> keyExtractor
    ) {
        log.info("Starting {} repair for world {}", typeName, worldId);

        Query query = new Query(Criteria.where("worldId").is(worldId));
        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);

        log.info("Found {} total {} documents for world {}", documents.size(), typeName, worldId);

        // Group by unique key to find duplicates
        Map<String, List<Document>> grouped = documents.stream()
                .filter(doc -> keyExtractor.apply(doc) != null)
                .collect(Collectors.groupingBy(keyExtractor));

        int duplicatesFound = 0;
        int duplicatesRemoved = 0;

        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            List<Document> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;

            duplicatesFound += duplicates.size() - 1;
            log.warn("Found {} duplicates for {} key: {}", duplicates.size(), typeName, entry.getKey());

            Document toKeep = selectDocumentToKeep(duplicates);
            log.info("Keeping document _id: {} (has _schema: {})", toKeep.get("_id"), toKeep.containsKey("_schema"));

            for (Document doc : duplicates) {
                if (doc.get("_id").equals(toKeep.get("_id"))) continue;

                Object docId = doc.get("_id");
                log.info("Removing duplicate {} _id: {}", typeName, docId);
                mongoTemplate.remove(new Query(Criteria.where("_id").is(docId)), collectionName);
                duplicatesRemoved++;
            }
        }

        log.info("{} repair completed: {} duplicates found, {} removed", typeName, duplicatesFound, duplicatesRemoved);

        return new ResourceRepairService.ProcessResult(
                typeName,
                true,
                String.format("Duplicates found: %d, removed: %d", duplicatesFound, duplicatesRemoved),
                System.currentTimeMillis()
        );
    }

    /**
     * Select which document to keep when duplicates are found.
     * Priority: 1. Document with _schema field, 2. Latest createdAt, 3. First in list.
     */
    private static Document selectDocumentToKeep(List<Document> documents) {
        Optional<Document> withSchema = documents.stream()
                .filter(doc -> doc.containsKey("_schema"))
                .findFirst();
        if (withSchema.isPresent()) return withSchema.get();

        return documents.stream()
                .max(Comparator.comparing(doc -> {
                    Object createdAt = doc.get("createdAt");
                    return createdAt != null ? createdAt.toString() : "";
                }))
                .orElse(documents.getFirst());
    }
}
