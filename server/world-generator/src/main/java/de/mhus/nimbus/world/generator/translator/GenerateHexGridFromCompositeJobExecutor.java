package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static de.mhus.nimbus.world.generator.translator.TranslateInstructionJobExecutor.COMPOSED_COLLECTION;

/**
 * Job executor for generating WHexGrids from a composed model.
 *
 * Executor name: 'generator-generate-hexgrid-from-composite'
 *
 * Required parameters:
 * - documentId: Id of the document containing the enriched composition (in 'generator_composed' collection)
 *
 * Optional parameters:
 * - seed: Random seed for reproducible generation (default: random)
 * - edge_blend_width: Width of edge blending area in pixels (default: "30")
 * - edge_blend_randomness: Random variation in blending 0.0-1.0 (default: "0.6")
 * - edge_shake_strength: Pixel swapping strength for organic look 0.0-1.0 (default: "0.2")
 * - edge_blur_radius: Blur radius for smooth transitions 0-5 (default: "1")
 *
 * Output:
 * - success: List of generated WHexGrid coordinates (space-separated: "0;0 0;1 1;0")
 * - failure: Error message
 *
 * This job creates or updates all WHexGrids described in the composition.
 * WHexGrids are created in the database via WHexGridRepository.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GenerateHexGridFromCompositeJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "generator-generate-hexgrid-from-composite";

    private final WDocumentService documentService;
    private final WHexGridRepository hexGridRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting generate hexgrid job: jobId={}", job.getId());

            // Extract required parameters
            String documentId = getRequiredParameter(job, "documentId");

            // Extract optional parameters
            Long seed = getOptionalLongParameter(job, "seed", null);
            if (seed == null) {
                seed = System.currentTimeMillis();
                log.info("No seed provided, using current time: {}", seed);
            }

            // Extract optional edge blending parameters (with defaults from test)
            String edgeBlendWidth = getOptionalParameter(job, "edge_blend_width", "30");
            String edgeBlendRandomness = getOptionalParameter(job, "edge_blend_randomness", "0.6");
            String edgeShakeStrength = getOptionalParameter(job, "edge_shake_strength", "0.2");
            String edgeBlurRadius = getOptionalParameter(job, "edge_blur_radius", "1");

            log.info("Generating hexgrids: documentId={}, seed={}, edge_blend_width={}, edge_blend_randomness={}, edge_shake_strength={}, edge_blur_radius={}",
                    documentId, seed, edgeBlendWidth, edgeBlendRandomness, edgeShakeStrength, edgeBlurRadius);

            // Step 1: Load document from path
            WDocument document = loadDocumentFromPath(job.getWorldId(), documentId);
            if (document == null) {
                return JobResult.failure("Document not found: " + documentId);
            }

            // Step 2: Extract enriched composition from document
            HexComposition composition = extractCompositionFromDocument(document);
            if (composition == null) {
                return JobResult.failure("Failed to extract composition from document");
            }

            // Override worldId with the one from job context (defensive programming)
            composition.setWorldId(job.getWorldId());

            log.info("Loaded enriched composition: name='{}', worldId='{}', featureHexGrids={}",
                    composition.getName(),
                    composition.getWorldId(),
                    composition.getFeatureHexGridRegistry() != null ? composition.getFeatureHexGridRegistry().size() : 0);

            // Step 3: Create WHexGrids from FeatureHexGrids in Central Registry
            // The composition contains FeatureHexGrids in the central registry
            // We create WHexGrids from them and persist to the database

            if (composition.getFeatureHexGridRegistry() == null || composition.getFeatureHexGridRegistry().isEmpty()) {
                log.error("No FeatureHexGrids found in Central Registry. This composition was likely created with an older " +
                        "version of the generator and needs to be re-generated. Composition: name='{}', documentId='{}'",
                        composition.getName(), documentId);
                return JobResult.failure("No FeatureHexGrids found in composition - the composition needs to be " +
                        "re-generated. Please delete the instruction and create a new one.");
            }

            int createdGrids = 0;
            int updatedGrids = 0;
            Set<String> allCoordinateStrings = new HashSet<>();

            // Process all FeatureHexGrids from Central Registry
            for (var featureHexGrid : composition.getFeatureHexGridRegistry().values()) {
                HexVector2 coord = featureHexGrid.getCoordinate();
                String position = TypeUtil.toStringHexCoord(coord);
                allCoordinateStrings.add(position);

                // Create WHexGrid from FeatureHexGrid
                de.mhus.nimbus.world.shared.world.WHexGrid sourceGrid = createWHexGridFromFeatureHexGrid(
                    featureHexGrid, composition.getWorldId());

                if (sourceGrid == null || sourceGrid.getParameters() == null) {
                    log.warn("Failed to create WHexGrid from FeatureHexGrid at {}, skipping", position);
                    continue;
                }

                Map<String, String> params = sourceGrid.getParameters();

                // Check if grid already exists in database
                Optional<de.mhus.nimbus.world.shared.world.WHexGrid> existingOpt =
                    hexGridRepository.findByWorldIdAndPosition(composition.getWorldId(), position);

                de.mhus.nimbus.world.shared.world.WHexGrid wHexGrid;

                if (existingOpt.isPresent()) {
                    // Update existing grid with parameters and publicData from FilledHexGrid
                    wHexGrid = existingOpt.get();
                    wHexGrid.getPublicData().setTitle(sourceGrid.getPublicData().getTitle());
                    wHexGrid.setParameters(new HashMap<>(params));

                    // Update publicData (name, title) from sourceGrid
                    if (sourceGrid.getPublicData() != null) {
                        if (wHexGrid.getPublicData() == null) {
                            wHexGrid.setPublicData(de.mhus.nimbus.generated.types.HexGrid.builder()
                                    .position(coord)
                                    .build());
                        }

                        // Copy name and title from sourceGrid
                        if (sourceGrid.getPublicData().getName() != null) {
                            wHexGrid.getPublicData().setName(sourceGrid.getPublicData().getName());
                        }
                        if (sourceGrid.getPublicData().getTitle() != null) {
                            wHexGrid.getPublicData().setTitle(sourceGrid.getPublicData().getTitle());
                        }
                        if (sourceGrid.getPublicData().getDescription() != null) {
                            wHexGrid.getPublicData().setDescription(sourceGrid.getPublicData().getDescription());
                        }
                    }

                    wHexGrid.touchUpdate();

                    log.debug("Updated WHexGrid: {} at {} with {} parameters, name={}, title={}",
                            composition.getWorldId(), position, params.size(),
                            wHexGrid.getPublicData() != null ? wHexGrid.getPublicData().getName() : null,
                            wHexGrid.getPublicData() != null ? wHexGrid.getPublicData().getTitle() : null);
                    updatedGrids++;
                } else {
                    // Create new WHexGrid entity from FilledHexGrid
                    de.mhus.nimbus.generated.types.HexGrid publicData = de.mhus.nimbus.generated.types.HexGrid.builder()
                            .position(coord)
                            .build();

                    // Copy name, title, and description from sourceGrid
                    if (sourceGrid.getPublicData() != null) {
                        if (sourceGrid.getPublicData().getName() != null) {
                            publicData.setName(sourceGrid.getPublicData().getName());
                        }
                        if (sourceGrid.getPublicData().getTitle() != null) {
                            publicData.setTitle(sourceGrid.getPublicData().getTitle());
                        }
                        if (sourceGrid.getPublicData().getDescription() != null) {
                            publicData.setDescription(sourceGrid.getPublicData().getDescription());
                        }
                    }

                    wHexGrid = de.mhus.nimbus.world.shared.world.WHexGrid.builder()
                            .worldId(composition.getWorldId())
                            .position(position)
                            .publicData(publicData)
                            .parameters(new HashMap<>(params))
                            .build();
                    wHexGrid.touchCreate();
                    wHexGrid.syncPositionKey();

                    log.debug("Created WHexGrid: {} at {} with {} parameters, name={}, title={}",
                            composition.getWorldId(), position, params.size(),
                            publicData.getName(), publicData.getTitle());
                    createdGrids++;
                }

                // Save to repository
                hexGridRepository.save(wHexGrid);
            }

            log.info("WHexGrid creation/update complete: created={}, updated={}, total={}",
                    createdGrids, updatedGrids, composition.getFeatureHexGridRegistry().size());

            // Step 3b: Add edge_flat parameters for neighbor flats and edge blending parameters
            log.info("Adding edge_flat and edge blending parameters for {} unique grids", allCoordinateStrings.size());
            int edgeParamsAdded = 0;

            // Iterate over unique coordinates only (use Set instead of List to avoid duplicates)
            for (String position : allCoordinateStrings) {
                Optional<de.mhus.nimbus.world.shared.world.WHexGrid> gridOpt =
                    hexGridRepository.findByWorldIdAndPosition(composition.getWorldId(), position);

                if (gridOpt.isEmpty()) {
                    continue;
                }

                de.mhus.nimbus.world.shared.world.WHexGrid grid = gridOpt.get();
                boolean modified = false;

                // Parse coordinate from position string
                HexVector2 coord = TypeUtil.parseHexCoord(position);

                // For each hex side, check if neighbor exists and add edge_flat parameter
                for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE side : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values()) {
                    HexVector2 neighborCoord = getNeighborCoordinate(coord, side);
                    String neighborPosition = TypeUtil.toStringHexCoord(neighborCoord);

                    // Check if neighbor grid exists in our composition
                    if (allCoordinateStrings.contains(neighborPosition)) {
                        // Generate flat ID using naming convention: genesis_{q}_{r}
                        String neighborFlatId = "genesis_" + neighborCoord.getQ() + "_" + neighborCoord.getR();
                        String paramKey = "g_edge_flat_" + side.name().toLowerCase();
                        grid.getParameters().put(paramKey, neighborFlatId);
                        modified = true;
                        log.trace("Set {} = {} for grid at {}", paramKey, neighborFlatId, position);
                    }
                }

                // Add edge blending parameters (if not already set)
                // These control how edges are blended with neighbors
                // Values come from job parameters (or defaults)
                grid.getParameters().putIfAbsent("g_edge_blend_width", edgeBlendWidth);
                grid.getParameters().putIfAbsent("g_edge_blend_randomness", edgeBlendRandomness);
                grid.getParameters().putIfAbsent("g_edge_shake_strength", edgeShakeStrength);
                grid.getParameters().putIfAbsent("g_edge_blur_radius", edgeBlurRadius);
                modified = true;

                if (modified) {
                    hexGridRepository.save(grid);
                    edgeParamsAdded++;
                }
            }

            log.info("Added edge_flat and edge blending parameters to {} grids", edgeParamsAdded);

            // Step 4: Format coordinates as space-separated list (sorted for consistency)
            String coordinatesStr = allCoordinateStrings.stream()
                    .sorted()
                    .collect(Collectors.joining(" "));

            // Build success result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("coordinates", coordinatesStr);
            resultData.put("gridCount", composition.getFeatureHexGridRegistry().size());
            resultData.put("createdGrids", createdGrids);
            resultData.put("updatedGrids", updatedGrids);

            log.info("GenerateHexGridFromComposite completed: gridCount={}, created={}, updated={}",
                    composition.getFeatureHexGridRegistry().size(), createdGrids, updatedGrids);

            return JobResult.success(resultData);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Generate hexgrid job failed", e);
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load document from path format: worldId/collection/name
     */
    private WDocument loadDocumentFromPath(String worldId, String documentId) {
        log.info("Loading document from path: {}", documentId);

        try {
            // Create WorldId
            WorldId wid = WorldId.of(worldId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

            // Load document
            Optional<WDocument> documentOpt = documentService.findByDocumentId(wid, COMPOSED_COLLECTION, documentId);

            if (documentOpt.isEmpty()) {
                log.warn("Document not found: worldId={}, collection={}, id={}", worldId, COMPOSED_COLLECTION, documentId);
                return null;
            }

            log.info("Document loaded: {}", documentId);
            return documentOpt.get();

        } catch (Exception e) {
            log.error("Failed to load document from path: {}", documentId, e);
            return null;
        }
    }

    /**
     * Extract composition directly from document content (no wrapper).
     */
    private HexComposition extractCompositionFromDocument(WDocument document) {
        try {
            // Parse composition directly from document content
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            HexComposition composition = mapper.readValue(document.getContent(), HexComposition.class);

            // Convert featureHexGrids List back to featureHexGridRegistry Map
            // (Jackson can't handle Map<String, FeatureHexGrid> directly, so we use a List for JSON)
            if (composition.getFeatureHexGrids() != null && !composition.getFeatureHexGrids().isEmpty()) {
                Map<String, de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid> registry =
                    composition.getFeatureHexGridRegistry();
                for (de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid grid : composition.getFeatureHexGrids()) {
                    String key = TypeUtil.toStringHexCoord(grid.getCoordinate());
                    registry.put(key, grid);
                }
                log.info("Converted {} FeatureHexGrids from list to registry after deserialization",
                        composition.getFeatureHexGrids().size());
            } else {
                log.warn("No FeatureHexGrids in list to convert to registry");
            }

            log.info("Successfully parsed HexComposition from document");
            return composition;

        } catch (Exception e) {
            log.error("Failed to extract composition from document", e);
            return null;
        }
    }

    /**
     * Get required string parameter from job.
     */
    private String getRequiredParameter(WJob job, String paramName) throws JobExecutionException {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + paramName);
        }
        return value;
    }

    /**
     * Get optional long parameter from job with default value.
     */
    private Long getOptionalLongParameter(WJob job, String paramName, Long defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}", paramName, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get optional string parameter from job with default value.
     */
    private String getOptionalParameter(WJob job, String paramName, String defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get neighbor coordinate for a given hex side.
     * Uses offset coordinates (odd-r stagger) via HexMathUtil.getNeighborPosition.
     */
    private HexVector2 getNeighborCoordinate(HexVector2 coord, de.mhus.nimbus.world.shared.world.WHexGrid.EDGE side) {
        return de.mhus.nimbus.world.shared.util.HexMathUtil.getNeighborPosition(coord, side);
    }

    /**
     * Creates a WHexGrid from a FeatureHexGrid (from Central Registry).
     * This method is analogous to HexCompositeBuilder.createWHexGridFromFeatureHexGrid.
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid createWHexGridFromFeatureHexGrid(
        de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid featureHexGrid,
        String worldId) {

        HexVector2 coord = featureHexGrid.getCoordinate();

        // Create public HexGrid data
        de.mhus.nimbus.generated.types.HexGrid publicData = new de.mhus.nimbus.generated.types.HexGrid();
        publicData.setPosition(coord);
        publicData.setName(featureHexGrid.getName());
        publicData.setTitle(featureHexGrid.getName()); // For now, set title same as name - can be customized later
        publicData.setDescription(featureHexGrid.getDescription());

        // Copy all parameters from FeatureHexGrid
        Map<String, String> parameters = new HashMap<>();
        if (featureHexGrid.getParameters() != null) {
            parameters.putAll(featureHexGrid.getParameters());
        }

        // Add debug text overlay with coordinates
        String coordText = TypeUtil.toStringHexCoord(coord);
        parameters.put("debugText", coordText);

        return de.mhus.nimbus.world.shared.world.WHexGrid.builder()
            .worldId(worldId)
            .position(de.mhus.nimbus.shared.utils.TypeUtil.toStringHexCoord(coord.getQ(), coord.getR()))
            .publicData(publicData)
            .parameters(parameters)
            .enabled(true)
            .build();
    }
}
