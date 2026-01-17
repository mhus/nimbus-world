package de.mhus.nimbus.world.control.service.repair;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator service for resource repair operations.
 * Coordinates ResourceRepairType implementations to fix database issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceRepairService {

    private final List<ResourceRepairer> repairTypes;
    private final List<DeleteWorldResources> deleteServices;
    private final WWorldService worldService;

    /**
     * Repair resources for given world.
     *
     * @param worldId World to repair
     * @param types   Resource types to repair (empty = all types)
     * @return Repair result
     */
    public List<ProcessResult> repair(WorldId worldId, List<String> types) {

        // Resolve types (empty list = all types)
        List<String> typesToRepair = types == null || types.isEmpty()
                ? repairTypes.stream().map(ResourceRepairer::name).toList()
                : types;

        log.info("Starting resource repair for world {} types={}",
                worldId, typesToRepair);

        // Repair each type
        List<ProcessResult> results = new ArrayList<>();

        for (String typeName : typesToRepair) {
            ResourceRepairer repairType = findRepairType(typeName);
            if (repairType == null) {
                log.warn("No repair type found for: {}", typeName);
                continue;
            }

            try {
                log.info("Repairing type: {}", typeName);
                var typeResult = repairType.repair(worldId);
                results.add(typeResult);

            } catch (Exception e) {
                log.error("Failed to repair type: " + typeName, e);
                results.add(new ProcessResult(
                        typeName,
                        false,
                        "Repair failed: " + e.getMessage(),
                        Instant.now().toEpochMilli()
                ));
            }
        }

        return results;
    }

    /**
     * Find repair type implementation by title.
     */
    private ResourceRepairer findRepairType(String name) {
        return repairTypes.stream()
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Delete world resources (all types).
     *
     * @param worldId World ID whose resources should be deleted
     * @return List of processing results
     */
    public List<ProcessResult> deleteWorldResources(String worldId) {
        return deleteWorldResources(worldId, null);
    }

    /**
     * Delete world resources (specific types or all).
     *
     * @param worldId World ID whose resources should be deleted
     * @param types   List of resource types to delete (null or empty = all types)
     * @return List of processing results
     */
    public List<ProcessResult> deleteWorldResources(String worldId, List<String> types) {
        List<ProcessResult> processed = new ArrayList<>();

        // Determine which services to execute
        List<DeleteWorldResources> servicesToExecute;
        if (types == null || types.isEmpty()) {
            servicesToExecute = deleteServices;
            log.info("Deleting all resource types for worldId={}", worldId);
        } else {
            servicesToExecute = deleteServices.stream()
                    .filter(service -> types.contains(service.name()))
                    .toList();
            log.info("Deleting specific resource types for worldId={}: {}", worldId, types);
        }

        for (DeleteWorldResources service : servicesToExecute) {
            log.info("Executing deletion service: {}", service.name());

            try {
                service.deleteWorldResources(worldId);
                processed.add(new ProcessResult(
                        service.name(),
                        true,
                        "Deleted resources successfully",
                        System.currentTimeMillis()
                ));

            } catch (Exception e) {
                String errorMsg = String.format("Failed to delete %s: %s",
                        service.name(), e.getMessage());
                log.error(errorMsg, e);
                processed.add(new ProcessResult(
                        service.name(),
                        false,
                        errorMsg,
                        System.currentTimeMillis()
                ));

                // Continue with other services even if one fails
                // This allows partial cleanup and better error reporting
            }
        }
        return processed;
    }

    /**
     * Delete orphaned world resources.
     * Finds all worldIds referenced in resources, checks if the worlds still exist,
     * and deletes resources for non-existent worlds.
     *
     * @return List of processing results per service and worldId
     */
    public List<ProcessResult> deleteOrphanedWorldResources() {
        log.info("Starting orphaned world resources cleanup");
        List<ProcessResult> results = new ArrayList<>();

        try {
            // 1. Collect all known worldIds from all delete services
            Set<String> allKnownWorldIds = new HashSet<>();

            for (DeleteWorldResources service : deleteServices) {
                try {
                    List<String> serviceWorldIds = service.getKnownWorldIds();
                    log.debug("Service {} reports {} worldIds", service.name(), serviceWorldIds.size());
                    allKnownWorldIds.addAll(serviceWorldIds);
                } catch (Exception e) {
                    log.warn("Failed to get worldIds from service {}: {}", service.name(), e.getMessage());
                    results.add(new ProcessResult(
                            service.name() + " (getKnownWorldIds)",
                            false,
                            "Failed to get worldIds: " + e.getMessage(),
                            System.currentTimeMillis()
                    ));
                }
            }

            log.info("Found {} distinct worldIds in resources", allKnownWorldIds.size());

            // 2. Remove instance IDs - keep only main worlds
            Set<String> mainWorldIds = allKnownWorldIds.stream()
                    .map(worldId -> {
                        var parsed = WorldId.of(worldId);
                        return parsed.map(wid -> wid.mainWorld().getId()).orElse(worldId);
                    })
                    .collect(Collectors.toSet());

            log.info("After filtering to main worlds: {} worldIds", mainWorldIds.size());

            // 3. Check which worlds still exist
            Set<String> orphanedWorldIds = new HashSet<>();

            for (String worldId : mainWorldIds) {
                try {
                    if (worldService.getByWorldId(worldId).isEmpty()) {
                        orphanedWorldIds.add(worldId);
                        log.debug("Found orphaned worldId: {}", worldId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to check existence of worldId {}: {}", worldId, e.getMessage());
                }
            }

            log.info("Found {} orphaned worldIds (worlds that no longer exist)", orphanedWorldIds.size());

            // 4. Delete resources for orphaned worlds
            if (orphanedWorldIds.isEmpty()) {
                results.add(new ProcessResult(
                        "orphaned-worlds-check",
                        true,
                        "No orphaned world resources found",
                        System.currentTimeMillis()
                ));
            } else {
                for (String orphanedWorldId : orphanedWorldIds) {
                    log.info("Deleting resources for orphaned worldId: {}", orphanedWorldId);

                    List<ProcessResult> deleteResults = deleteWorldResources(orphanedWorldId);
                    results.addAll(deleteResults);

                    // Add summary result for this world
                    long successCount = deleteResults.stream().filter(ProcessResult::success).count();
                    long totalCount = deleteResults.size();

                    results.add(new ProcessResult(
                            "orphaned-world-" + orphanedWorldId,
                            successCount == totalCount,
                            String.format("Deleted resources for orphaned world: %d/%d services succeeded",
                                    successCount, totalCount),
                            System.currentTimeMillis()
                    ));
                }
            }

            log.info("Orphaned world resources cleanup completed");

        } catch (Exception e) {
            log.error("Failed to delete orphaned world resources", e);
            results.add(new ProcessResult(
                    "orphaned-worlds-cleanup",
                    false,
                    "Cleanup failed: " + e.getMessage(),
                    System.currentTimeMillis()
            ));
        }

        return results;
    }

    public record ProcessResult(
            String serviceName,
            boolean success,
            String message,
            long timestamp
    ) {
    };
}
