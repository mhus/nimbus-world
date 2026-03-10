package de.mhus.nimbus.world.control.service.epoch;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator service for epoch operations on world resources.
 * Coordinates ResourceEpochType implementations for validation and creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceEpochService {

    private final List<ResourceEpochType> epochTypes;
    private final WWorldService worldService;

    /**
     * Validate epoch consistency for all resource types in a world.
     *
     * @param worldId Base world ID
     * @return List of validation results per resource type
     */
    public List<ProcessResult> validate(String worldId) {
        List<WEpochMeta> epochMetas = loadEpochMetas(worldId);
        if (epochMetas.isEmpty()) {
            return List.of(new ProcessResult("epoch-check", false,
                    "No epochs defined in WWorld", System.currentTimeMillis()));
        }

        log.info("Validating epoch consistency for world {} with {} epochs and {} resource types",
                worldId, epochMetas.size(), epochTypes.size());

        List<ProcessResult> results = new ArrayList<>();
        for (ResourceEpochType type : epochTypes) {
            try {
                results.add(type.validate(worldId, epochMetas));
            } catch (Exception e) {
                log.error("Failed to validate epoch for type {}", type.name(), e);
                results.add(new ProcessResult(type.name(), false,
                        "Validation failed: " + e.getMessage(), System.currentTimeMillis()));
            }
        }
        return results;
    }

    /**
     * Create a new epoch by propagating data from the source epoch.
     * Adds newEpoch to all documents that currently contain sourceEpoch.
     *
     * @param worldId     Base world ID
     * @param sourceEpoch Epoch to copy from
     * @param newEpoch    New epoch number
     * @return List of results per resource type
     */
    public List<ProcessResult> create(String worldId, int sourceEpoch, int newEpoch) {
        List<WEpochMeta> epochMetas = loadEpochMetas(worldId);

        // Verify source epoch exists
        boolean sourceExists = epochMetas.stream().anyMatch(e -> e.getEpoch() == sourceEpoch);
        if (!sourceExists) {
            return List.of(new ProcessResult("epoch-check", false,
                    "Source epoch " + sourceEpoch + " not defined in WWorld", System.currentTimeMillis()));
        }

        // Verify new epoch is already defined in WWorld.epoches (should be added first)
        boolean newExists = epochMetas.stream().anyMatch(e -> e.getEpoch() == newEpoch);
        if (!newExists) {
            return List.of(new ProcessResult("epoch-check", false,
                    "New epoch " + newEpoch + " not defined in WWorld.epoches. Add it first via world editor.",
                    System.currentTimeMillis()));
        }

        log.info("Creating epoch {} from source epoch {} for world {} across {} resource types",
                newEpoch, sourceEpoch, worldId, epochTypes.size());

        List<ProcessResult> results = new ArrayList<>();
        for (ResourceEpochType type : epochTypes) {
            try {
                results.add(type.create(worldId, sourceEpoch, newEpoch));
            } catch (Exception e) {
                log.error("Failed to create epoch for type {}", type.name(), e);
                results.add(new ProcessResult(type.name(), false,
                        "Create failed: " + e.getMessage(), System.currentTimeMillis()));
            }
        }
        return results;
    }

    /**
     * Delete an epoch by removing it from all resource documents.
     *
     * @param worldId Base world ID
     * @param epoch   Epoch number to remove
     * @return List of results per resource type
     */
    public List<ProcessResult> delete(String worldId, int epoch) {
        log.info("Deleting epoch {} for world {} across {} resource types",
                epoch, worldId, epochTypes.size());

        List<ProcessResult> results = new ArrayList<>();
        for (ResourceEpochType type : epochTypes) {
            try {
                results.add(type.delete(worldId, epoch));
            } catch (Exception e) {
                log.error("Failed to delete epoch for type {}", type.name(), e);
                results.add(new ProcessResult(type.name(), false,
                        "Delete failed: " + e.getMessage(), System.currentTimeMillis()));
            }
        }
        return results;
    }

    private List<WEpochMeta> loadEpochMetas(String worldId) {
        WorldId wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId));
        WWorld world = worldService.getByWorldId(wid.toBaseWorldId().getId()).orElse(null);
        if (world == null || world.getEpoches() == null) {
            return List.of();
        }
        return world.getEpoches();
    }

    public record ProcessResult(
            String typeName,
            boolean success,
            String message,
            long timestamp
    ) {}
}
