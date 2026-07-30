package de.mhus.nimbus.world.shared.world;

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
 * Shared repair engine for finding and removing duplicate documents based on a
 * unique business key. Invoked by owner services (data ownership) to deduplicate
 * their own collections.
 * <p>
 * Operates on raw {@link Document} instances so that legacy rows lacking the
 * {@code _schema} field are still detected. Duplicates are removed precisely by
 * {@code _id}, keeping one document per key group (prefer the one with a
 * {@code _schema} field, otherwise the latest {@code createdAt}).
 */
@Slf4j
public final class DuplicateRepairHelper {

    private DuplicateRepairHelper() {}

    /**
     * Find and remove duplicate documents in the collection backing the given entity class.
     *
     * @param mongoTemplate MongoTemplate instance
     * @param entityClass   entity class whose backing collection is repaired
     * @param typeName      type name for logging/result
     * @param worldId       World ID to scope the repair (raw stored worldId)
     * @param keyExtractor  function to extract the unique key from a document
     * @return neutral repair result with details
     */
    public static DuplicateRepairResult repairDuplicates(
            MongoTemplate mongoTemplate,
            Class<?> entityClass,
            String typeName,
            String worldId,
            Function<Document, String> keyExtractor
    ) {
        String collectionName = mongoTemplate.getCollectionName(entityClass);
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

        return new DuplicateRepairResult(
                typeName,
                true,
                String.format("Duplicates found: %d, removed: %d", duplicatesFound, duplicatesRemoved),
                System.currentTimeMillis(),
                duplicatesFound,
                duplicatesRemoved
        );
    }

    /**
     * Select which document to keep when duplicates are found.
     * Priority: 1. Document with _schema field, 2. Latest createdAt, 3. First in list.
     * <p>
     * Package-private so owner services with bespoke dedup logic (e.g. asset
     * repair) can reuse the exact same selection rule.
     */
    static Document selectDocumentToKeep(List<Document> documents) {
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
