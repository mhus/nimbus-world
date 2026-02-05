package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
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

/**
 * Job executor for generating WHexGrids from a composed model.
 *
 * Executor name: 'generator-generate-hexgrid-from-composite'
 *
 * Required parameters:
 * - documentPath: Path to the document containing the enriched composition (in 'generator_composed' collection)
 *
 * Optional parameters:
 * - seed: Random seed for reproducible generation (default: random)
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
            String documentPath = getRequiredParameter(job, "documentPath");

            // Extract optional parameters
            Long seed = getOptionalLongParameter(job, "seed", null);
            if (seed == null) {
                seed = System.currentTimeMillis();
                log.info("No seed provided, using current time: {}", seed);
            }

            log.info("Generating hexgrids: documentPath={}, seed={}", documentPath, seed);

            // Step 1: Load document from path
            WDocument document = loadDocumentFromPath(job.getWorldId(), documentPath);
            if (document == null) {
                return JobResult.failure("Document not found: " + documentPath);
            }

            // Step 2: Extract enriched composition from document
            HexComposition composition = extractCompositionFromDocument(document);
            if (composition == null) {
                return JobResult.failure("Failed to extract composition from document");
            }

            // Override worldId with the one from job context (defensive programming)
            composition.setWorldId(job.getWorldId());

            log.info("Loaded enriched composition: name='{}', worldId='{}', features={}",
                    composition.getName(),
                    composition.getWorldId(),
                    composition.getFeatures() != null ? composition.getFeatures().size() : 0);

            // Step 3: Create WHexGrids from already enriched/composed model
            // The composition has already been applied by ApplyTranslatedInstructionJob
            // We just need to extract the hexGrids and create WHexGrid entities

            int createdGrids = 0;
            int skippedGrids = 0;
            List<HexVector2> allCoordinates = new ArrayList<>();

            if (composition.getFeatures() != null) {
                for (var feature : composition.getFeatures()) {
                    if (feature.getHexGrids() == null || feature.getHexGrids().isEmpty()) {
                        continue;
                    }

                    for (var hexGrid : feature.getHexGrids()) {
                        HexVector2 coord = hexGrid.getCoordinate();
                        allCoordinates.add(coord);

                        // Check if grid already exists
                        String position = coord.getQ() + ";" + coord.getR();
                        boolean exists = hexGridRepository.existsByWorldIdAndPosition(
                                composition.getWorldId(), position);

                        if (exists) {
                            log.debug("WHexGrid already exists: {} at {}", composition.getWorldId(), position);
                            skippedGrids++;
                            continue;
                        }

                        // Create new WHexGrid entity
                        de.mhus.nimbus.generated.types.HexGrid publicData = de.mhus.nimbus.generated.types.HexGrid.builder()
                                .position(coord)
                                .build();

                        de.mhus.nimbus.world.shared.world.WHexGrid wHexGrid = de.mhus.nimbus.world.shared.world.WHexGrid.builder()
                                .worldId(composition.getWorldId())
                                .position(position)
                                .publicData(publicData)
                                .parameters(hexGrid.getParameters() != null ? new HashMap<>(hexGrid.getParameters()) : new HashMap<>())
                                .build();
                        wHexGrid.touchCreate();
                        wHexGrid.syncPositionKey();

                        // Save to repository
                        hexGridRepository.save(wHexGrid);
                        createdGrids++;

                        log.debug("Created WHexGrid: {} at {}", composition.getWorldId(), position);
                    }
                }
            }

            log.info("WHexGrid generation complete: created={}, skipped={}, total={}",
                    createdGrids, skippedGrids, allCoordinates.size());

            // Step 4: Format coordinates as space-separated list
            String coordinatesStr = allCoordinates.stream()
                    .map(coord -> coord.getQ() + ";" + coord.getR())
                    .collect(Collectors.joining(" "));

            // Build success result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("coordinates", coordinatesStr);
            resultData.put("gridCount", allCoordinates.size());
            resultData.put("createdGrids", createdGrids);
            resultData.put("skippedGrids", skippedGrids);

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
    private WDocument loadDocumentFromPath(String worldId, String documentPath) {
        log.info("Loading document from path: {}", documentPath);

        try {
            // Parse path: worldId/collection/name
            String[] parts = documentPath.split("/");
            if (parts.length != 3) {
                log.error("Invalid document path format: {}. Expected: worldId/collection/name", documentPath);
                return null;
            }

            String pathWorldId = parts[0];
            String collection = parts[1];
            String name = parts[2];

            // Create WorldId
            WorldId wid = WorldId.of(pathWorldId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + pathWorldId));

            // Load document
            Optional<WDocument> documentOpt = documentService.findByName(wid, collection, name);

            if (documentOpt.isEmpty()) {
                log.warn("Document not found: worldId={}, collection={}, name={}", pathWorldId, collection, name);
                return null;
            }

            log.info("Document loaded: {}", name);
            return documentOpt.get();

        } catch (Exception e) {
            log.error("Failed to load document from path: {}", documentPath, e);
            return null;
        }
    }

    /**
     * Extract enriched composition JSON from document.
     */
    private HexComposition extractCompositionFromDocument(WDocument document) {
        try {
            // Parse document content
            JsonNode content = objectMapper.readTree(document.getContent());

            // Extract enriched composition JSON
            if (!content.has("enrichedCompositionJson")) {
                log.error("Document does not contain enrichedCompositionJson field");
                return null;
            }

            String compositionJson = content.get("enrichedCompositionJson").asText();

            // Parse to HexComposition
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            HexComposition composition = mapper.readValue(compositionJson, HexComposition.class);

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
}
