package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Repair implementation for assets.
 * Finds and removes duplicate entries (e.g., with and without _schema field).
 * Ensures uniqueness by worldId + path combination.
 * Removes orphaned storage references.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetResourceRepairType implements ResourceRepairType {

    private static final String COLLECTION_NAME = "s_assets";

    private final MongoTemplate mongoTemplate;
    private final StorageService storageService;

    @Override
    public String name() {
        return "asset";
    }

    @Override
    public RepairResult repair(WorldId worldId, boolean dryRun) {
        log.info("Starting asset repair for world {} (dryRun={})", worldId, dryRun);

        int duplicatesFound = 0;
        int duplicatesRemoved = 0;
        int orphanedStorageFound = 0;
        int orphanedStorageRemoved = 0;

        // Find all assets for this world
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        log.info("Found {} total asset documents for world {}", documents.size(), worldId);

        // Group by worldId + path to find duplicates
        Map<String, List<Document>> groupedByPath = documents.stream()
                .filter(doc -> doc.getString("path") != null)
                .collect(Collectors.groupingBy(doc -> {
                    String wId = doc.getString("worldId");
                    String path = doc.getString("path");
                    return wId + "|" + path;
                }));

        // Find and handle duplicates
        for (Map.Entry<String, List<Document>> entry : groupedByPath.entrySet()) {
            List<Document> duplicates = entry.getValue();

            if (duplicates.size() > 1) {
                duplicatesFound += duplicates.size() - 1;
                log.warn("Found {} duplicates for key: {}", duplicates.size(), entry.getKey());

                // Keep the document with _schema field (most recent), or highest createdAt
                Document toKeep = selectDocumentToKeep(duplicates);
                log.info("Keeping document with _id: {} (has _schema: {})",
                        toKeep.get("_id"),
                        toKeep.containsKey("_schema"));

                // Remove duplicates
                for (Document doc : duplicates) {
                    if (doc.get("_id").equals(toKeep.get("_id"))) {
                        continue; // Skip the one we want to keep
                    }

                    Object docId = doc.get("_id");
                    String storageId = doc.getString("storageId");

                    log.info("Removing duplicate document _id: {} (has _schema: {}, storageId: {})",
                            docId, doc.containsKey("_schema"), storageId);

                    if (!dryRun) {
                        // Check if storageId is used by the document we're keeping or any other document
                        boolean storageInUse = false;
                        if (storageId != null) {
                            String keptStorageId = toKeep.getString("storageId");
                            storageInUse = storageId.equals(keptStorageId);

                            // Check if any other document uses this storageId
                            if (!storageInUse) {
                                Query storageQuery = new Query(
                                        Criteria.where("storageId").is(storageId)
                                                .and("_id").ne(docId)
                                );
                                storageInUse = mongoTemplate.exists(storageQuery, COLLECTION_NAME);
                            }
                        }

                        // Delete the storage if not in use
                        if (storageId != null && !storageInUse) {
                            log.info("Deleting orphaned storage: {}", storageId);
                            try {
                                storageService.delete(storageId);
                                orphanedStorageRemoved++;
                            } catch (Exception e) {
                                log.warn("Failed to delete storage {}: {}", storageId, e.getMessage());
                            }
                        }

                        // Delete the duplicate document
                        Query deleteQuery = new Query(Criteria.where("_id").is(docId));
                        mongoTemplate.remove(deleteQuery, COLLECTION_NAME);
                        duplicatesRemoved++;
                    }
                }
            }
        }

        // Find orphaned storage IDs (storageId exists but not referenced by kept document)
        Set<String> allStorageIds = documents.stream()
                .map(doc -> doc.getString("storageId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("Checking {} unique storage IDs for orphans", allStorageIds.size());

        for (String storageId : allStorageIds) {
            Query storageQuery = new Query(Criteria.where("storageId").is(storageId));
            long count = mongoTemplate.count(storageQuery, COLLECTION_NAME);

            if (count == 0) {
                orphanedStorageFound++;
                log.warn("Found orphaned storage (no document references it): {}", storageId);

                if (!dryRun) {
                    try {
                        storageService.delete(storageId);
                        orphanedStorageRemoved++;
                        log.info("Deleted orphaned storage: {}", storageId);
                    } catch (Exception e) {
                        log.warn("Failed to delete orphaned storage {}: {}", storageId, e.getMessage());
                    }
                }
            }
        }

        log.info("Asset repair completed: {} duplicates found, {} removed, {} orphaned storage found, {} removed",
                duplicatesFound, duplicatesRemoved, orphanedStorageFound, orphanedStorageRemoved);

        return RepairResult.of(duplicatesFound, duplicatesRemoved,
                orphanedStorageFound, orphanedStorageRemoved);
    }

    /**
     * Select which document to keep when duplicates are found.
     * Priority:
     * 1. Document with _schema field (most recent schema version)
     * 2. Document with latest createdAt timestamp
     * 3. First document in list
     */
    private Document selectDocumentToKeep(List<Document> documents) {
        // Prefer document with _schema field
        Optional<Document> withSchema = documents.stream()
                .filter(doc -> doc.containsKey("_schema"))
                .findFirst();

        if (withSchema.isPresent()) {
            return withSchema.get();
        }

        // Otherwise, keep the one with the latest createdAt
        return documents.stream()
                .max(Comparator.comparing(doc -> {
                    Object createdAt = doc.get("createdAt");
                    if (createdAt == null) {
                        return "";
                    }
                    return createdAt.toString();
                }))
                .orElse(documents.get(0));
    }
}
