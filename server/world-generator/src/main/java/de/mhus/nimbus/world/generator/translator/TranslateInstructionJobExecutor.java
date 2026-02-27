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
 * - instructionsDocumentId: Id to the Textual world description to translate document in the collection 'generator_instructions'.
 *   This document should contain the textual instructions to be translated.
 *
 * Optional parameters:
 * - documentPath: Path where to save the translated document, will be completed with a timestamp
 * - maxAttempts: Maximum number of translation attempts before giving up (default: 5)
 *
 * Output:
 * - success: Document Id where translation was saved
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

    public static final String TRANSLATIONS_COLLECTION = "generator_translations";
    public static final String INSTRUCTIONS_COLLECTION = "generator_instructions";
    public static final String COMPOSED_COLLECTION = "generator_composed";

    private static final String EXECUTOR_NAME = "generator-translate-instruction";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String DEFAULT_DOCUMENT_PATH = "translation";

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
            String instructionsDocumentId = getRequiredParameter(job, "instructionsId");

            // Extract optional parameters
            int maxAttempts = getOptionalIntParameter(job, "maxAttempts", DEFAULT_MAX_ATTEMPTS);
            String documentName = getOptionalParameter(job, "documentPath", DEFAULT_DOCUMENT_PATH);

            // Validate parameters
            if (maxAttempts < 1 || maxAttempts > 10) {
                throw new JobExecutionException("maxAttempts must be between 1 and 10, got: " + maxAttempts);
            }

            log.info("Translating instruction: documentId={}, resultPath={}, maxAttempts={}",
                    instructionsDocumentId, documentName, maxAttempts);

            String instructions = loadInstructions(job.getWorldId(), instructionsDocumentId);

            // Build translator context once per job (flora/fauna options from region)
            TranslatorContext translatorContext = translatorService.buildTranslatorContext(job.getWorldId());

            // Retry loop
            String previousError = null;
            CompositionResult result = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                log.info("Translation attempt {}/{}", attempt, maxAttempts);

                try {
                    // Attempt translation
                    result = translatorService.translateInstructionToComposite(instructions, previousError, translatorContext);

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
                String documentIdOut = saveTranslationToDocument(
                        job.getWorldId(),
                        documentName,
                        instructionsDocumentId,
                        result.getComposition(),
                        result.getComposerModelJson()
                );

                log.info("Translation saved to document: {}", documentIdOut);

                // Build success result
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("documentId", documentIdOut);
                resultData.put("featuresCount", result.getComposition().getFeatures() != null ?
                        result.getComposition().getFeatures().size() : 0);
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

    private String loadInstructions(String worldId, String instructionDocumentId) {

        // Create WorldId
        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

        return documentService.findByDocumentId(wid, INSTRUCTIONS_COLLECTION, instructionDocumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Instructions document not found: worldId=%s, collection=%s, documentId=%s",
                                worldId, INSTRUCTIONS_COLLECTION, instructionDocumentId)))
                .getContent();
    }

    /**
     * Save translation result to a WDocument.
     * Creates a new document with timestamp to preserve history.
     */
    private String saveTranslationToDocument(
            String worldId,
            String documentName,
            String instructionsDocumentId,
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
        String finalDocumentName = String.format("%s-%s", documentName, timestamp);

        // Create document metadata
        // Save document with composition as direct JSON content
        Map<String, String> metadata = new HashMap<>();
        metadata.put("generatedAt", now.toString());
        metadata.put("compositionName", composition.getName());
        metadata.put("compositionWorldId", composition.getWorldId());
        metadata.put("featuresCount", String.valueOf(
                composition.getFeatures() != null ? composition.getFeatures().size() : 0));
        metadata.put("instructionsDocumentId", instructionsDocumentId);

        WDocument document = documentService.save(wid, TRANSLATIONS_COLLECTION, documentId, doc -> {
            doc.setName(finalDocumentName);
            doc.setTitle(composition.getName() != null ? composition.getName() : "Generated World");
            doc.setFormat("json");
            doc.setContent(json);  // Direct model JSON
            doc.setMetadata(metadata);
            doc.setType("composer-translation");
            doc.setReadOnly(false);
        });

        log.info("Saved translation document: worldId={}, collection={}, documentId={}, name={}",
                worldId, documentName, documentId, finalDocumentName);

        // Return document id
        return documentId;
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
