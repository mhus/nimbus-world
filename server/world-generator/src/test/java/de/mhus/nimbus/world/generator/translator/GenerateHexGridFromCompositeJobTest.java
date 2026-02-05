package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
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

/**
 * Manual test for GenerateHexGridFromCompositeJobExecutor.
 * Tests the complete hexgrid generation pipeline without Spring Boot overhead.
 */
@Slf4j
public class GenerateHexGridFromCompositeJobTest {

    private GenerateHexGridFromCompositeJobExecutor jobExecutor;
    private WDocumentService documentService;
    private WHexGridRepository hexGridRepository;
    private ObjectMapper objectMapper;

    // In-memory document storage for test
    private final Map<String, WDocument> documentStorage = new ConcurrentHashMap<>();

    // Track created hex grids
    private final List<WHexGrid> createdHexGrids = new ArrayList<>();
    private final Set<String> existingHexGridKeys = new HashSet<>();

    private static final String TEST_WORLD_ID = "test:valley";
    private static final String TEST_DOCUMENT_PATH = "test:valley/generator_composed/test-composition";
    private static final String GENERATED_JSON_FILE = "translator-generated.json";

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== Setting up GenerateHexGridFromCompositeJobTest ===");

        // Clear storage
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Create ObjectMapper
        objectMapper = new ObjectMapper();

        // Mock WDocumentService
        documentService = mock(WDocumentService.class);
        setupDocumentServiceMock();

        // Mock WHexGridRepository
        hexGridRepository = mock(WHexGridRepository.class);
        setupHexGridRepositoryMock();

        // Load test document with generated composition
        loadTestDocument();

        // Create job executor
        jobExecutor = new GenerateHexGridFromCompositeJobExecutor(
                documentService,
                hexGridRepository,
                objectMapper
        );

        log.info("Test setup complete");
    }

    @Test
    public void testGenerateHexGridsFromComposite() throws Exception {
        log.info("=== Starting HexGrid Generation Test ===");

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentPath", TEST_DOCUMENT_PATH);
        params.put("seed", "42");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job...");
        JobExecutor.JobResult result = jobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());
        assertNotNull(result.resultData(), "Job should return result data");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        String coordinates = resultNode.get("coordinates").asText();
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int skippedGrids = resultNode.get("skippedGrids").asInt();

        log.info("Job completed successfully!");
        log.info("Grid count: {}", gridCount);
        log.info("Created grids: {}", createdGrids);
        log.info("Skipped grids: {}", skippedGrids);
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

        // Simulate some existing grids
        existingHexGridKeys.add("test-valley-001:0;0");
        existingHexGridKeys.add("test-valley-001:0;1");
        existingHexGridKeys.add("test-valley-001:1;0");

        log.info("Simulating {} existing grids", existingHexGridKeys.size());

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentPath", TEST_DOCUMENT_PATH);
        params.put("seed", "42");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        JobExecutor.JobResult result = jobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed even with existing grids");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int skippedGrids = resultNode.get("skippedGrids").asInt();

        log.info("Created: {}, Skipped: {}, Total coordinates: {}", createdGrids, skippedGrids, gridCount);

        // Validate that some grids were skipped
        assertTrue(skippedGrids > 0, "Should have skipped at least some existing grids");

        // Note: gridCount includes all coordinates from all features (with duplicates)
        // while created+skipped counts only unique processed grids
        assertTrue(gridCount >= createdGrids + skippedGrids,
                "Total coordinates should be at least created + skipped");

        log.info("=== HexGrid Generation Test with Existing Grids Successful ===");
    }

    @Test
    public void testGenerateHexGridsWithInvalidDocumentPath() throws Exception {
        log.info("=== Starting HexGrid Generation Test with Invalid Path ===");

        // Create job with invalid document path
        Map<String, String> params = new HashMap<>();
        params.put("documentPath", "invalid/path/to/document");
        params.put("seed", "42");

        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        JobExecutor.JobResult result = jobExecutor.execute(job);

        // Validate result - should fail
        assertFalse(result.successful(), "Job should fail with invalid document path");
        assertNotNull(result.errorMessage(), "Should have error message");
        assertTrue(result.errorMessage().contains("not found"),
                "Error should mention document not found");

        log.info("=== Invalid Path Test Successful - Job failed as expected ===");
    }

    @Test
    public void testGenerateHexGridsFromVillageComposite() throws Exception {
        log.info("=== Starting HexGrid Generation Test for Village ===");

        // Clear storage and reload with village composition
        documentStorage.clear();
        createdHexGrids.clear();
        existingHexGridKeys.clear();

        // Load village test document
        loadVillageTestDocument();

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("documentPath", "test:village/generator_composed/village-composition");
        params.put("seed", "42");

        // Create job
        WJob job = WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId("test:village")
                .executor("generator-generate-hexgrid-from-composite")
                .parameters(params)
                .build();

        // Execute job
        log.info("Executing hexgrid generation job for village...");
        JobExecutor.JobResult result = jobExecutor.execute(job);

        // Validate result
        assertTrue(result.successful(), "Job should succeed: " + result.errorMessage());
        assertNotNull(result.resultData(), "Job should return result data");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(result.resultData());
        int gridCount = resultNode.get("gridCount").asInt();
        int createdGrids = resultNode.get("createdGrids").asInt();
        int skippedGrids = resultNode.get("skippedGrids").asInt();

        log.info("Village job completed successfully!");
        log.info("Grid count: {}", gridCount);
        log.info("Created grids: {}", createdGrids);
        log.info("Skipped grids: {}", skippedGrids);

        // Verify at least some grids were created
        assertTrue(createdGrids > 0, "Should have created at least one hexgrid");
        assertEquals(gridCount, createdGrids + skippedGrids, "Grid count should match created + skipped");

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

    /**
     * Load test document with enriched composition JSON
     */
    private void loadTestDocument() throws Exception {
        log.info("Loading test document from: {}", GENERATED_JSON_FILE);

        // Load generated JSON from resources
        ClassPathResource resource = new ClassPathResource(GENERATED_JSON_FILE);
        String enrichedCompositionJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded enriched composition JSON: {} characters", enrichedCompositionJson.length());

        // Create document content (wrapped like ApplyTranslatedInstructionJobExecutor does)
        Map<String, Object> documentContent = new HashMap<>();
        documentContent.put("enrichedCompositionJson", enrichedCompositionJson);
        documentContent.put("compositionResult", Map.of(
                "success", true,
                "totalGrids", 50,
                "totalBiomes", 3,
                "totalStructures", 1,
                "totalPoints", 2,
                "totalFlows", 1
        ));

        String content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(documentContent);

        // Parse document path
        String[] parts = TEST_DOCUMENT_PATH.split("/");
        WorldId worldId = WorldId.of(parts[0]).orElseThrow();
        String collection = parts[1];
        String name = parts[2];

        // Create document
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(name)
                .documentId(name)
                .content(content)
                .build();
        document.touchCreate();

        // Store in memory
        String key = buildStorageKey(worldId.getId(), collection, name);
        documentStorage.put(key, document);

        log.info("Stored test document: {}", key);
    }

    /**
     * Load village test document with enriched composition JSON
     */
    private void loadVillageTestDocument() throws Exception {
        String villageJsonFile = "translator-generated-village.json";
        log.info("Loading village test document from: {}", villageJsonFile);

        // Load village generated JSON from resources
        ClassPathResource resource = new ClassPathResource(villageJsonFile);
        String enrichedCompositionJson = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        log.info("Loaded village enriched composition JSON: {} characters", enrichedCompositionJson.length());

        // Create document content (wrapped like ApplyTranslatedInstructionJobExecutor does)
        Map<String, Object> documentContent = new HashMap<>();
        documentContent.put("enrichedCompositionJson", enrichedCompositionJson);
        documentContent.put("compositionResult", Map.of(
                "success", true,
                "totalGrids", 10,
                "totalBiomes", 1,
                "totalStructures", 1,
                "totalPoints", 0,
                "totalFlows", 0
        ));

        String content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(documentContent);

        // Parse document path for village
        String villagePath = "test:village/generator_composed/village-composition";
        String[] parts = villagePath.split("/");
        WorldId worldId = WorldId.of(parts[0]).orElseThrow();
        String collection = parts[1];
        String name = parts[2];

        // Create document
        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(worldId.getId())
                .collection(collection)
                .name(name)
                .documentId(name)
                .content(content)
                .build();
        document.touchCreate();

        // Store in memory
        String key = buildStorageKey(worldId.getId(), collection, name);
        documentStorage.put(key, document);

        log.info("Stored village test document: {}", key);
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
    }

    /**
     * Setup WHexGridRepository mock to track created/existing grids
     */
    private void setupHexGridRepositoryMock() {
        // Mock existsByWorldIdAndPosition
        when(hexGridRepository.existsByWorldIdAndPosition(any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    String worldId = invocation.getArgument(0);
                    String position = invocation.getArgument(1);

                    String key = worldId + ":" + position;
                    boolean exists = existingHexGridKeys.contains(key);

                    log.debug("Mock existsByWorldIdAndPosition: {}:{} -> {}", worldId, position, exists);
                    return exists;
                });

        // Mock save
        when(hexGridRepository.save(any(WHexGrid.class)))
                .thenAnswer(invocation -> {
                    WHexGrid grid = invocation.getArgument(0);

                    createdHexGrids.add(grid);
                    HexVector2 pos = grid.getPublicData().getPosition();
                    String key = grid.getWorldId() + ":" + pos.getQ() + ";" + pos.getR();
                    existingHexGridKeys.add(key);

                    log.debug("Mock save: worldId={}, position={}",
                            grid.getWorldId(), grid.getPosition());

                    return grid;
                });

        // Mock findByWorldIdAndPosition (for updates)
        when(hexGridRepository.findByWorldIdAndPosition(any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    String worldId = invocation.getArgument(0);
                    String position = invocation.getArgument(1);

                    String key = worldId + ":" + position;

                    if (existingHexGridKeys.contains(key)) {
                        // Return a mock grid
                        String[] parts = position.split(";");
                        int q = Integer.parseInt(parts[0]);
                        int r = Integer.parseInt(parts[1]);

                        HexVector2 hexPos = HexVector2.builder()
                                .q(q)
                                .r(r)
                                .build();
                        HexGrid publicData = HexGrid.builder()
                                .position(hexPos)
                                .build();
                        WHexGrid grid = WHexGrid.builder()
                                .worldId(worldId)
                                .position(position)
                                .publicData(publicData)
                                .build();
                        return Optional.of(grid);
                    }

                    return Optional.empty();
                });
    }

    /**
     * Build storage key for document
     */
    private String buildStorageKey(String worldId, String collection, String name) {
        return worldId + "/" + collection + "/" + name;
    }
}
