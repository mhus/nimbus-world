package de.mhus.nimbus.world.generator.translator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.generated.types.WorldInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * Manual test for GenerateHexGridFromCompositeJobExecutor.
 * Tests the complete hexgrid generation pipeline without Spring Boot overhead.
 */
@Slf4j
public class GenerateHexGridFromCompositeJobTest {

    private GenerateHexGridFromCompositeJobExecutor generateJobExecutor;
    private ApplyTranslatedInstructionJobExecutor applyJobExecutor;
    private WDocumentService documentService;
    private WWorldService worldService;
    private WHexGridService hexGridService;
    private ObjectMapper objectMapper;

    // In-memory document storage for test
    private final Map<String, WDocument> documentStorage = new ConcurrentHashMap<>();

    // Track created hex grids
    private final List<WHexGrid> createdHexGrids = new ArrayList<>();
    private final Set<String> existingHexGridKeys = new HashSet<>();

    private static final String TEST_WORLD_ID = "test:valley";
    private static final String TEST_DOCUMENT_ID = "test-composition-id";
    private static final String GENERATED_JSON_FILE = "translator-generated.json";

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== Setting up GenerateHexGridFromCompositeJobTest ===");

        // Clear storage
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Create ObjectMapper with JavaTimeModule for Instant serialization
        objectMapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();

        // Mock WDocumentService
        documentService = mock(WDocumentService.class);
        setupDocumentServiceMock();

        // Mock WWorldService
        worldService = mock(WWorldService.class);
        setupWorldServiceMock();

        // Mock WHexGridService
        hexGridService = mock(WHexGridService.class);
        setupHexGridServiceMock();

        // Load test document with generated composition
        loadTestDocument();

        // Create job executors
        generateJobExecutor = new GenerateHexGridFromCompositeJobExecutor(
                documentService,
                hexGridService,
                objectMapper
        );

        applyJobExecutor = new ApplyTranslatedInstructionJobExecutor(
                documentService,
                worldService,
                null,
                objectMapper
        );

        log.info("Test setup complete");
    }

    @Test
    public void testGenerateHexGridsFromComposite() throws Exception {
        log.info("=== Starting HexGrid Generation Test ===");

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentId", TEST_DOCUMENT_ID);
        params.put("seed", "42");
        params.put("epoch", "1");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job...");
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());
        assertNotNull(result.resultData(), "Job should return result data");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        String coordinates = resultNode.get("coordinates").asText();
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int updatedGrids = resultNode.get("updatedGrids").asInt();

        log.info("Job completed successfully!");
        log.info("Grid count: {}", gridCount);
        log.info("Created grids: {}", createdGrids);
        log.info("Updated grids: {}", updatedGrids);
        log.info("Coordinates sample: {}",
                coordinates.length() > 100 ? coordinates.substring(0, 100) + "..." : coordinates);

        // Validate results
        assertTrue(gridCount > 0, "Should have generated at least one grid");
        assertEquals(createdGrids, createdHexGrids.size(),
                "Created grids count should match tracked grids");
        assertNotNull(coordinates, "Coordinates should not be null");
        assertFalse(coordinates.isBlank(), "Coordinates should not be empty");

        // Validate coordinate format (should be space-separated "q;r" pairs)
        String[] coordArray = coordinates.split(" ");
        assertEquals(gridCount, coordArray.length,
                "Coordinate count should match grid count");

        for (String coord : coordArray) {
            assertTrue(coord.matches("-?\\d+;-?\\d+"),
                    "Coordinate should be in format 'q;r': " + coord);
        }

        log.info("=== HexGrid Generation Test Successful ===");
        log.info("Total grids generated: {}", gridCount);
        log.info("Actually created in repository: {}", createdHexGrids.size());
    }

    @Test
    public void testGenerateHexGridsWithExistingGrids() throws Exception {
        log.info("=== Starting HexGrid Generation Test with Existing Grids ===");

        // Simulate some existing grids (use TEST_WORLD_ID since executor overrides worldId from job)
        existingHexGridKeys.add(TEST_WORLD_ID + ":0;0");
        existingHexGridKeys.add(TEST_WORLD_ID + ":0;1");
        existingHexGridKeys.add(TEST_WORLD_ID + ":1;0");

        log.info("Simulating {} existing grids", existingHexGridKeys.size());

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentId", TEST_DOCUMENT_ID);
        params.put("seed", "42");
        params.put("epoch", "1");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed even with existing grids");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int updatedGrids = resultNode.get("updatedGrids").asInt();

        log.info("Created: {}, Updated: {}, Total coordinates: {}", createdGrids, updatedGrids, gridCount);

        // Validate that some grids were updated (previously existing grids)
        assertTrue(updatedGrids > 0, "Should have updated at least some existing grids");

        // gridCount is the number of unique coordinates processed
        assertEquals(gridCount, createdGrids + updatedGrids,
                "Grid count should equal created + updated");

        log.info("=== HexGrid Generation Test with Existing Grids Successful ===");
    }

    @Test
    public void testGenerateHexGridsWithInvalidDocumentId() throws Exception {
        log.info("=== Starting HexGrid Generation Test with Invalid Document ID ===");

        // Create job with invalid document ID
        Map<String, String> params = new HashMap<>();
        params.put("documentId", "invalid-document-id");
        params.put("seed", "42");
        params.put("epoch", "1");

        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result - should fail
        assertFalse(result.successful(), "Job should fail with invalid document ID");
        assertNotNull(result.errorMessage(), "Should have error message");
        assertTrue(result.errorMessage().contains("not found"),
                "Error should mention document not found");

        log.info("=== Invalid Document ID Test Successful - Job failed as expected ===");
    }

    @Test
    public void testGenerateHexGridsFromVillageComposite() throws Exception {
        log.info("=== Starting HexGrid Generation Test for Village ===");

        // Clear storage and reload with village composition
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Load village test document
        String villageDocumentId = loadVillageTestDocument();

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentId", villageDocumentId);
        params.put("seed", "42");
        params.put("epoch", "1");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("test:village")
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job for village...");
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());
        assertNotNull(result.resultData(), "Job should return result data");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int updatedGrids = resultNode.get("updatedGrids").asInt();

        log.info("Village job completed successfully!");
        log.info("Grid count: {}", gridCount);
        log.info("Created grids: {}", createdGrids);
        log.info("Updated grids: {}", updatedGrids);

        // Verify at least some grids were created
        assertTrue(createdGrids > 0, "Should have created at least one hexgrid");
        assertEquals(gridCount, createdGrids + updatedGrids, "Grid count should match created + updated");

        // Verify village-specific hexgrids were created
        boolean hasVillageGrid = createdHexGrids.stream()
                .anyMatch(grid -> grid.getParameters() != null &&
                         (grid.getParameters().containsKey("g_village") ||
                          grid.getParameters().containsKey("structureName") ||
                          grid.getParameters().containsKey("districtName")));

        assertTrue(hasVillageGrid, "Should have created at least one village/district hexgrid");

        // Verify g_village parameter exists
        long villageGridsWithGenerator = createdHexGrids.stream()
                .filter(grid -> grid.getParameters() != null &&
                        grid.getParameters().containsKey("g_village"))
                .count();

        log.info("Village hexgrids with g_village parameter: {}", villageGridsWithGenerator);
        assertTrue(villageGridsWithGenerator > 0, "Should have at least one hexgrid with g_village parameter");

        log.info("=== Village HexGrid Generation Test Successful ===");
        log.info("Total village grids generated: {}", createdGrids);
    }

    @Test
    public void testGenerateHexGridsFromGenesisDay2Prepared() throws Exception {
        log.info("=== Starting HexGrid Generation Test for Genesis Day2 (prepared) ===");

        // Clear storage and reload with genesis composition
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Load genesis day2 prepared document
        String genesisDocumentId = loadGenesisDay2PreparedDocument();

        // Create job parameters with edge blending settings
        Map<String, String> params = new HashMap<>();
        params.put("documentId", genesisDocumentId);
        params.put("seed", "42");
        params.put("epoch", "1");
        params.put("edge_blend_width", "30");
        params.put("edge_blend_randomness", "0.6");
        params.put("edge_shake_strength", "0.2");
        params.put("edge_blur_radius", "1");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("ymir:hello1")
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job for genesis day2 prepared...");
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());
        assertNotNull(result.resultData(), "Job should return result data");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int updatedGrids = resultNode.get("updatedGrids").asInt();

        log.info("Genesis day2 job completed successfully!");
        log.info("Grid count: {}", gridCount);
        log.info("Created grids: {}", createdGrids);
        log.info("Updated grids: {}", updatedGrids);

        // Verify at least some grids were created
        assertTrue(gridCount > 0, "Should have generated at least one grid");
        assertTrue(createdGrids > 0 || updatedGrids > 0, "Should have created or updated at least one grid");

        // CRITICAL: Verify ALL grids have g_builder parameter
        log.info("Verifying all {} grids have g_builder parameter...", createdHexGrids.size());
        List<String> gridsWithoutBuilder = new ArrayList<>();

        for (WHexGrid grid : createdHexGrids) {
            Map<String, String> params2 = grid.getParameters();
            if (params2 == null || !params2.containsKey("g_builder")) {
                gridsWithoutBuilder.add(grid.getPosition());
                log.error("Grid {} has no g_builder parameter! Parameters: {}",
                        grid.getPosition(), params2);
            }
        }

        assertTrue(gridsWithoutBuilder.isEmpty(),
                "All grids must have g_builder parameter. Missing in: " + gridsWithoutBuilder);

        // Verify edge blending parameters were added
        long gridsWithEdgeBlending = createdHexGrids.stream()
                .filter(grid -> grid.getParameters() != null &&
                        grid.getParameters().containsKey("g_edge_blend_width"))
                .count();

        log.info("Grids with edge blending parameters: {}", gridsWithEdgeBlending);
        assertTrue(gridsWithEdgeBlending > 0, "Should have added edge blending parameters to at least some grids");

// Note: In the actual BLENDER builder, it calculates neighbor flat IDs based on the center flat's coordinates.
        // Verify edge_flat parameters were added for neighbors
//        long gridsWithEdgeFlat = createdHexGrids.stream()
//                .filter(grid -> grid.getParameters() != null &&
//                        grid.getParameters().keySet().stream()
//                                .anyMatch(key -> key.startsWith("g_edge_flat_")))
//                .count();

//        log.info("Grids with edge_flat neighbor parameters: {}", gridsWithEdgeFlat);
//        assertTrue(gridsWithEdgeFlat > 0, "Should have added edge_flat parameters for neighbors");

        log.info("=== Genesis Day2 HexGrid Generation Test Successful ===");
        log.info("Total grids generated: {}", gridCount);
        log.info("All grids have required g_builder parameter");
    }

    @Test
    public void testFullPipelineFromTranslatedToHexGrids() throws Exception {
        log.info("=== Starting Full Pipeline Test: Translated -> Apply -> Generate ===");

        // Clear storage and reload
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Load genesis day2 translated document
        String translatedDocId = loadGenesisDay2TranslatedDocument();

        // STEP 1: Apply translated instruction (runs OrphanGridFiller, etc.)
        log.info("STEP 1: Applying translated instruction...");
        Map<String, String> applyParams = new HashMap<>();
        applyParams.put("translationDocumentId", translatedDocId);
        applyParams.put("seed", "42");
        applyParams.put("fillGaps", "true");
        applyParams.put("oceanBorderRings", "1");

        WJob applyJob = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("ymir:hello1")
                .executor("generator-apply-translated-instruction")
                .parameters(applyParams)
                .build();

        JobExecutor.JobResult applyResult = applyJobExecutor.execute(applyJob);

        // Validate apply result
        assertTrue(applyResult.successful(), "Apply job should succeed: " + applyResult.errorMessage());
        assertNotNull(applyResult.resultData(), "Apply job should return result data");

        // Parse apply result to get composition document ID
        JsonNode applyResultNode = objectMapper.readTree(applyResult.resultData());
        String compositionDocId = applyResultNode.get("documentId").asText();
        int totalGrids = applyResultNode.get("totalGrids").asInt();

        log.info("Apply job completed: compositionDocumentId={}, totalGrids={}", compositionDocId, totalGrids);

        // Verify that enriched composition was saved
        WDocument compositionDoc = documentStorage.values().stream()
                .filter(doc -> doc.getDocumentId().equals(compositionDocId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Composition document should be saved"));

        log.info("Composition document found: {}", compositionDoc.getName());

        // CRITICAL CHECK AFTER APPLY: Parse composition JSON (direct model) and verify all grids
        log.info("=== Verifying composition after Apply (before Generate) ===");

        JsonNode compositionNode = objectMapper.readTree(compositionDoc.getContent());

        // Count all FeatureHexGrids from the top-level featureHexGrids list
        Map<String, Integer> compositionCoordinateCounts = new HashMap<>();
        List<String> compositionGridsWithoutBuilder = new ArrayList<>();
        int totalFeatureHexGrids = 0;

        JsonNode featureHexGridsList = compositionNode.get("featureHexGrids");
        if (featureHexGridsList != null && featureHexGridsList.isArray()) {
            for (JsonNode hexGrid : featureHexGridsList) {
                totalFeatureHexGrids++;

                JsonNode coordinate = hexGrid.get("coordinate");
                int q = coordinate.get("q").asInt();
                int r = coordinate.get("r").asInt();
                String position = q + ";" + r;

                // Count occurrences
                compositionCoordinateCounts.put(position,
                        compositionCoordinateCounts.getOrDefault(position, 0) + 1);

                // Check for g_builder
                JsonNode parameters = hexGrid.get("parameters");
                if (parameters == null || !parameters.has("g_builder")) {
                    compositionGridsWithoutBuilder.add(position);
                }
            }
        }

        log.info("Composition contains {} FeatureHexGrids", totalFeatureHexGrids);
        log.info("Unique coordinates: {}", compositionCoordinateCounts.size());

        // Check for duplicate coordinates in composition
        List<String> compositionDuplicates = compositionCoordinateCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " (x" + e.getValue() + ")")
                .toList();

        if (!compositionDuplicates.isEmpty()) {
            log.warn("Composition has duplicate coordinates: {}", compositionDuplicates);
            // This is OK - multiple features can reference the same grid
        }

        // Check for grids without g_builder
        assertTrue(compositionGridsWithoutBuilder.isEmpty(),
                "After Apply, all FeatureHexGrids must have g_builder. Missing in: " + compositionGridsWithoutBuilder);

        log.info("All {} FeatureHexGrids in composition have g_builder", totalFeatureHexGrids);
        log.info("Composition has {} unique coordinates", compositionCoordinateCounts.size());

        // STEP 2: Generate hex grids from composed model
        log.info("STEP 2: Generating hex grids from composition...");
        Map<String, String> generateParams = new HashMap<>();
        generateParams.put("documentId", compositionDocId);
        generateParams.put("seed", "42");
        generateParams.put("epoch", "1");
        generateParams.put("edge_blend_width", "30");
        generateParams.put("edge_blend_randomness", "0.6");
        generateParams.put("edge_shake_strength", "0.2");
        generateParams.put("edge_blur_radius", "1");

        WJob generateJob = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("ymir:hello1")
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(generateParams)
                .build();

        JobExecutor.JobResult generateResult = generateJobExecutor.execute(generateJob);

        // Validate generate result
        assertTrue(generateResult.successful(), "Generate job should succeed: " + generateResult.errorMessage());
        assertNotNull(generateResult.resultData(), "Generate job should return result data");

        // Parse generate result
        JsonNode generateResultNode = objectMapper.readTree(generateResult.resultData());
        int gridCount = generateResultNode.get("gridCount").asInt();
        int createdGrids = generateResultNode.get("createdGrids").asInt();
        int updatedGrids = generateResultNode.get("updatedGrids").asInt();

        log.info("Generate job completed: gridCount={}, created={}, updated={}", gridCount, createdGrids, updatedGrids);

        // CRITICAL VALIDATIONS:
        // 1. All grids should have been created (not just updated)
        assertTrue(createdGrids > 0, "Should have created at least one grid");
        // gridCount is the total registry size (biomes + fillers + orphans),
        // totalGrids is only the initial biome count. Grid count should be >= totalGrids.
        assertTrue(gridCount >= totalGrids,
                "Grid count (%d) should be >= total initial biome grids (%d)".formatted(gridCount, totalGrids));

        // 2. Each coordinate should appear only ONCE
        Map<String, Integer> coordinateCounts = new HashMap<>();
        for (WHexGrid grid : createdHexGrids) {
            String pos = grid.getPosition();
            coordinateCounts.put(pos, coordinateCounts.getOrDefault(pos, 0) + 1);
        }

        List<String> duplicates = coordinateCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " (x" + e.getValue() + ")")
                .toList();

        assertTrue(duplicates.isEmpty(),
                "Each coordinate should appear exactly once. Duplicates: " + duplicates);

        // 3. ALL grids must have g_builder parameter
        log.info("Verifying all {} grids have g_builder parameter...", createdHexGrids.size());
        List<String> gridsWithoutBuilder = new ArrayList<>();

        for (WHexGrid grid : createdHexGrids) {
            Map<String, String> params = grid.getParameters();
            if (params == null || !params.containsKey("g_builder")) {
                gridsWithoutBuilder.add(grid.getPosition());
                log.error("Grid {} has no g_builder parameter! Parameters: {}",
                        grid.getPosition(), params);
            }
        }

        assertTrue(gridsWithoutBuilder.isEmpty(),
                "All grids must have g_builder parameter. Missing in: " + gridsWithoutBuilder);

// Note: In the actual BLENDER builder, it calculates neighbor flat IDs based on the center flat's coordinates.
//        // 4. Verify edge parameters were added
//        long gridsWithEdgeFlat = createdHexGrids.stream()
//                .filter(grid -> grid.getParameters() != null &&
//                        grid.getParameters().keySet().stream()
//                                .anyMatch(key -> key.startsWith("g_edge_flat_")))
//                .count();
//
//        log.info("Grids with edge_flat parameters: {}", gridsWithEdgeFlat);
//        assertTrue(gridsWithEdgeFlat > 0, "Should have added edge_flat parameters to at least some grids");

        log.info("=== Full Pipeline Test Successful ===");
        log.info("Total unique grids: {}", gridCount);
        log.info("All grids have required g_builder parameter");
        log.info("No duplicate coordinates");
    }

    @Test
    public void testGenerateHexGridsWithExistingGridsUpdate() throws Exception {
        log.info("=== Starting HexGrid Generation Test with Existing Grids (Update Mode) ===");

        // Clear storage and reload with genesis composition
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Load genesis day2 prepared document
        String genesisDocumentId = loadGenesisDay2PreparedDocument();

        // Simulate existing grids with EMPTY or INCOMPLETE parameters
        // This simulates the scenario where grids were created before OrphanGridFiller ran
        log.info("Simulating existing grids with empty/incomplete parameters...");
        existingHexGridKeys.add("ymir:hello1:0;0");
        existingHexGridKeys.add("ymir:hello1:-1;0");
        existingHexGridKeys.add("ymir:hello1:-2;0");

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentId", genesisDocumentId);
        params.put("seed", "42");
        params.put("epoch", "1");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("ymir:hello1")
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job (should UPDATE existing grids)...");
        JobExecutor.JobResult result = generateJobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int updatedGrids = resultNode.get("updatedGrids").asInt();

        log.info("Created: {}, Updated: {}, Total: {}", createdGrids, updatedGrids, gridCount);

        // Verify that some grids were updated
        assertTrue(updatedGrids > 0, "Should have updated at least the existing grids");

        // CRITICAL: Verify updated grids now have g_builder parameter
        log.info("Verifying all {} grids have g_builder parameter after update...", createdHexGrids.size());

        for (WHexGrid grid : createdHexGrids) {
            Map<String, String> gridParams = grid.getParameters();
            assertTrue(gridParams != null && gridParams.containsKey("g_builder"),
                    "Grid " + grid.getPosition() + " should have g_builder parameter after update");
        }

        log.info("=== HexGrid Generation Test with Update Successful ===");
    }

    /**
     * Enriches a HexComposition JSON by extracting hexGrids from features and adding them
     * as top-level featureHexGrids array. This is needed because old JSON fixtures have
     * hexGrids nested in features[].featureComposed.hexGrids[], but the executor expects
     * a top-level featureHexGrids list for populating the central registry.
     *
     * Deduplicates by coordinate (last writer wins for same q,r).
     */
    private String enrichCompositionJsonWithFeatureHexGrids(String rawJson) throws Exception {
        tools.jackson.databind.node.ObjectNode root =
                (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(rawJson);

        // Collect all hexGrids from features, deduplicating by coordinate
        Map<String, JsonNode> uniqueGrids = new LinkedHashMap<>();

        JsonNode features = root.get("features");
        if (features != null && features.isArray()) {
            for (JsonNode feature : features) {
                JsonNode featureComposed = feature.get("featureComposed");
                if (featureComposed == null) continue;
                JsonNode hexGrids = featureComposed.get("hexGrids");
                if (hexGrids == null || !hexGrids.isArray()) continue;
                for (JsonNode hexGrid : hexGrids) {
                    JsonNode coordinate = hexGrid.get("coordinate");
                    if (coordinate == null) continue;
                    int q = coordinate.get("q").asInt();
                    int r = coordinate.get("r").asInt();
                    String key = q + "," + r;
                    uniqueGrids.put(key, hexGrid);
                }
            }
        }

        // Add featureHexGrids array to root
        tools.jackson.databind.node.ArrayNode featureHexGridsArray =
                objectMapper.createArrayNode();
        for (JsonNode grid : uniqueGrids.values()) {
            featureHexGridsArray.add(grid);
        }
        root.set("featureHexGrids", featureHexGridsArray);

        log.info("Enriched composition JSON with {} featureHexGrids (from {} features)",
                uniqueGrids.size(), features != null ? features.size() : 0);

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    /**
     * Load test document with composition JSON (direct model format, not wrapped).
     */
    private void loadTestDocument() throws Exception {
        log.info("Loading test document from: {}", GENERATED_JSON_FILE);

        // Load generated JSON from resources
        ClassPathResource resource = new ClassPathResource(GENERATED_JSON_FILE);
        String rawJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded composition JSON: {} characters", rawJson.length());

        // Enrich with featureHexGrids from features' hexGrids
        String content = enrichCompositionJsonWithFeatureHexGrids(rawJson);

        WorldId worldId = WorldId.of(TEST_WORLD_ID).orElseThrow();
        String collection = "generator_composed";
        String documentName = "test-composition";

        // Create document with direct model JSON (as ApplyTranslatedInstructionJobExecutor does)
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(documentName)
                .documentId(TEST_DOCUMENT_ID)
                .content(content)
                .build();
        document.touchCreate();

        // Store in memory (by name and by documentId)
        String key = buildStorageKey(worldId.getId(), collection, documentName);
        documentStorage.put(key, document);

        log.info("Stored test document: documentId={}, key={}", TEST_DOCUMENT_ID, key);
    }

    /**
     * Load village test document with composition JSON (direct model format).
     * @return the documentId of the created document
     */
    private String loadVillageTestDocument() throws Exception {
        String villageJsonFile = "translator-generated-village.json";
        log.info("Loading village test document from: {}", villageJsonFile);

        // Load village generated JSON from resources
        ClassPathResource resource = new ClassPathResource(villageJsonFile);
        String rawJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded village composition JSON: {} characters", rawJson.length());

        // Enrich with featureHexGrids from features' hexGrids
        String content = enrichCompositionJsonWithFeatureHexGrids(rawJson);

        WorldId worldId = WorldId.of("test:village").orElseThrow();
        String collection = "generator_composed";
        String documentName = "village-composition";
        String documentId = "village-composition-id";

        // Create document with direct model JSON
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(documentName)
                .documentId(documentId)
                .content(content)
                .build();
        document.touchCreate();

        // Store in memory (by name and by documentId)
        String key = buildStorageKey(worldId.getId(), collection, documentName);
        documentStorage.put(key, document);

        log.info("Stored village test document: documentId={}, key={}", documentId, key);
        return documentId;
    }

    /**
     * Load genesis day2 prepared document with composition JSON (direct model format).
     * The genesis-day2-prepared.json has a wrapper with enrichedCompositionJson;
     * we extract the inner composition JSON, enrich it, and store as direct content.
     * @return the documentId of the created document
     */
    private String loadGenesisDay2PreparedDocument() throws Exception {
        String genesisJsonFile = "genesis-day2-prepared.json";
        log.info("Loading genesis day2 prepared document from: {}", genesisJsonFile);

        // Load genesis JSON from resources
        ClassPathResource resource = new ClassPathResource(genesisJsonFile);
        String documentJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded genesis document JSON: {} characters", documentJson.length());

        // Parse the document to extract enrichedCompositionJson from wrapper
        JsonNode documentNode = objectMapper.readTree(documentJson);
        String rawCompositionJson = documentNode.get("enrichedCompositionJson").asText();

        log.info("Extracted composition JSON: {} characters", rawCompositionJson.length());

        // Enrich with featureHexGrids from features' hexGrids
        String content = enrichCompositionJsonWithFeatureHexGrids(rawCompositionJson);

        WorldId worldId = WorldId.of("ymir:hello1").orElseThrow();
        String collection = "generator_composed";
        String documentName = "genesis-composition";
        String documentId = "genesis-day2-prepared-id";

        // Create document with direct model JSON
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(documentName)
                .documentId(documentId)
                .content(content)
                .build();
        document.touchCreate();

        // Store in memory
        String key = buildStorageKey(worldId.getId(), collection, documentName);
        documentStorage.put(key, document);

        log.info("Stored genesis day2 prepared document: documentId={}, key={}", documentId, key);
        return documentId;
    }

    /**
     * Setup WWorldService mock
     */
    private void setupWorldServiceMock() {
        when(worldService.getByWorldId(any(WorldId.class)))
                .thenAnswer(invocation -> {
                    WorldId worldId = invocation.getArgument(0);

                    // Create WorldInfo with hexGridSize
                    WorldInfo publicData = WorldInfo.builder()
                            .hexGridSize(512)  // Default hex grid size
                            .build();

                    WWorld world = WWorld.builder()
                            .worldId(worldId.getId())
                            .publicData(publicData)
                            .build();
                    return Optional.of(world);
                });
    }

    /**
     * Setup WDocumentService mock for in-memory storage
     */
    private void setupDocumentServiceMock() {
        when(documentService.findByName(any(WorldId.class), any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    WorldId worldId = invocation.getArgument(0);
                    String collection = invocation.getArgument(1);
                    String name = invocation.getArgument(2);

                    String key = buildStorageKey(worldId.getId(), collection, name);
                    WDocument doc = documentStorage.get(key);

                    log.debug("Mock findByName: worldId={}, collection={}, name={}, found={}",
                            worldId, collection, name, doc != null);

                    return Optional.ofNullable(doc);
                });

        // Mock findByDocumentId - retrieve document from storage by documentId
        when(documentService.findByDocumentId(any(WorldId.class), any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    WorldId worldId = invocation.getArgument(0);
                    String collection = invocation.getArgument(1);
                    String documentId = invocation.getArgument(2);

                    // Search through storage for document with matching documentId
                    WDocument foundDoc = documentStorage.values().stream()
                            .filter(doc -> doc.getDocumentId().equals(documentId) &&
                                    doc.getWorldId().equals(worldId.getId()) &&
                                    doc.getCollection().equals(collection))
                            .findFirst()
                            .orElse(null);

                    log.debug("Mock findByDocumentId: worldId={}, collection={}, documentId={}, found={}",
                            worldId, collection, documentId, foundDoc != null);

                    return Optional.ofNullable(foundDoc);
                });

        // Mock save - store document in memory
        when(documentService.save(any(WorldId.class), any(String.class), any(String.class), any()))
                .thenAnswer(invocation -> {
                    WorldId worldId = invocation.getArgument(0);
                    String collection = invocation.getArgument(1);
                    String documentId = invocation.getArgument(2);
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<WDocument> updater = invocation.getArgument(3);

                    // Find or create document
                    WDocument doc = documentStorage.values().stream()
                            .filter(d -> d.getDocumentId().equals(documentId) &&
                                        d.getWorldId().equals(worldId.getId()) &&
                                        d.getCollection().equals(collection))
                            .findFirst()
                            .orElseGet(() -> {
                                WDocument newDoc = WDocument.builder()
                                        .id(UUID.randomUUID().toString())
                                        .worldId(worldId.getId())
                                        .collection(collection)
                                        .documentId(documentId)
                                        .name(documentId)  // Use documentId as name for simplicity
                                        .build();
                                newDoc.touchCreate();
                                return newDoc;
                            });

                    // Apply updates
                    updater.accept(doc);

                    // Store by name key
                    String key = buildStorageKey(doc.getWorldId(), doc.getCollection(), doc.getName());
                    documentStorage.put(key, doc);

                    log.debug("Mock save: stored document at key={}, documentId={}", key, doc.getDocumentId());

                    return doc;
                });
    }

    /**
     * Setup WHexGridService mock to track created/existing grids
     */
    private void setupHexGridServiceMock() {
        // Mock findAllByWorldIdAndPosition (used for both existence checks and lookups)
        when(hexGridService.findAllByWorldIdAndPosition(any(String.class), any(HexVector2.class)))
                .thenAnswer(invocation -> {
                    String worldId = invocation.getArgument(0);
                    HexVector2 hexPos = invocation.getArgument(1);
                    String position = hexPos.getQ() + ";" + hexPos.getR();

                    String key = worldId + ":" + position;

                    // Find the ACTUAL saved grid, not a new empty one
                    Optional<WHexGrid> found = createdHexGrids.stream()
                            .filter(g -> g.getWorldId().equals(worldId) &&
                                         g.getPosition().equals(position))
                            .findFirst();

                    if (found.isPresent()) {
                        return List.of(found.get());
                    }

                    // If not in createdHexGrids but in existingHexGridKeys (simulated existing grids)
                    if (existingHexGridKeys.contains(key)) {
                        // Return a mock grid with EMPTY parameters to simulate the bug
                        // where grids exist but have no g_builder
                        HexGrid publicData = HexGrid.builder()
                                .position(hexPos)
                                .build();
                        WHexGrid grid = WHexGrid.builder()
                                .worldId(worldId)
                                .position(position)
                                .publicData(publicData)
                                .parameters(new HashMap<>())  // Empty parameters to simulate bug
                                .build();
                        return List.of(grid);
                    }

                    log.debug("Mock findAllByWorldIdAndPosition: {}:{} -> empty", worldId, position);
                    return List.of();
                });

        // Mock save
        when(hexGridService.save(any(WHexGrid.class)))
                .thenAnswer(invocation -> {
                    WHexGrid grid = invocation.getArgument(0);

                    // Remove existing grid with same position before adding (to handle updates)
                    createdHexGrids.removeIf(existing ->
                            existing.getWorldId().equals(grid.getWorldId()) &&
                            existing.getPosition().equals(grid.getPosition()));

                    // Add the updated/new grid
                    createdHexGrids.add(grid);

                    HexVector2 pos = grid.getPublicData().getPosition();
                    String key = grid.getWorldId() + ":" + pos.getQ() + ";" + pos.getR();
                    existingHexGridKeys.add(key);

                    log.debug("Mock save: worldId={}, position={}",
                            grid.getWorldId(), grid.getPosition());

                    return grid;
                });
    }

    /**
     * Load genesis day2 translated document
     * @return the documentId of the created document
     */
    private String loadGenesisDay2TranslatedDocument() throws Exception {
        String translatedJsonFile = "genesis-day2-translated.json";
        log.info("Loading genesis day2 translated document from: {}", translatedJsonFile);

        // Load translated JSON from resources
        ClassPathResource resource = new ClassPathResource(translatedJsonFile);
        String translatedJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded translated JSON: {} characters", translatedJson.length());

        // Extract compositionJson from wrapper and fix legacy featureType "village" → "town"
        JsonNode wrapperNode = objectMapper.readTree(translatedJson);
        String compositionJsonStr = wrapperNode.get("compositionJson").asText();
        compositionJsonStr = compositionJsonStr.replace("\"featureType\": \"village\"",
                "\"featureType\": \"town\"");

        // Store the raw composition JSON as document content
        // (ApplyTranslatedInstructionJobExecutor expects direct HexComposition JSON)
        WorldId worldId = WorldId.of("ymir:hello1").orElseThrow();
        String collection = "generator_translations";
        String documentName = "genesis-day2-translated";
        String documentId = "genesis-day2-translated-id";

        // Add metadata with instructionsDocumentId (extracted from wrapper)
        Map<String, String> metadata = new HashMap<>();
        JsonNode metaNode = wrapperNode.get("compositionMetadata");
        if (metaNode != null && metaNode.has("instructionsDocumentId")) {
            metadata.put("instructionsDocumentId", metaNode.get("instructionsDocumentId").asText());
        }

        // Create document with raw composition JSON
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(documentName)
                .documentId(documentId)
                .content(compositionJsonStr)
                .metadata(metadata)
                .build();
        document.touchCreate();

        // Store in memory
        String key = buildStorageKey(worldId.getId(), collection, documentName);
        documentStorage.put(key, document);

        log.info("Stored genesis day2 translated document: documentId={}, key={}", documentId, key);
        return documentId;
    }

    /**
     * Build storage key for document
     */
    private String buildStorageKey(String worldId, String collection, String name) {
        return worldId + "/" + collection + "/" + name;
    }
}
