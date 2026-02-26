package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.CompositionResult;
import de.mhus.nimbus.world.generator.composer.build.HexCompositeBuilder;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.town.StructuresIndex;
import de.mhus.nimbus.world.generator.composer.town.StructuresService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Job executor for applying translated instructions (generating composed world model).
 *
 * Executor name: 'generator-apply-translated-instruction'
 *
 * Required parameters:
 * - translationDocumentId: Document ID of the translated instruction in 'generator_translations' collection
 *
 * Optional parameters:
 * - maxAttempts: Maximum number of composition attempts before giving up (default: 3)
 * - seed: Random seed for composition (optional, defaults to current time)
 * - fillGaps: Whether to fill gaps with ocean/land/coast (default: true)
 * - oceanBorderRings: Number of ocean border rings (default: 1)
 *
 * Output:
 * - success: Document ID where composed model was saved
 * - failure: Error message after all attempts failed
 *
 * The job loads a translated instruction document, applies the composition pipeline
 * (positioning, filling, points, flows), and saves the enriched model to 'generator_composed'.
 * Does NOT generate WFlats - only the HexComposition model with all computed data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApplyTranslatedInstructionJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "generator-apply-translated-instruction";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final WDocumentService documentService;
    private final WWorldService worldService;
    private final StructuresService structuresService;
    private final ObjectMapper objectMapper;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting apply translated instruction job: jobId={}", job.getId());

            // Extract required parameters
            String translationDocumentId = getRequiredParameter(job, "translationDocumentId");

            // Extract optional parameters
            int maxAttempts = getOptionalIntParameter(job, "maxAttempts", DEFAULT_MAX_ATTEMPTS);
            Long seed = getOptionalLongParameter(job, "seed", null);
            boolean fillGaps = getOptionalBooleanParameter(job, "fillGaps", true);
            int oceanBorderRings = getOptionalIntParameter(job, "oceanBorderRings", 1);

            // Validate parameters
            if (maxAttempts < 1 || maxAttempts > 10) {
                throw new JobExecutionException("maxAttempts must be between 1 and 10, got: " + maxAttempts);
            }

            log.info("Applying composition: translationDocumentId={}, maxAttempts={}, seed={}, fillGaps={}, oceanBorderRings={}",
                    translationDocumentId, maxAttempts, seed, fillGaps, oceanBorderRings);

            // Load translated document
            HexComposition composition;
            String originalInstruction;
            String documentName;

            try {
                LoadedDocument loaded = loadTranslatedDocument(job.getWorldId(), translationDocumentId);
                composition = loaded.composition;
                originalInstruction = loaded.originalInstruction;
                documentName = loaded.documentName;

                // Override worldId with the one from job context (defensive programming)
                composition.setWorldId(job.getWorldId());

                log.info("Loaded composition: name='{}', features={}",
                        composition.getName(),
                        composition.getFeatures() != null ? composition.getFeatures().size() : 0);

            } catch (Exception e) {
                log.error("Failed to load translated document", e);
                throw new JobExecutionException("Failed to load document: " + e.getMessage(), e);
            }

            // Get or create World for composition context
            WWorld world = null;
            try {
                WorldId wid = WorldId.of(job.getWorldId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + job.getWorldId()));
                Optional<WWorld> worldOpt = worldService.getByWorldId(wid);

                if (worldOpt.isPresent()) {
                    world = worldOpt.get();
                    log.debug("Using existing world: {}", job.getWorldId());
                } else {
                    log.debug("World not found, composition will use defaults");
                }
            } catch (Exception e) {
                log.warn("Could not load world, using null: {}", e.getMessage());
            }

            // Load structures index from region collection
            StructuresIndex structuresIndex = null;
            try {
                structuresIndex = structuresService.findStructuresForWorldId(job.getWorldId());
                log.info("Loaded StructuresIndex for worldId={}: {} buildings", job.getWorldId(), structuresIndex.getTotalBuildingCount());
            } catch (Exception e) {
                log.warn("Failed to load structures index, continuing without: {}", e.getMessage());
                structuresIndex = new StructuresIndex();
            }

            // Retry loop for composition
            CompositionResult result = null;
            String previousError = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                log.info("Composition attempt {}/{}", attempt, maxAttempts);

                try {
                    // Use provided seed or generate new one
                    long compositionSeed = seed != null ? seed : System.currentTimeMillis();

                    // Apply composition pipeline
                    // WHexGrids will be generated later by GenerateHexGridFromCompositeJobExecutor in Day3
                    result = HexCompositeBuilder.builder()
                            .composition(composition)
                            .worldId(composition.getWorldId())
                            .world(world)
                            .seed(compositionSeed)
                            .fillGaps(fillGaps)
                            .oceanBorderRings(oceanBorderRings)
                            .structuresIndex(structuresIndex)
                            .build()
                            .compose();

                    if (result.isSuccess()) {
                        log.info("Composition successful on attempt {}/{}: totalGrids={}, features={}, flows={}",
                                attempt, maxAttempts,
                                result.getTotalGrids(),
                                composition.getFeatures() != null ? composition.getFeatures().size() : 0,
                                result.getTotalFlows());
                        break;
                    } else {
                        // Log error and prepare for retry
                        String errorMsg = result.getErrorMessage();
                        log.warn("Composition attempt {}/{} failed: {}", attempt, maxAttempts, errorMsg);

                        if (attempt < maxAttempts) {
                            previousError = String.format(
                                    "Attempt %d/%d failed: %s",
                                    attempt, maxAttempts, errorMsg
                            );

                            // Reload composition for retry (it might have been modified)
                            LoadedDocument reloaded = loadTranslatedDocument(job.getWorldId(), translationDocumentId);
                            composition = reloaded.composition;
                        } else {
                            // Last attempt failed
                            String finalError = String.format(
                                    "Composition failed after %d attempts. Final error: %s",
                                    maxAttempts, errorMsg
                            );
                            log.error(finalError);
                            return JobResult.failure(finalError);
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during composition attempt {}/{}", attempt, maxAttempts, e);

                    if (attempt < maxAttempts) {
                        previousError = String.format(
                                "Attempt %d/%d crashed: %s",
                                attempt, maxAttempts, e.getMessage()
                        );

                        // Reload composition for retry
                        try {
                            LoadedDocument reloaded = loadTranslatedDocument(job.getWorldId(), translationDocumentId);
                            composition = reloaded.composition;
                        } catch (Exception reloadEx) {
                            log.error("Failed to reload composition for retry", reloadEx);
                            throw new JobExecutionException("Composition failed and cannot reload: " + e.getMessage(), e);
                        }
                    } else {
                        String finalError = String.format(
                                "Composition failed after %d attempts with exception: %s",
                                maxAttempts, e.getMessage()
                        );
                        return JobResult.failure(finalError);
                    }
                }
            }

            // Check if we have a successful result
            if (result == null || !result.isSuccess()) {
                String error = "Composition failed: No successful result after " + maxAttempts + " attempts";
                log.error(error);
                return JobResult.failure(error);
            }

            // Save composed model to document
            try {
                String outputDocumentId = saveComposedModel(
                        job.getWorldId(),
                        documentName,
                        originalInstruction,
                        composition,
                        result
                );

                log.info("Composed model saved to document: {}", outputDocumentId);

                // Build success result
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("documentId", outputDocumentId);
                resultData.put("totalGrids", result.getTotalGrids());
                resultData.put("totalBiomes", result.getTotalBiomes());
                resultData.put("totalStructures", result.getTotalStructures());
                resultData.put("totalPoints", result.getTotalPoints());
                resultData.put("totalFlows", result.getTotalFlows());
                resultData.put("filledGrids", result.getFilledGrids());
                resultData.put("worldId", composition.getWorldId());
                resultData.put("compositionName", composition.getName());

                if (!result.getWarnings().isEmpty()) {
                    resultData.put("warnings", result.getWarnings());
                }

                return JobResult.success(resultData);

            } catch (Exception e) {
                log.error("Failed to save composed model to document", e);
                throw new JobExecutionException("Composition succeeded but failed to save document: " + e.getMessage(), e);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apply translated instruction job failed", e);
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load translated document and extract composition.
     */
    private LoadedDocument loadTranslatedDocument(String worldId, String documentId) throws Exception {
        // Create WorldId
        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

        // Load document by ID
        Optional<WDocument> docOpt = documentService.findByDocumentId(wid, TranslateInstructionJobExecutor.TRANSLATIONS_COLLECTION, documentId);
        if (docOpt.isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }

        WDocument document = docOpt.get();
        String content = document.getContent();

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Document content is empty: " + documentId);
        }

        // Parse composition directly from document content (no wrapper)
        ObjectMapper compMapper = new ObjectMapper();
        compMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        compMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        compMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        HexComposition composition = compMapper.readValue(content, HexComposition.class);

        if (composition == null) {
            throw new IllegalArgumentException("Failed to parse HexComposition from document");
        }

        // Get instructionsDocumentId from document metadata (not from content)
        String instructionsDocumentId = document.getMetadata() != null
            ? document.getMetadata().get("instructionsDocumentId")
            : "";

        LoadedDocument result = new LoadedDocument();
        result.composition = composition;
        result.originalInstruction = instructionsDocumentId;
        result.documentName = document.getName();

        return result;
    }

    /**
     * Save composed model to document.
     */
    private String saveComposedModel(
            String worldId,
            String sourceDocumentName,
            String originalInstruction,
            HexComposition composition,
            CompositionResult compositionResult
    ) throws Exception {

        // Create WorldId
        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

        // Generate document identifiers (keep same name as source, but in different collection)
        String documentId = UUID.randomUUID().toString();
        String documentName = sourceDocumentName;  // Same name as source document

        // Convert featureHexGridRegistry Map to featureHexGrids List for JSON serialization
        // (Jackson has issues with Map<String, FeatureHexGrid>)
        if (composition.getFeatureHexGridRegistry() != null && !composition.getFeatureHexGridRegistry().isEmpty()) {
            composition.setFeatureHexGrids(new ArrayList<>(composition.getFeatureHexGridRegistry().values()));
            log.info("Converted {} FeatureHexGrids from registry to list for serialization",
                    composition.getFeatureHexGrids().size());
        } else {
            log.warn("No FeatureHexGrids in registry to convert");
        }

        // Serialize composition directly to JSON (no wrapper)
        String compositionJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(composition);

        // Create document metadata (for search/filtering, not part of content)
        Map<String, String> metadata = new HashMap<>();
        metadata.put("composedAt", Instant.now().toString());
        metadata.put("compositionName", composition.getName());
        metadata.put("compositionWorldId", composition.getWorldId());
        metadata.put("totalGrids", String.valueOf(compositionResult.getTotalGrids()));
        metadata.put("totalBiomes", String.valueOf(compositionResult.getTotalBiomes()));
        metadata.put("totalStructures", String.valueOf(compositionResult.getTotalStructures()));
        metadata.put("totalPoints", String.valueOf(compositionResult.getTotalPoints()));
        metadata.put("totalFlows", String.valueOf(compositionResult.getTotalFlows()));
        metadata.put("filledGrids", String.valueOf(compositionResult.getFilledGrids()));
        metadata.put("sourceDocumentName", sourceDocumentName);
        metadata.put("instructionsDocumentId", originalInstruction);

        // Save document with composition as direct JSON content
        WDocument document = documentService.save(wid, TranslateInstructionJobExecutor.COMPOSED_COLLECTION, documentId, doc -> {
            doc.setName(documentName);
            doc.setTitle(composition.getName() != null ? composition.getName() : "Composed World");
            doc.setFormat("json");
            doc.setContent(compositionJson);  // Direct model JSON
            doc.setMetadata(metadata);
            doc.setType("composer-composed");
            doc.setReadOnly(false);
        });

        log.info("Saved composed model document: worldId={}, collection={}, documentId={}, name={}",
                worldId, TranslateInstructionJobExecutor.COMPOSED_COLLECTION, documentId, documentName);

        // Return document ID
        return documentId;
    }

    // Helper methods for parameter extraction

    private String getRequiredParameter(WJob job, String paramName) throws JobExecutionException {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + paramName);
        }
        return value;
    }

    private int getOptionalIntParameter(WJob job, String paramName, int defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", paramName, value, defaultValue);
            return defaultValue;
        }
    }

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

    private boolean getOptionalBooleanParameter(WJob job, String paramName, boolean defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Helper class for loaded document data.
     */
    private static class LoadedDocument {
        HexComposition composition;
        String originalInstruction;
        String documentName;
    }
}
