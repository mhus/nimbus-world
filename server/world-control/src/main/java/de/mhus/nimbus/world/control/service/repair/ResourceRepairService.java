package de.mhus.nimbus.world.control.service.repair;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
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

    private final List<ResourceRepairer> repairTypes;
    private final List<DeleteWorldResources> deleteServices;

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

    public List<ProcessResult> deleteWorldResources(String worldId) {
        List<ProcessResult> processed = new ArrayList<>();
        for (DeleteWorldResources service : deleteServices) {
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

    public record ProcessResult(
            String serviceName,
            boolean success,
            String message,
            long timestamp
    ) {
    };
}
