package de.mhus.nimbus.world.shared.world;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared logic for epoch validation and creation across all epoch-aware entity types.
 * Owner services delegate to this helper so no other module manipulates their
 * collections directly (data ownership). The collection name is resolved from the
 * owning entity class, never hardcoded.
 */
@Slf4j
public final class EpochArrayHelper {

    private EpochArrayHelper() {}

    /**
     * Validate epoch consistency for a collection.
     * Auto-repairs: removes duplicate epoch entries and undefined epochs from documents.
     */
    public static EpochProcessResult validate(
            MongoTemplate mongoTemplate, Class<?> entityClass, String typeName,
            String worldId, List<WEpochMeta> epochMetas) {

        String collection = mongoTemplate.getCollectionName(entityClass);

        Set<Integer> definedEpochs = epochMetas.stream()
                .map(WEpochMeta::getEpoch)
                .collect(Collectors.toSet());

        List<String> issues = new ArrayList<>();
        List<String> repairs = new ArrayList<>();

        // 1. Find documents with empty epoches
        Query emptyEpochesQuery = new Query(Criteria.where("worldId").is(worldId)
                .andOperator(
                        new Criteria().orOperator(
                                Criteria.where("epoches").exists(false),
                                Criteria.where("epoches").size(0)
                        )
                ));
        long emptyCount = mongoTemplate.count(emptyEpochesQuery, collection);
        if (emptyCount > 0) {
            issues.add(emptyCount + " documents with empty/missing epoches");
        }

        // 2. Check all documents for duplicates, undefined epochs
        Query worldQuery = new Query(Criteria.where("worldId").is(worldId));
        List<Document> docs = mongoTemplate.find(worldQuery, Document.class, collection);

        Set<Integer> usedEpochs = new HashSet<>();
        int invalidEpochDocs = 0;
        int duplicateRepairs = 0;
        int undefinedRepairs = 0;

        for (Document doc : docs) {
            List<Integer> epoches = doc.getList("epoches", Integer.class);
            if (epoches == null || epoches.isEmpty()) continue;

            // Detect duplicates and undefined epochs
            List<Integer> cleaned = epoches.stream()
                    .distinct()
                    .filter(definedEpochs::contains)
                    .sorted()
                    .toList();

            boolean hasDuplicates = epoches.size() != new HashSet<>(epoches).size();
            boolean hasUndefined = epoches.stream().anyMatch(e -> !definedEpochs.contains(e));

            if (hasDuplicates || hasUndefined) {
                // Auto-repair: update document with cleaned epoches
                Object docId = doc.get("_id");
                Query updateQuery = new Query(Criteria.where("_id").is(docId));
                Update update = new Update().set("epoches", cleaned);
                mongoTemplate.updateFirst(updateQuery, update, collection);

                if (hasDuplicates) {
                    duplicateRepairs++;
                    log.info("Epoch validate repair: removed duplicate epoches in {} doc _id={}, was={}, now={}",
                            typeName, docId, epoches, cleaned);
                }
                if (hasUndefined) {
                    undefinedRepairs++;
                    Set<Integer> removed = epoches.stream()
                            .filter(e -> !definedEpochs.contains(e))
                            .collect(Collectors.toSet());
                    log.info("Epoch validate repair: removed undefined epoches {} in {} doc _id={}, was={}, now={}",
                            removed, typeName, docId, epoches, cleaned);
                }
            }

            for (Integer epoch : epoches) {
                usedEpochs.add(epoch);
                if (!definedEpochs.contains(epoch)) {
                    invalidEpochDocs++;
                }
            }
        }

        if (duplicateRepairs > 0) {
            repairs.add("Deduplicated epoches in " + duplicateRepairs + " documents");
        }
        if (undefinedRepairs > 0) {
            repairs.add("Removed undefined epoches from " + undefinedRepairs + " documents");
        }

        // 3. Warn about defined epochs not used by any document
        Set<Integer> unusedEpochs = definedEpochs.stream()
                .filter(e -> !usedEpochs.contains(e))
                .collect(Collectors.toSet());
        if (!unusedEpochs.isEmpty()) {
            issues.add("Defined epochs not used by any document: " + unusedEpochs);
        }

        long totalDocs = mongoTemplate.count(worldQuery, collection);

        // Build result message
        StringBuilder message = new StringBuilder();
        if (!repairs.isEmpty()) {
            message.append("REPAIRED: ").append(String.join("; ", repairs)).append(". ");
        }
        if (issues.isEmpty()) {
            message.append("OK (").append(totalDocs).append(" documents, epochs ").append(usedEpochs).append(")");
        } else {
            message.append(String.join("; ", issues)).append(" (").append(totalDocs).append(" total documents)");
        }

        boolean success = issues.isEmpty();
        return new EpochProcessResult(typeName, success,
                message.toString(), System.currentTimeMillis());
    }

    /**
     * Delete an epoch by removing it from all documents.
     * Documents that would end up with an empty epoches array are reported but not deleted.
     */
    public static EpochProcessResult delete(
            MongoTemplate mongoTemplate, Class<?> entityClass, String typeName,
            String worldId, int epoch) {

        String collection = mongoTemplate.getCollectionName(entityClass);

        // $pull epoch from all documents that contain it
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("epoches").is(epoch));
        Update update = new Update().pull("epoches", epoch);

        var result = mongoTemplate.updateMulti(query, update, collection);
        long modifiedCount = result.getModifiedCount();

        // Check if any documents now have empty epoches
        Query emptyQuery = new Query(Criteria.where("worldId").is(worldId)
                .andOperator(
                        new Criteria().orOperator(
                                Criteria.where("epoches").exists(false),
                                Criteria.where("epoches").size(0)
                        )
                ));
        long emptyCount = mongoTemplate.count(emptyQuery, collection);

        log.info("Epoch delete for {}: world={}, epoch={}, updated={} documents, emptyEpoches={}",
                typeName, worldId, epoch, modifiedCount, emptyCount);

        String message = "Removed epoch " + epoch + " from " + modifiedCount + " documents";
        if (emptyCount > 0) {
            message += " (WARNING: " + emptyCount + " documents now have empty epoches)";
        }
        return new EpochProcessResult(typeName, true, message, System.currentTimeMillis());
    }

    /**
     * Create a new epoch by adding it to all documents that contain the source epoch.
     * Uses $addToSet to avoid duplicates. Documents that already have newEpoch are safely skipped.
     */
    public static EpochProcessResult create(
            MongoTemplate mongoTemplate, Class<?> entityClass, String typeName,
            String worldId, int sourceEpoch, int newEpoch) {

        String collection = mongoTemplate.getCollectionName(entityClass);

        // Use $and to avoid duplicate key in Criteria, and $addToSet to prevent duplicate epoch values
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("worldId").is(worldId),
                Criteria.where("epoches").is(sourceEpoch)
        ));
        Update update = new Update().addToSet("epoches", newEpoch);

        var result = mongoTemplate.updateMulti(query, update, collection);
        long modifiedCount = result.getModifiedCount();

        log.info("Epoch create for {}: world={}, sourceEpoch={}, newEpoch={}, updated={} documents",
                typeName, worldId, sourceEpoch, newEpoch, modifiedCount);

        return new EpochProcessResult(typeName, true,
                "Added epoch " + newEpoch + " to " + modifiedCount + " documents (from epoch " + sourceEpoch + ")",
                System.currentTimeMillis());
    }
}
