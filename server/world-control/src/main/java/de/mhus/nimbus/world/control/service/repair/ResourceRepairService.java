package de.mhus.nimbus.world.control.service.repair;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator service for resource repair operations.
 * Coordinates ResourceRepairType implementations to fix database issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceRepairService {

    private static final List<String> ALL_TYPES = Arrays.asList(
            "asset", "backdrop", "blocktype", "item", "itemtype", "itemposition",
            "entity", "entitymodel", "model", "ground"
    );

    private final List<ResourceRepairType> repairTypes;

    /**
     * Repair resources for given world.
     *
     * @param worldId World to repair
     * @param types   Resource types to repair (empty = all types)
     * @param dryRun  If true, only report issues without fixing them
     * @return Repair result
     */
    public RepairResult repair(WorldId worldId, List<String> types, boolean dryRun) {
        log.info("Starting resource repair for world {} dryRun={} types={}",
                worldId, dryRun, types == null || types.isEmpty() ? "all" : types);

        // Resolve types (empty list = all types)
        List<String> typesToRepair = types == null || types.isEmpty()
                ? ALL_TYPES
                : types;

        // Repair each type
        int totalDuplicatesFound = 0;
        int totalDuplicatesRemoved = 0;
        int totalOrphanedStorageFound = 0;
        int totalOrphanedStorageRemoved = 0;
        int totalIssuesFound = 0;
        int totalIssuesFixed = 0;
        Map<String, ResourceRepairType.RepairResult> resultsByType = new HashMap<>();

        for (String typeName : typesToRepair) {
            ResourceRepairType repairType = findRepairType(typeName);
            if (repairType == null) {
                log.warn("No repair type found for: {}", typeName);
                continue;
            }

            try {
                log.info("Repairing type: {}", typeName);
                ResourceRepairType.RepairResult typeResult = repairType.repair(worldId, dryRun);
                resultsByType.put(typeName, typeResult);

                totalDuplicatesFound += typeResult.duplicatesFound();
                totalDuplicatesRemoved += typeResult.duplicatesRemoved();
                totalOrphanedStorageFound += typeResult.orphanedStorageFound();
                totalOrphanedStorageRemoved += typeResult.orphanedStorageRemoved();
                totalIssuesFound += typeResult.totalIssuesFound();
                totalIssuesFixed += typeResult.totalIssuesFixed();

                log.info("Repaired {} type: {} issues found, {} fixed",
                        typeName, typeResult.totalIssuesFound(), typeResult.totalIssuesFixed());

            } catch (Exception e) {
                log.error("Failed to repair type: " + typeName, e);
                return RepairResult.failure("Failed to repair " + typeName + ": " + e.getMessage());
            }
        }

        log.info("Repair completed: {} issues found, {} fixed (dryRun={})",
                totalIssuesFound, totalIssuesFixed, dryRun);

        return RepairResult.success(
                totalDuplicatesFound, totalDuplicatesRemoved,
                totalOrphanedStorageFound, totalOrphanedStorageRemoved,
                totalIssuesFound, totalIssuesFixed,
                resultsByType
        );
    }

    /**
     * Find repair type implementation by name.
     */
    private ResourceRepairType findRepairType(String name) {
        return repairTypes.stream()
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Result of repair operation.
     */
    public record RepairResult(
            boolean success,
            int duplicatesFound,
            int duplicatesRemoved,
            int orphanedStorageFound,
            int orphanedStorageRemoved,
            int totalIssuesFound,
            int totalIssuesFixed,
            Map<String, ResourceRepairType.RepairResult> resultsByType,
            String errorMessage,
            Instant timestamp
    ) {
        public static RepairResult success(
                int duplicatesFound, int duplicatesRemoved,
                int orphanedStorageFound, int orphanedStorageRemoved,
                int totalIssuesFound, int totalIssuesFixed,
                Map<String, ResourceRepairType.RepairResult> resultsByType) {
            return new RepairResult(
                    true,
                    duplicatesFound, duplicatesRemoved,
                    orphanedStorageFound, orphanedStorageRemoved,
                    totalIssuesFound, totalIssuesFixed,
                    resultsByType,
                    null,
                    Instant.now()
            );
        }

        public static RepairResult failure(String errorMessage) {
            return new RepairResult(
                    false,
                    0, 0, 0, 0, 0, 0,
                    Map.of(),
                    errorMessage,
                    Instant.now()
            );
        }
    }
}
