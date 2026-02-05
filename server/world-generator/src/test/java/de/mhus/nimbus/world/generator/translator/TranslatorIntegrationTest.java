package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.ai.model.gemini.GeminiChat;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Manual integration test for the complete translation pipeline.
 *
 * Tests that require AI service are disabled by default.
 * The document service mock test runs without AI dependencies.
 */
@Slf4j
@Disabled // Disabled by default - start manual for testing with real Gemini API
public class TranslatorIntegrationTest {

    private TranslatorService translatorService;
    private TranslateInstructionJobExecutor translateJobExecutor;
    private ApplyTranslatedInstructionJobExecutor applyTranslatedJobExecutor;
    private AiModelService aiModelService;
    private WDocumentService documentService;
    private WWorldService worldService;
    private ObjectMapper objectMapper;

    // In-memory document storage for test
    private final Map<String, WDocument> documentStorage = new ConcurrentHashMap<>();

    // Gemini configuration loaded from application-test-default.yaml
    private String geminiApiKey;
    private String geminiModel;
    private double geminiTemperature;
    private int geminiMaxTokens;

    private static final String TEST_CONFIG_FILE = "application-test-default.yaml";
    private static final String TEST_WORLD_ID = "test:world";
    private static final String TEST_INSTRUCTION_FILE = "test-instruction-simple-world.txt";
    private static final String TEST_INSTRUCTION_MINIMAL_FILE = "test-instruction-minimal-world.txt";
    private static final String COMPOSER_MODEL_DESCRIPTION_FILE = "documents/generator/composer-model-description.md";
    private static final String LESSONS_LEARNED_FILE = "composer-model-lessons-learned.md";

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== Setting up TranslatorIntegrationTest ===");

        // Clear document storage
        documentStorage.clear();

        // Load configuration from YAML
        loadTestConfiguration();

        // Create ObjectMapper with JavaTimeModule for Instant support
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        // Mock WDocumentService
        documentService = mock(WDocumentService.class);
        setupDocumentServiceMock();

        // Load composer-model-description.md into document storage
        loadComposerModelDescription();

        // Load lessons learned into document storage (optional - test continues if not found)
        boolean lessonsLearnedLoaded = loadLessonsLearned();
        if (!lessonsLearnedLoaded) {
            log.warn("Lessons learned document not found - continuing without it");
        }

        // Create GeminiChat with configuration from YAML
        AiChat geminiChat = createGeminiChat();
        log.info("Created Gemini chat: {}", geminiChat.getName());

        // Mock AiModelService
        aiModelService = mock(AiModelService.class);
        when(aiModelService.createChat(anyString(), any(AiChatOptions.class)))
                .thenReturn(Optional.of(geminiChat));
        when(aiModelService.createChat(anyString()))
                .thenReturn(Optional.of(geminiChat));

        // Create TranslatorService
        translatorService = new TranslatorService(aiModelService, documentService, objectMapper);

        // Create TranslateInstructionJobExecutor (with retry logic)
        translateJobExecutor = new TranslateInstructionJobExecutor(translatorService, documentService, objectMapper);

        // Mock WWorldService with a test world
        worldService = mock(WWorldService.class);

        // Create mock world with required publicData
        de.mhus.nimbus.generated.types.WorldInfo mockPublicData =
            de.mhus.nimbus.generated.types.WorldInfo.builder()
                .hexGridSize(512)  // Default hex grid size
                .chunkSize(32)     // Default chunk size
                .build();

        WWorld mockWorld = WWorld.builder()
                .worldId(TEST_WORLD_ID)
                .publicData(mockPublicData)
                .build();

        when(worldService.getByWorldId(any(WorldId.class))).thenReturn(Optional.of(mockWorld));

        // Create ApplyTranslatedInstructionJobExecutor
        applyTranslatedJobExecutor = new ApplyTranslatedInstructionJobExecutor(documentService, worldService, objectMapper);

        log.info("Test setup complete with real Gemini API");
    }

    /**
     * Load test configuration from application-test-default.yaml
     * Skips test if config file or API key is missing
     */
    private void loadTestConfiguration() {
        log.info("Loading test configuration from: {}", TEST_CONFIG_FILE);

        try {
            // Load YAML file
            ClassPathResource resource = new ClassPathResource(TEST_CONFIG_FILE);

            // Check if resource exists
            Assumptions.assumeTrue(resource.exists(),
                "Test skipped: Config file not found: " + TEST_CONFIG_FILE);

            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            JsonNode config = yamlMapper.readTree(resource.getInputStream());

            // Extract Gemini configuration
            JsonNode geminiConfig = config.path("ai").path("gemini");

            geminiApiKey = geminiConfig.path("api-key").asText();
            geminiModel = geminiConfig.path("model").asText();
            geminiTemperature = geminiConfig.path("temperature").asDouble(0.7);
            geminiMaxTokens = geminiConfig.path("max-tokens").asInt(15000);

            // Skip test if API key is missing or empty
            Assumptions.assumeTrue(geminiApiKey != null && !geminiApiKey.isBlank(),
                "Test skipped: Gemini API key not configured in " + TEST_CONFIG_FILE);

            log.info("Loaded Gemini config: model={}, temperature={}, maxTokens={}",
                    geminiModel, geminiTemperature, geminiMaxTokens);

        } catch (Exception e) {
            // Skip test if config cannot be loaded
            Assumptions.assumeTrue(false,
                "Test skipped: Failed to load config from " + TEST_CONFIG_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Create GeminiChat with configuration from application-test-default.yaml
     */
    private AiChat createGeminiChat() {
        log.info("Creating Gemini chat model: model={}, temperature={}, maxTokens={}",
                geminiModel, geminiTemperature, geminiMaxTokens);

        // Create LangChain4j Gemini chat model
        var chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModel)
                .temperature(geminiTemperature)
                .maxOutputTokens(geminiMaxTokens)
                .timeout(Duration.ofSeconds(120))
                .logRequestsAndResponses(false)
                .build();

        // Create AiChatOptions
        AiChatOptions options = AiChatOptions.builder()
                .temperature(geminiTemperature)
                .maxTokens(geminiMaxTokens)
                .timeoutSeconds(120)
                .build();

        // Create GeminiChat (no rate limiter for test)
        return new GeminiChat("gemini:" + geminiModel, chatModel, options, null);
    }

    /**
     * Load composer-model-description.md into document storage for TranslatorService
     */
    private void loadComposerModelDescription() throws Exception {
        log.info("Loading composer-model-description.md from resources");

        // Load file from resources
        ClassPathResource resource = new ClassPathResource(COMPOSER_MODEL_DESCRIPTION_FILE);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        log.info("Loaded composer-model-description.md: {} characters", content.length());

        // Create document in storage (worldId @shared:n, collection generator)
        WorldId sharedWorldId = WorldId.of(WorldId.COLLECTION_SHARED, "n").orElseThrow();
        String collection = "generator";
        String documentName = "composer-model-description.md";

        WDocument document = WDocument.builder()
                .id(UUID.randomUUID().toString())
                .worldId(sharedWorldId.getId())
                .collection(collection)
                .name(documentName)
                .content(content)
                .build();
        document.touchCreate();

        String key = buildStorageKey(sharedWorldId.getId(), collection, documentName);
        documentStorage.put(key, document);

        log.info("Stored composer-model-description.md in document storage: {}", key);
    }

    /**
     * Load composer-model-lessons-learned.md into document storage for TranslatorService
     *
     * @return true if loaded successfully, false if file not found
     */
    private boolean loadLessonsLearned() {
        log.info("Loading composer-model-lessons-learned.md from resources");

        try {
            // Load file from resources
            ClassPathResource resource = new ClassPathResource(LESSONS_LEARNED_FILE);

            // Check if resource exists
            if (!resource.exists()) {
                log.info("Lessons learned file not found at: {} - skipping", LESSONS_LEARNED_FILE);
                return false;
            }

            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            log.info("Loaded composer-model-lessons-learned.md: {} characters", content.length());

            // Create document in storage (worldId @shared:n, collection generator)
            WorldId sharedWorldId = WorldId.of(WorldId.COLLECTION_SHARED, "n").orElseThrow();
            String collection = "generator";
            String documentName = "composer-model-lessons-learned.md";

            WDocument document = WDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .worldId(sharedWorldId.getId())
                    .collection(collection)
                    .name(documentName)
                    .content(content)
                    .build();
            document.touchCreate();

            String key = buildStorageKey(sharedWorldId.getId(), collection, documentName);
            documentStorage.put(key, document);

            log.info("Stored composer-model-lessons-learned.md in document storage: {}", key);
            return true;

        } catch (Exception e) {
            log.warn("Failed to load lessons learned (optional): {}", e.getMessage());
            return false;
        }
    }

    @Test
    public void testTranslateInstructionWithJobExecutor() throws Exception {
        log.info("=== Starting Translation Test with JobExecutor (includes retry logic) ===");

        // Load test instruction
        String instruction = loadTestInstruction();
        assertNotNull(instruction, "Test instruction should be loaded");
        log.info("Loaded instruction: {} characters", instruction.length());

        // Create job parameters
        Map<String, String> params = new HashMap<>();
        params.put("instruction", instruction);
        params.put("documentPath", "test_translations");
        params.put("maxAttempts", "5");  // Allow 5 attempts with error feedback

        // Create job
        var job = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-translate-instruction")
                .parameters(params)
                .build();

        // Execute job (with automatic retry on errors)
        log.info("Executing translation job with retry logic...");
        var jobResult = translateJobExecutor.execute(job);

        // Validate job result
        assertTrue(jobResult.successful(), "Translation job should succeed: " + jobResult.errorMessage());
        assertNotNull(jobResult.resultData(), "Job should return result data");

        log.info("Job completed successfully!");

        // Parse result data
        JsonNode resultNode = objectMapper.readTree(jobResult.resultData());
        String documentPath = resultNode.get("documentPath").asText();
        int featuresCount = resultNode.get("featuresCount").asInt();
        String worldId = resultNode.get("worldId").asText();
        String compositionName = resultNode.get("compositionName").asText();

        log.info("Translation result: documentPath={}, features={}, worldId={}, name={}",
                documentPath, featuresCount, worldId, compositionName);

        // Validate result data
        assertNotNull(documentPath, "Document path should not be null");
        assertTrue(featuresCount > 0, "Should have at least one feature");
        assertNotNull(worldId, "WorldId should not be null");
        assertNotNull(compositionName, "Composition name should not be null");

        // Load and validate the saved document
        WDocument savedDoc = getDocumentFromStorage(documentPath);
        assertNotNull(savedDoc, "Saved document should exist");
        assertNotNull(savedDoc.getContent(), "Document should have content");

        // Parse document content
        JsonNode docContent = objectMapper.readTree(savedDoc.getContent());
        assertTrue(docContent.has("compositionJson"), "Document should contain compositionJson");
        assertTrue(docContent.has("originalInstruction"), "Document should contain original instruction");

        String compositionJson = docContent.get("compositionJson").asText();
        assertNotNull(compositionJson, "Composition JSON should not be null");

        // Validate that JSON can be parsed to HexComposition
        HexComposition composition = objectMapper.readValue(compositionJson, HexComposition.class);
        assertNotNull(composition, "Composition should be parseable");
        assertNotNull(composition.getFeatures(), "Composition should have features");
        assertFalse(composition.getFeatures().isEmpty(), "Composition should have at least one feature");

        log.info("Successfully parsed composition: name='{}', worldId='{}', features={}",
                composition.getName(), composition.getWorldId(), composition.getFeatures().size());

        // Print generated JSON to console
        System.out.println("\n" + "=".repeat(80));
        System.out.println("GENERATED COMPOSER MODEL JSON:");
        System.out.println("=".repeat(80));
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(composition));
        System.out.println("=".repeat(80));
        System.out.println("Features:");
        composition.getFeatures().forEach(feature -> {
            System.out.println("  - " + feature.getClass().getSimpleName() + ": " +
                feature.getName() + " (" + feature.getTitle() + ")");
        });
        System.out.println("=".repeat(80) + "\n");

        // Now test ApplyTranslatedInstructionJob
        log.info("=== Starting ApplyTranslatedInstruction Test ===");

        // Create ApplyTranslatedInstruction job parameters
        Map<String, String> applyParams = new HashMap<>();
        applyParams.put("documentPath", documentPath);
        applyParams.put("maxAttempts", "5");  // Allow 5 attempts with error feedback

        // Create ApplyTranslatedInstruction job
        var applyJob = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-apply-translated-instruction")
                .parameters(applyParams)
                .build();

        // Execute ApplyTranslatedInstruction job
        log.info("Executing apply translated instruction job...");
        var applyJobResult = applyTranslatedJobExecutor.execute(applyJob);

        // Validate apply job result
        assertTrue(applyJobResult.successful(), "Apply job should succeed: " + applyJobResult.errorMessage());
        assertNotNull(applyJobResult.resultData(), "Apply job should return result data");

        log.info("Apply job completed successfully!");

        // Parse apply result data
        JsonNode applyResultNode = objectMapper.readTree(applyJobResult.resultData());
        String enrichedDocumentPath = applyResultNode.get("documentPath").asText();

        log.info("Enriched document path: {}", enrichedDocumentPath);

        // Load enriched document
        WDocument enrichedDoc = getDocumentFromStorage(enrichedDocumentPath);
        assertNotNull(enrichedDoc, "Enriched document should exist");

        // Parse enriched document content
        JsonNode enrichedContent = objectMapper.readTree(enrichedDoc.getContent());
        assertTrue(enrichedContent.has("enrichedCompositionJson"), "Document should contain enrichedCompositionJson");

        String enrichedCompositionJson = enrichedContent.get("enrichedCompositionJson").asText();
        assertNotNull(enrichedCompositionJson, "Enriched composition JSON should not be null");

        // Parse enriched composition
        HexComposition enrichedComposition = objectMapper.readValue(enrichedCompositionJson, HexComposition.class);
        assertNotNull(enrichedComposition, "Enriched composition should be parseable");

        // Count total hexGrids across all features
        int totalHexGrids = 0;
        if (enrichedComposition.getFeatures() != null) {
            for (var feature : enrichedComposition.getFeatures()) {
                if (feature.getHexGrids() != null) {
                    totalHexGrids += feature.getHexGrids().size();
                }
            }
        }

        log.info("Enriched composition: name='{}', features={}, totalHexGrids={}",
                enrichedComposition.getName(),
                enrichedComposition.getFeatures() != null ? enrichedComposition.getFeatures().size() : 0,
                totalHexGrids);

        // Print enriched JSON to console
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ENRICHED COMPOSER MODEL JSON (with hexGrids):");
        System.out.println("=".repeat(80));
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(enrichedComposition));
        System.out.println("=".repeat(80));
        System.out.println("Total HexGrids: " + totalHexGrids);
        System.out.println("=".repeat(80) + "\n");

        log.info("=== Translation and Apply Test Successful ===");
    }

    /**
     * Get document from storage by path.
     */
    private WDocument getDocumentFromStorage(String path) {
        WDocument doc = documentStorage.get(path);
        log.debug("Get document from storage: path={}, found={}", path, doc != null);
        return doc;
    }

    /**
     * Test the basic workflow without AI service.
     * Tests only the document service mock and data structures.
     */
    @Test
    public void testDocumentServiceMock() throws Exception {
        log.info("=== Testing Document Service Mock ===");

        // Create test document
        WorldId worldId = WorldId.of(TEST_WORLD_ID).orElseThrow();
        String collection = "test_collection";
        String documentId = UUID.randomUUID().toString();
        String documentName = "test-doc";

        // Save document
        WDocument savedDoc = documentService.save(worldId, collection, documentId, doc -> {
            doc.setName(documentName);
            doc.setTitle("Test Document");
            doc.setContent("{\"test\": \"data\"}");
        });

        assertNotNull(savedDoc, "Document should be saved");
        assertEquals(documentName, savedDoc.getName(), "Document name should match");

        // Retrieve document
        String key = buildStorageKey(worldId.getId(), collection, documentName);
        WDocument retrievedDoc = documentStorage.get(key);

        assertNotNull(retrievedDoc, "Document should be retrievable");
        assertEquals(documentName, retrievedDoc.getName(), "Retrieved name should match");
        assertEquals("{\"test\": \"data\"}", retrievedDoc.getContent(), "Content should match");

        log.info("=== Document Service Mock Test Successful ===");
    }

    @Test
    public void testMinimalWorldComposition() throws Exception {
        log.info("=== Starting Minimal World Composition Test ===");

        // Load minimal instruction
        ClassPathResource resource = new ClassPathResource(TEST_INSTRUCTION_MINIMAL_FILE);
        String instruction = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Step 1: Translate
        Map<String, String> translateParams = new HashMap<>();
        translateParams.put("instruction", instruction);
        translateParams.put("documentPath", "test_translations");
        translateParams.put("maxAttempts", "5"); // Allow 5 attempts with error feedback

        var translateJob = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-translate-instruction")
                .parameters(translateParams)
                .build();

        var translateResult = translateJobExecutor.execute(translateJob);
        assertTrue(translateResult.successful(), "Translation should succeed");

        JsonNode translateResultNode = objectMapper.readTree(translateResult.resultData());
        String documentPath = translateResultNode.get("documentPath").asText();

        // Step 2: Apply
        Map<String, String> applyParams = new HashMap<>();
        applyParams.put("documentPath", documentPath);
        applyParams.put("maxAttempts", "5"); // Allow 5 attempts with error feedback
        applyParams.put("fillGaps", "false");  // Don't fill gaps for minimal test
        applyParams.put("oceanBorderRings", "0");  // No ocean border

        var applyJob = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-apply-translated-instruction")
                .parameters(applyParams)
                .build();

        var applyResult = applyTranslatedJobExecutor.execute(applyJob);
        assertTrue(applyResult.successful(), "Apply should succeed: " + applyResult.errorMessage());

        // Get enriched composition
        JsonNode applyResultNode = objectMapper.readTree(applyResult.resultData());
        String enrichedDocumentPath = applyResultNode.get("documentPath").asText();

        WDocument enrichedDoc = getDocumentFromStorage(enrichedDocumentPath);
        JsonNode enrichedContent = objectMapper.readTree(enrichedDoc.getContent());
        String enrichedCompositionJson = enrichedContent.get("enrichedCompositionJson").asText();

        HexComposition enrichedComposition = objectMapper.readValue(enrichedCompositionJson, HexComposition.class);

        // Count hexGrids
        int totalHexGrids = 0;
        if (enrichedComposition.getFeatures() != null) {
            for (var feature : enrichedComposition.getFeatures()) {
                if (feature.getHexGrids() != null) {
                    totalHexGrids += feature.getHexGrids().size();
                }
            }
        }

        log.info("Enriched composition has {} total hexGrids", totalHexGrids);

        // Print to console
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ENRICHED COMPOSITION JSON FOR translator-generated.json:");
        System.out.println("=".repeat(80));
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(enrichedComposition));
        System.out.println("=".repeat(80));
        System.out.println("Total HexGrids: " + totalHexGrids);
        System.out.println("=".repeat(80) + "\n");

        assertTrue(totalHexGrids > 0, "Should have generated some hexGrids");

        log.info("=== Minimal World Composition Test Successful ===");
    }

    @Test
    public void testVillageWithDistrictComposition() throws Exception {
        log.info("=== Starting Village with District Composition Test ===");

        // Load village instruction
        ClassPathResource resource = new ClassPathResource("test-instruction-village.txt");
        String instruction = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Step 1: Translate
        Map<String, String> translateParams = new HashMap<>();
        translateParams.put("instruction", instruction);
        translateParams.put("documentPath", "test_translations");
        translateParams.put("maxAttempts", "3");

        var translateJob = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-translate-instruction")
                .parameters(translateParams)
                .build();

        var translateResult = translateJobExecutor.execute(translateJob);
        assertTrue(translateResult.successful(), "Translation should succeed");

        JsonNode translateResultNode = objectMapper.readTree(translateResult.resultData());
        String documentPath = translateResultNode.get("documentPath").asText();

        // Step 2: Apply
        Map<String, String> applyParams = new HashMap<>();
        applyParams.put("documentPath", documentPath);
        applyParams.put("maxAttempts", "3");
        applyParams.put("fillGaps", "false");
        applyParams.put("oceanBorderRings", "0");

        var applyJob = de.mhus.nimbus.world.shared.job.WJob.builder()
                .id(UUID.randomUUID().toString())
                .worldId(TEST_WORLD_ID)
                .executor("generator-apply-translated-instruction")
                .parameters(applyParams)
                .build();

        var applyResult = applyTranslatedJobExecutor.execute(applyJob);
        assertTrue(applyResult.successful(), "Apply should succeed: " + applyResult.errorMessage());

        // Get enriched composition
        JsonNode applyResultNode = objectMapper.readTree(applyResult.resultData());
        String enrichedDocumentPath = applyResultNode.get("documentPath").asText();

        WDocument enrichedDoc = getDocumentFromStorage(enrichedDocumentPath);
        JsonNode enrichedContent = objectMapper.readTree(enrichedDoc.getContent());
        String enrichedCompositionJson = enrichedContent.get("enrichedCompositionJson").asText();

        HexComposition enrichedComposition = objectMapper.readValue(enrichedCompositionJson, HexComposition.class);

        // Verify village with district
        assertNotNull(enrichedComposition.getFeatures(), "Should have features");

        var villages = enrichedComposition.getFeatures().stream()
                .filter(f -> f instanceof de.mhus.nimbus.world.generator.composer.village.Village)
                .map(f -> (de.mhus.nimbus.world.generator.composer.village.Village) f)
                .toList();

        assertFalse(villages.isEmpty(), "Should have at least one village");

        var village = villages.get(0);
        assertEquals("test-village", village.getName(), "Village should have correct name");
        assertNotNull(village.getDistricts(), "Village should have districts");
        assertFalse(village.getDistricts().isEmpty(), "Village should have at least one district");

        var district = village.getDistricts().get(0);
        assertEquals("market-district", district.getName(), "District should have correct name");
        assertNotNull(district.getPlaces(), "District should have places");
        assertTrue(district.getPlaces().size() >= 3, "District should have at least 3 places");

        // Count hexGrids
        int totalHexGrids = 0;
        if (enrichedComposition.getFeatures() != null) {
            for (var feature : enrichedComposition.getFeatures()) {
                if (feature.getHexGrids() != null) {
                    totalHexGrids += feature.getHexGrids().size();
                }
            }
        }

        log.info("Village composition has {} total hexGrids", totalHexGrids);

        // Print enriched composition JSON for translator-generated-village.json
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ENRICHED COMPOSITION JSON FOR translator-generated-village.json:");
        System.out.println("=".repeat(80));
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(enrichedComposition));
        System.out.println("=".repeat(80));
        System.out.println("Total HexGrids: " + totalHexGrids);
        System.out.println("=".repeat(80));
        System.out.println("\nVillage Summary:");
        System.out.println("  Name: " + village.getName() + " (" + village.getTitle() + ")");
        System.out.println("  Districts: " + village.getDistricts().size());
        for (var d : village.getDistricts()) {
            System.out.println("    - " + d.getName() + ": " + d.getPlaces().size() + " places");
        }
        System.out.println("=".repeat(80) + "\n");

        assertTrue(totalHexGrids > 0, "Should have generated some hexGrids");

        log.info("=== Village with District Composition Test Successful ===");
    }

    /**
     * Load test instruction from resources.
     */
    private String loadTestInstruction() throws Exception {
        ClassPathResource resource = new ClassPathResource(TEST_INSTRUCTION_FILE);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Setup WDocumentService mock for in-memory storage.
     */
    private void setupDocumentServiceMock() {
        // Mock findByName - retrieve document from storage
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

        // Mock save - store document in storage
        when(documentService.save(any(WorldId.class), any(String.class), any(String.class), any()))
                .thenAnswer(invocation -> {
                    WorldId worldId = invocation.getArgument(0);
                    String collection = invocation.getArgument(1);
                    String documentId = invocation.getArgument(2);
                    java.util.function.Consumer<WDocument> updater = invocation.getArgument(3);

                    // Create new document
                    WDocument document = WDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .worldId(worldId.getId())
                            .collection(collection)
                            .documentId(documentId)
                            .build();
                    document.touchCreate();

                    // Apply updater
                    updater.accept(document);
                    document.touchUpdate();

                    // Store in memory
                    String key = buildStorageKey(worldId.getId(), collection, document.getName());
                    documentStorage.put(key, document);

                    log.debug("Mock save: worldId={}, collection={}, documentId={}, name={}",
                            worldId, collection, documentId, document.getName());

                    return document;
                });
    }

    /**
     * Build storage key for document.
     */
    private String buildStorageKey(String worldId, String collection, String name) {
        return worldId + "/" + collection + "/" + name;
    }
}
