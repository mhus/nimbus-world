package de.mhus.nimbus.world.control.service.epoch.impl;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
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
 */
@Slf4j
public final class EpochTypeHelper {

    private EpochTypeHelper() {}

    /**
     * Validate epoch consistency for a collection.
     */
    public static ResourceEpochService.ProcessResult validate(
            MongoTemplate mongoTemplate, String collection, String typeName,
            String worldId, List<WEpochMeta> epochMetas) {

        Set<Integer> definedEpochs = epochMetas.stream()
                .map(WEpochMeta::getEpoch)
                .collect(Collectors.toSet());

        List<String> issues = new ArrayList<>();

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

        // 2. Find documents with epoch values not in WWorld.epoches
        // Get all distinct epoch values used in this collection for this world
        Query worldQuery = new Query(Criteria.where("worldId").is(worldId));
        List<Document> docs = mongoTemplate.find(worldQuery, Document.class, collection);

        Set<Integer> usedEpochs = new HashSet<>();
        int invalidEpochDocs = 0;
        for (Document doc : docs) {
            List<Integer> epoches = doc.getList("epoches", Integer.class);
            if (epoches == null || epoches.isEmpty()) continue;
            for (Integer epoch : epoches) {
                usedEpochs.add(epoch);
                if (!definedEpochs.contains(epoch)) {
                    invalidEpochDocs++;
                }
            }
        }

        if (invalidEpochDocs > 0) {
            Set<Integer> undefinedEpochs = usedEpochs.stream()
                    .filter(e -> !definedEpochs.contains(e))
                    .collect(Collectors.toSet());
            issues.add(invalidEpochDocs + " documents reference undefined epochs: " + undefinedEpochs);
        }

        // 3. Warn about defined epochs not used by any document
        Set<Integer> unusedEpochs = definedEpochs.stream()
                .filter(e -> !usedEpochs.contains(e))
                .collect(Collectors.toSet());
        if (!unusedEpochs.isEmpty()) {
            issues.add("Defined epochs not used by any document: " + unusedEpochs);
        }

        long totalDocs = mongoTemplate.count(worldQuery, collection);

        if (issues.isEmpty()) {
            return new ResourceEpochService.ProcessResult(typeName, true,
                    "OK (" + totalDocs + " documents, epochs " + usedEpochs + ")",
                    System.currentTimeMillis());
        } else {
            return new ResourceEpochService.ProcessResult(typeName, false,
                    String.join("; ", issues) + " (" + totalDocs + " total documents)",
                    System.currentTimeMillis());
        }
    }

    /**
     * Delete an epoch by removing it from all documents.
     * Documents that would end up with an empty epoches array are reported but not deleted.
     */
    public static ResourceEpochService.ProcessResult delete(
            MongoTemplate mongoTemplate, String collection, String typeName,
            String worldId, int epoch) {

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
        return new ResourceEpochService.ProcessResult(typeName, true, message, System.currentTimeMillis());
    }

    /**
     * Create a new epoch by adding it to all documents that contain the source epoch.
     */
    public static ResourceEpochService.ProcessResult create(
            MongoTemplate mongoTemplate, String collection, String typeName,
            String worldId, int sourceEpoch, int newEpoch) {

        // $push newEpoch to all documents that contain sourceEpoch but not newEpoch
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("epoches").is(sourceEpoch)
                .and("epoches").nin(newEpoch));
        Update update = new Update().push("epoches", newEpoch);

        var result = mongoTemplate.updateMulti(query, update, collection);
        long modifiedCount = result.getModifiedCount();

        log.info("Epoch create for {}: world={}, sourceEpoch={}, newEpoch={}, updated={} documents",
                typeName, worldId, sourceEpoch, newEpoch, modifiedCount);

        return new ResourceEpochService.ProcessResult(typeName, true,
                "Added epoch " + newEpoch + " to " + modifiedCount + " documents (from epoch " + sourceEpoch + ")",
                System.currentTimeMillis());
    }
}
