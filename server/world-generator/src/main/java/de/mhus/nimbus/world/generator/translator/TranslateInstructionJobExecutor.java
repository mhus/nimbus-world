package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Job executor for translating textual instructions to Composer Model JSON.
 *
 * Executor name: 'generator-translate-instruction'
 *
 * Required parameters:
 * - instruction: Textual world description to translate
 *
 * Optional parameters:
 * - documentPath: Path/collection where to save the translated document (default: 'generator_translations')
 * - maxAttempts: Maximum number of translation attempts before giving up (default: 5)
 *
 * Output:
 * - success: Document path where translation was saved
 * - failure: Error message after all attempts failed
 *
 * The job automatically retries on translation errors, passing previous error messages
 * to the AI for improvement. Each successful translation is saved as a new document
 * with timestamp to preserve history.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TranslateInstructionJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "generator-translate-instruction";
    private static final String DEFAULT_COLLECTION = "generator_translations";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final TranslatorService translatorService;
    private final WDocumentService documentService;
    private final ObjectMapper objectMapper;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting translate instruction job: jobId={}", job.getId());

            // Extract required parameters
            String instruction = getRequiredParameter(job, "instruction");

            // Extract optional parameters
            String collection = getOptionalParameter(job, "documentPath", DEFAULT_COLLECTION);
            int maxAttempts = getOptionalIntParameter(job, "maxAttempts", DEFAULT_MAX_ATTEMPTS);

            // Validate parameters
            if (maxAttempts < 1 || maxAttempts > 10) {
                throw new JobExecutionException("maxAttempts must be between 1 and 10, got: " + maxAttempts);
            }

            log.info("Translating instruction: length={}, collection={}, maxAttempts={}",
                    instruction.length(), collection, maxAttempts);

            // Retry loop
            String previousError = null;
            CompositionResult result = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                log.info("Translation attempt {}/{}", attempt, maxAttempts);

                try {
                    // Attempt translation
                    result = translatorService.translateInstructionToComposite(instruction, previousError);

                    if (result.isSuccessful()) {
                        // Override worldId with the one from job context (not from instruction/Gemini)
                        result.getComposition().setWorldId(job.getWorldId());
                        log.info("Translation successful on attempt {}/{}", attempt, maxAttempts);
                        break;
                    } else {
                        // Log errors and prepare for retry
                        String errorMsg = String.join("; ", result.getErrors());
                        log.warn("Translation attempt {}/{} failed: {}", attempt, maxAttempts, errorMsg);

                        if (attempt < maxAttempts) {
                            // Prepare error feedback for next attempt
                            previousError = String.format(
                                    "Attempt %d/%d failed with errors:\n%s",
                                    attempt, maxAttempts, errorMsg
                            );
                        } else {
                            // Last attempt failed
                            String finalError = String.format(
                                    "Translation failed after %d attempts. Final errors: %s",
                                    maxAttempts, errorMsg
                            );
                            log.error(finalError);
                            return JobResult.failure(finalError);
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during translation attempt {}/{}", attempt, maxAttempts, e);

                    if (attempt < maxAttempts) {
                        previousError = String.format(
                                "Attempt %d/%d crashed with exception: %s",
                                attempt, maxAttempts, e.getMessage()
                        );
                    } else {
                        String finalError = String.format(
                                "Translation failed after %d attempts with exception: %s",
                                maxAttempts, e.getMessage()
                        );
                        return JobResult.failure(finalError);
                    }
                }
            }

            // Check if we have a successful result
            if (result == null || result.hasFailed()) {
                String error = "Translation failed: No successful result after " + maxAttempts + " attempts";
                log.error(error);
                return JobResult.failure(error);
            }

            // Save successful translation to document
            try {
                String documentPath = saveTranslationToDocument(
                        job.getWorldId(),
                        collection,
                        instruction,
                        result.getComposition(),
                        result.getComposerModelJson()
                );

                log.info("Translation saved to document: {}", documentPath);

                // Build success result
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("documentPath", documentPath);
                resultData.put("featuresCount", result.getComposition().getFeatures() != null ?
                        result.getComposition().getFeatures().size() : 0);
                resultData.put("worldId", result.getComposition().getWorldId());
                resultData.put("compositionName", result.getComposition().getName());

                return JobResult.success(resultData);

            } catch (Exception e) {
                log.error("Failed to save translation to document", e);
                throw new JobExecutionException("Translation succeeded but failed to save document: " + e.getMessage(), e);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Translate instruction job failed", e);
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Save translation result to a WDocument.
     * Creates a new document with timestamp to preserve history.
     */
    private String saveTranslationToDocument(
            String worldId,
            String collection,
            String originalInstruction,
            HexComposition composition,
            String json
    ) throws Exception {

        // Create WorldId
        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

        // Generate unique document name with timestamp
        Instant now = Instant.now();
        String timestamp = TIMESTAMP_FORMATTER.format(now).replaceAll(":", "-");
        String documentId = UUID.randomUUID().toString();
        String documentName = String.format("translation-%s", timestamp);

        // Create document metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("generatedAt", now.toString());
        metadata.put("compositionName", composition.getName());
        metadata.put("compositionWorldId", composition.getWorldId());
        metadata.put("featuresCount", String.valueOf(
                composition.getFeatures() != null ? composition.getFeatures().size() : 0));

        // Build document content with original instruction and JSON
        String content = buildDocumentContent(originalInstruction, json, composition);

        // Save document
        WDocument document = documentService.save(wid, collection, documentId, doc -> {
            doc.setName(documentName);
            doc.setTitle(composition.getName() != null ? composition.getName() : "Generated World");
            doc.setFormat("json");
            doc.setContent(content);
            doc.setMetadata(metadata);
            doc.setType("composer-translation");
            doc.setReadOnly(false);
        });

        log.info("Saved translation document: worldId={}, collection={}, documentId={}, name={}",
                worldId, collection, documentId, documentName);

        // Return document path
        return String.format("%s/%s/%s", worldId, collection, documentName);
    }

    /**
     * Build document content with metadata and JSON.
     */
    private String buildDocumentContent(String instruction, String json, HexComposition composition) throws Exception {
        Map<String, Object> documentContent = new HashMap<>();
        documentContent.put("originalInstruction", instruction);
        documentContent.put("generatedAt", Instant.now().toString());
        documentContent.put("compositionJson", json);

        // Add composition metadata
        Map<String, Object> compositionMeta = new HashMap<>();
        compositionMeta.put("name", composition.getName());
        compositionMeta.put("worldId", composition.getWorldId());
        compositionMeta.put("featuresCount", composition.getFeatures() != null ? composition.getFeatures().size() : 0);
        compositionMeta.put("continentsCount", composition.getContinents() != null ? composition.getContinents().size() : 0);
        documentContent.put("compositionMetadata", compositionMeta);

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(documentContent);
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
     * Get optional integer parameter from job with default value.
     */
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
}
