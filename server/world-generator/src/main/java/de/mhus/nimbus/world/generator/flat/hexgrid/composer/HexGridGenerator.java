package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates WHexGrid database entities from FeatureHexGrid configurations.
 * Orchestrates conversion from feature model (configuration) to runtime entities.
 *
 * Key responsibilities:
 * - Process features with status=COMPOSED
 * - Create WHexGrid instances from FeatureHexGrid configs
 * - Handle idempotent creation (skip existing grids)
 * - Update feature status to CREATED after successful
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HexGridGenerator {

    private final WHexGridRepository hexGridRepository;

    @lombok.Data
    @lombok.Builder
    public static class GenerationResult {
        private int totalFeatures;
        private int processedFeatures;
        private int createdGrids;
        private int skippedGrids;
        private boolean success;
        private String errorMessage;
        private List<String> errors;
    }

    /**
     * Generates WHexGrids for all features in composition that need creation.
     * Only processes features with status=COMPOSED and enabled=true.
     * Updates feature status to CREATED after successful generation.
     *
     * @param composition The HexComposition containing features
     * @return Generation result with statistics
     */
    public GenerationResult generateHexGrids(HexComposition composition) {
        log.info("Starting HexGrid generation for composition: {}", composition.getName());

        GenerationResult.GenerationResultBuilder resultBuilder = GenerationResult.builder();
        List<String> errors = new ArrayList<>();
        int totalFeatures = 0;
        int processedFeatures = 0;
        int createdGrids = 0;
        int skippedGrids = 0;

        try {
            List<Feature> featuresToProcess = findFeaturesToProcess(composition);
            totalFeatures = featuresToProcess.size();

            log.info("Found {} features to process", totalFeatures);

            for (Feature feature : featuresToProcess) {
                try {
                    FeatureGenerationResult featureResult = generateHexGridsForFeature(
                        feature, composition.getWorldId());

                    createdGrids += featureResult.getCreatedCount();
                    skippedGrids += featureResult.getSkippedCount();

                    if (featureResult.isSuccess()) {
                        feature.setStatus(FeatureStatus.CREATED);
                        processedFeatures++;
                    } else {
                        errors.add("Feature " + feature.getName() + ": " +
                            featureResult.getErrorMessage());
                    }

                } catch (Exception e) {
                    log.error("Failed to generate HexGrids for feature: {}",
                        feature.getName(), e);
                    errors.add("Feature " + feature.getName() + ": " + e.getMessage());
                }
            }

            composition.touch();

            log.info("HexGrid generation complete: created={}, skipped={}, processed={}/{}",
                createdGrids, skippedGrids, processedFeatures, totalFeatures);

            return resultBuilder
                .totalFeatures(totalFeatures)
                .processedFeatures(processedFeatures)
                .createdGrids(createdGrids)
                .skippedGrids(skippedGrids)
                .success(errors.isEmpty())
                .errors(errors)
                .build();

        } catch (Exception e) {
            log.error("HexGrid generation failed", e);
            return resultBuilder
                .totalFeatures(totalFeatures)
                .processedFeatures(processedFeatures)
                .createdGrids(createdGrids)
                .skippedGrids(skippedGrids)
                .success(false)
                .errorMessage(e.getMessage())
                .errors(errors)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class FeatureGenerationResult {
        private int createdCount;
        private int skippedCount;
        private boolean success;
        private String errorMessage;
    }

    /**
     * Generates WHexGrids for a single feature.
     * Idempotent: skips grids that already exist in database.
     *
     * @param feature The feature containing FeatureHexGrid configurations
     * @param worldId The world ID for the grids
     * @return Result with created and skipped counts
     */
    private FeatureGenerationResult generateHexGridsForFeature(Feature feature, String worldId) {
        log.debug("Generating HexGrids for feature: {} (status={})",
            feature.getName(), feature.getStatus());

        int createdCount = 0;
        int skippedCount = 0;
        List<WHexGrid> gridsToCreate = new ArrayList<>();

        try {
            List<FeatureHexGrid> hexGridConfigs = feature.getHexGrids();
            if (hexGridConfigs == null || hexGridConfigs.isEmpty()) {
                log.warn("Feature {} has no HexGrid configurations", feature.getName());
                return FeatureGenerationResult.builder()
                    .createdCount(0)
                    .skippedCount(0)
                    .success(true)
                    .build();
            }

            for (FeatureHexGrid config : hexGridConfigs) {
                String positionKey = config.getPositionKey();
                Optional<WHexGrid> existing = hexGridRepository
                    .findByWorldIdAndPosition(worldId, positionKey);

                if (existing.isPresent()) {
                    log.debug("HexGrid already exists at {}, skipping", positionKey);
                    skippedCount++;
                    continue;
                }

                WHexGrid hexGrid = createWHexGridFromConfig(config, feature, worldId);
                gridsToCreate.add(hexGrid);
            }

            if (!gridsToCreate.isEmpty()) {
                List<WHexGrid> saved = hexGridRepository.saveAll(gridsToCreate);
                createdCount = saved.size();
                log.info("Created {} WHexGrids for feature {}", createdCount, feature.getName());
            }

            return FeatureGenerationResult.builder()
                .createdCount(createdCount)
                .skippedCount(skippedCount)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to generate HexGrids for feature: {}", feature.getName(), e);
            return FeatureGenerationResult.builder()
                .createdCount(createdCount)
                .skippedCount(skippedCount)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Creates a WHexGrid instance from FeatureHexGrid configuration.
     * Converts lightweight config object to full database entity.
     *
     * @param config The FeatureHexGrid configuration
     * @param feature The parent feature
     * @param worldId The world ID
     * @return WHexGrid ready to be persisted
     */
    private WHexGrid createWHexGridFromConfig(FeatureHexGrid config, Feature feature,
                                              String worldId) {
        HexGrid publicData = new HexGrid();
        publicData.setPosition(config.getCoordinate());
        publicData.setName(config.getName() != null ? config.getName() :
            generateDefaultName(config.getCoordinate(), feature));
        publicData.setDescription(config.getDescription() != null ? config.getDescription() :
            generateDefaultDescription(feature));

        Map<String, String> parameters = new HashMap<>();
        if (config.getParameters() != null) {
            parameters.putAll(config.getParameters());
        }

        // Add feature metadata
        parameters.put("featureId", feature.getFeatureId());
        parameters.put("featureName", feature.getName());

        Map<String, Map<String, String>> areas = new HashMap<>();
        if (config.getAreas() != null) {
            areas.putAll(config.getAreas());
        }

        WHexGrid hexGrid = WHexGrid.builder()
            .worldId(worldId)
            .position(config.getPositionKey())
            .publicData(publicData)
            .parameters(parameters)
            .areas(areas)
            .enabled(true)
            .build();

        hexGrid.touchCreate();
        hexGrid.syncPositionKey();

        // Apply flow segments if present AND not already configured
        // HexGridRoadConfigurator sets road/river/wall parameters, so only fallback if missing
        if (config.hasFlowSegments()) {
            boolean hasRoadParam = parameters.containsKey("g_road");
            boolean hasRiverParam = parameters.containsKey("g_river");
            boolean hasWallParam = parameters.containsKey("g_wall");

            // Only apply if not already configured by HexGridRoadConfigurator
            if (!hasRoadParam && !hasRiverParam && !hasWallParam) {
                applyFlowSegments(hexGrid, config);
            }
        }

        return hexGrid;
    }

    /**
     * Finds all features that need HexGrid creation.
     * Recursively searches features and composites.
     *
     * @param composition The composition to search
     * @return List of features with status=COMPOSED and enabled=true
     */
    private List<Feature> findFeaturesToProcess(HexComposition composition) {
        List<Feature> toProcess = new ArrayList<>();

        if (composition.getFeatures() == null) {
            return toProcess;
        }

        // Direct features
        for (Feature feature : composition.getFeatures()) {
            if (feature.needsHexGridCreation()) {
                toProcess.add(feature);
            }
        }

        // Nested features in composites
        for (Composite composite : composition.getComposites()) {
            for (Feature nestedFeature : composite.getFeatures()) {
                if (nestedFeature.needsHexGridCreation()) {
                    toProcess.add(nestedFeature);
                }
            }
        }

        return toProcess;
    }

    /**
     * Generates default name for a HexGrid
     */
    private String generateDefaultName(HexVector2 coord, Feature feature) {
        return feature.getName() + " [" + coord.getQ() + "," + coord.getR() + "]";
    }

    /**
     * Generates default description for a HexGrid
     */
    private String generateDefaultDescription(Feature feature) {
        return "Generated from feature: " + feature.getDisplayTitle();
    }

    /**
     * Checks if a feature can be processed by this generator
     */
    public boolean canGenerate(Feature feature) {
        if (feature == null) return false;
        if (!feature.needsHexGridCreation()) return false;
        if (feature.getHexGrids() == null || feature.getHexGrids().isEmpty()) return false;
        return true;
    }

    /**
     * Applies flow segments to a WHexGrid by converting them to parameter JSON.
     * Supports roads, rivers, and walls.
     *
     * @param hexGrid The WHexGrid to apply flow segments to
     * @param config The FeatureHexGrid configuration with flow segments
     */
    private void applyFlowSegments(WHexGrid hexGrid, FeatureHexGrid config) {
        List<FlowSegment> roadSegments = config.getFlowSegmentsByType(FlowType.ROAD);
        List<FlowSegment> riverSegments = config.getFlowSegmentsByType(FlowType.RIVER);
        List<FlowSegment> wallSegments = config.getFlowSegmentsByType(FlowType.WALL);

        // Apply roads
        if (!roadSegments.isEmpty()) {
            applyRoadSegments(hexGrid, roadSegments);
        }

        // Apply rivers
        if (!riverSegments.isEmpty()) {
            applyRiverSegments(hexGrid, riverSegments);
        }

        // Apply walls
        if (!wallSegments.isEmpty()) {
            applyWallSegments(hexGrid, wallSegments);
        }
    }

    /**
     * Applies road segments to WHexGrid parameters
     */
    private void applyRoadSegments(WHexGrid hexGrid, List<FlowSegment> roadSegments) {
        Map<String, String> params = hexGrid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            hexGrid.setParameters(params);
        }

        // Build road configuration JSON
        StringBuilder json = new StringBuilder("{");
        json.append("\"lx\":256,\"lz\":256,"); // Default center point
        json.append("\"level\":").append(roadSegments.get(0).getLevel() != null ? roadSegments.get(0).getLevel() : 95).append(",");
        json.append("\"route\":[");

        boolean first = true;
        for (FlowSegment segment : roadSegments) {
            if (!first) json.append(",");
            first = false;

            json.append("{");
            if (segment.getFromSide() != null) {
                json.append("\"side\":\"").append(segment.getFromSide().name()).append("\",");
            }
            if (segment.getToSide() != null) {
                json.append("\"toSide\":\"").append(segment.getToSide().name()).append("\",");
            }
            json.append("\"width\":").append(segment.getWidth() != null ? segment.getWidth() : 4).append(",");
            json.append("\"level\":").append(segment.getLevel() != null ? segment.getLevel() : 95);
            if (segment.getType() != null) {
                json.append(",\"type\":\"").append(segment.getType()).append("\"");
            }
            json.append("}");
        }

        json.append("]}");
        params.put("g_road", json.toString());

        log.debug("Applied {} road segments to grid {}", roadSegments.size(), hexGrid.getPosition());
    }

    /**
     * Applies river segments to WHexGrid parameters
     */
    private void applyRiverSegments(WHexGrid hexGrid, List<FlowSegment> riverSegments) {
        Map<String, String> params = hexGrid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            hexGrid.setParameters(params);
        }

        // Build river configuration JSON
        StringBuilder json = new StringBuilder("{");
        json.append("\"from\":[");

        boolean first = true;
        for (FlowSegment segment : riverSegments) {
            if (segment.getFromSide() != null) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"side\":\"").append(segment.getFromSide().name()).append("\",");
                json.append("\"width\":").append(segment.getWidth() != null ? segment.getWidth() : 6).append(",");
                json.append("\"depth\":").append(segment.getDepth() != null ? segment.getDepth() : 3).append(",");
                json.append("\"level\":").append(segment.getLevel() != null ? segment.getLevel() : 50);
                json.append("}");
            }
        }

        json.append("],\"to\":[");

        first = true;
        for (FlowSegment segment : riverSegments) {
            if (segment.getToSide() != null) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"side\":\"").append(segment.getToSide().name()).append("\",");
                json.append("\"width\":").append(segment.getWidth() != null ? segment.getWidth() : 6).append(",");
                json.append("\"depth\":").append(segment.getDepth() != null ? segment.getDepth() : 3).append(",");
                json.append("\"level\":").append(segment.getLevel() != null ? segment.getLevel() : 50);
                json.append("}");
            }
        }

        json.append("]}");
        params.put("g_river", json.toString());

        log.debug("Applied {} river segments to grid {}", riverSegments.size(), hexGrid.getPosition());
    }

    /**
     * Applies wall segments to WHexGrid parameters
     */
    private void applyWallSegments(WHexGrid hexGrid, List<FlowSegment> wallSegments) {
        Map<String, String> params = hexGrid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            hexGrid.setParameters(params);
        }

        // Build wall configuration JSON
        // TODO: Define proper wall parameter format
        StringBuilder json = new StringBuilder("{");
        json.append("\"segments\":[");

        boolean first = true;
        for (FlowSegment segment : wallSegments) {
            if (!first) json.append(",");
            first = false;

            json.append("{");
            if (segment.getFromSide() != null) {
                json.append("\"fromSide\":\"").append(segment.getFromSide().name()).append("\",");
            }
            if (segment.getToSide() != null) {
                json.append("\"toSide\":\"").append(segment.getToSide().name()).append("\",");
            }
            json.append("\"height\":").append(segment.getHeight() != null ? segment.getHeight() : 10);
            if (segment.getMaterial() != null) {
                json.append(",\"material\":\"").append(segment.getMaterial()).append("\"");
            }
            json.append("}");
        }

        json.append("]}");
        params.put("g_wall", json.toString());

        log.debug("Applied {} wall segments to grid {}", wallSegments.size(), hexGrid.getPosition());
    }
}
