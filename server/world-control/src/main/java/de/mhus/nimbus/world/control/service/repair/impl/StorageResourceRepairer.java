package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.storage.StorageDataRepository;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.SAssetService;
import de.mhus.nimbus.world.shared.world.StorageProvider;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Repair implementation for storage.
 * Finds and removes:
 * - Orphaned storage (no entity references)
 * - Non-final storage entries (incomplete uploads)
 *
 * Only processes storage entries older than 2 hours to avoid conflicts with ongoing uploads.
 * Uses services with data ownership (SAssetService, WChunkService, WLayerService) to get referenced storageIds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageResourceRepairer implements ResourceRepairer {

    private static final String STORAGE_COLLECTION = "storage_data";
    private static final long MIN_AGE_HOURS = 2;

    private final MongoTemplate mongoTemplate;
    private final StorageService storageService;
    private final StorageDataRepository storageDataRepository;

    // Services with data ownership
    private final List<StorageProvider> storageProviders;

    @Override
    public String name() {
        return "storage";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        log.info("Starting storage repair for world {}", worldId);

        int orphanedStorageFound = 0;
        int orphanedStorageRemoved = 0;
        int nonFinalStorageFound = 0;
        int nonFinalStorageRemoved = 0;

        // Calculate minimum age timestamp (2 hours ago)
        Date minAgeDate = Date.from(Instant.now().minus(MIN_AGE_HOURS, ChronoUnit.HOURS));
        log.info("Processing storage entries older than: {}", minAgeDate);

        // 1. Find orphaned storage (no entity references)
        log.info("Checking for orphaned storage entries...");

        Set<String> orphanedStorageIds = findOrphanedStorageIds(worldId, minAgeDate);
        orphanedStorageFound = orphanedStorageIds.size();

        if (orphanedStorageFound > 0) {
            log.warn("Found {} orphaned storage entries", orphanedStorageFound);

            for (String storageId : orphanedStorageIds) {
                try {
                    storageService.delete(storageId);
                    orphanedStorageRemoved++;
                    log.debug("Deleted orphaned storage: {}", storageId);
                } catch (Exception e) {
                    log.warn("Failed to delete orphaned storage {}: {}", storageId, e.getMessage());
                }
            }
        }

        // 2. Find non-final storage (incomplete uploads)
        log.info("Checking for non-final storage entries...");

        Set<String> nonFinalStorageIds = findNonFinalStorageIds(worldId, minAgeDate);
        nonFinalStorageFound = nonFinalStorageIds.size();

        if (nonFinalStorageFound > 0) {
            log.warn("Found {} non-final storage entries", nonFinalStorageFound);

            for (String storageId : nonFinalStorageIds) {
                try {
                    // Delete all chunks for this storageId
                    storageDataRepository.deleteByUuid(storageId);
                    nonFinalStorageRemoved++;
                    log.debug("Deleted non-final storage: {}", storageId);
                } catch (Exception e) {
                    log.warn("Failed to delete non-final storage {}: {}", storageId, e.getMessage());
                }
            }
        }

        log.info("Storage repair completed: {} orphaned found, {} removed; {} non-final found, {} removed",
                orphanedStorageFound, orphanedStorageRemoved,
                nonFinalStorageFound, nonFinalStorageRemoved);

        return new ResourceRepairService.ProcessResult(
                name(),
                true,
                String.format("Orphaned storage found: %d, removed: %d; Non-final storage found: %d, removed: %d",
                        orphanedStorageFound, orphanedStorageRemoved,
                        nonFinalStorageFound, nonFinalStorageRemoved
                ),
                System.currentTimeMillis()
        );
    }

    /**
     * Find orphaned storage IDs (no entity references).
     * Returns storage IDs that exist in storage_data but are not referenced by any entity.
     * Uses services with data ownership to get referenced storageIds.
     */
    private Set<String> findOrphanedStorageIds(WorldId worldId, Date minAgeDate) {
        // Get all final storage IDs for this world (older than 2 hours)
        Query storageQuery = new Query(
                Criteria.where("worldId").is(worldId.getId())
                        .and("isFinal").is(true)
                        .and("createdAt").lt(minAgeDate)
        );

        List<String> allStorageIds = mongoTemplate.findDistinct(
                storageQuery,
                "uuid",
                STORAGE_COLLECTION,
                String.class
        );

        log.debug("Found {} final storage entries older than {} for world {}",
                allStorageIds.size(), minAgeDate, worldId);

        // Get all referenced storage IDs from entities using services
        Set<String> referencedStorageIds = new HashSet<>();

        for (StorageProvider provider : storageProviders) {
            try {
                List<String> providerStorageIds = provider.findDistinctStorageIds(worldId);
                referencedStorageIds.addAll(providerStorageIds);
                log.debug("Found {} storageIds referenced by provider {}", providerStorageIds.size(), provider.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("Failed to get storageIds from {}: {}", provider.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Remove nulls
        referencedStorageIds.remove(null);

        log.debug("Found {} total unique storage IDs referenced by entities", referencedStorageIds.size());

        // Find orphaned IDs (in storage but not referenced)
        Set<String> orphanedIds = new HashSet<>(allStorageIds);
        orphanedIds.removeAll(referencedStorageIds);

        return orphanedIds;
    }

    /**
     * Find non-final storage IDs (incomplete uploads).
     * Returns storage IDs that have chunks but no final chunk marked with isFinal=true.
     */
    private Set<String> findNonFinalStorageIds(WorldId worldId, Date minAgeDate) {
        // Get all storage IDs for this world (older than 2 hours)
        Query allStorageQuery = new Query(
                Criteria.where("worldId").is(worldId.getId())
                        .and("createdAt").lt(minAgeDate)
        );

        List<String> allStorageIds = mongoTemplate.findDistinct(
                allStorageQuery,
                "uuid",
                STORAGE_COLLECTION,
                String.class
        );

        log.debug("Found {} total storage entries older than {} for world {}",
                allStorageIds.size(), minAgeDate, worldId);

        // Get all final storage IDs
        Query finalStorageQuery = new Query(
                Criteria.where("worldId").is(worldId.getId())
                        .and("isFinal").is(true)
                        .and("createdAt").lt(minAgeDate)
        );

        Set<String> finalStorageIds = new HashSet<>(mongoTemplate.findDistinct(
                finalStorageQuery,
                "uuid",
                STORAGE_COLLECTION,
                String.class
        ));

        log.debug("Found {} final storage entries for world {}", finalStorageIds.size(), worldId);

        // Find non-final IDs (have chunks but no final marker)
        Set<String> nonFinalIds = new HashSet<>(allStorageIds);
        nonFinalIds.removeAll(finalStorageIds);

        return nonFinalIds;
    }
}
