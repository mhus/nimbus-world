package de.mhus.nimbus.world.generator.document;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Job executor for generating AI-powered summaries for documents.
 *
 * Loads a document's content and uses an AI model to generate a concise summary.
 * The generated summary is saved back to the document's summary field.
 *
 * Required parameters:
 * - worldId: World identifier (can be overridden by job.getWorldId())
 * - collection: Document collection name
 * - documentId: Document identifier
 *
 * Optional parameters:
 * - aiModel: AI model to use (default: "default:chat")
 * - maxTokens: Maximum tokens for AI response (default: 150)
 * - temperature: AI temperature setting (default: 0.7)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentSummaryJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "document-summary";
    private static final String DEFAULT_AI_MODEL = "default:chat";
    private static final int DEFAULT_MAX_TOKENS = 150;
    private static final double DEFAULT_TEMPERATURE = 0.7;

    private final WDocumentService documentService;
    private final AiModelService aiModelService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting document summary generation job: jobId={}", job.getId());

            // Get parameters
            String worldIdStr = getOptionalParameter(job, "worldId", job.getWorldId());
            String collection = getRequiredParameter(job, "collection");
            String documentId = getRequiredParameter(job, "documentId");
            String aiModel = getOptionalParameter(job, "aiModel", DEFAULT_AI_MODEL);
            int maxTokens = getOptionalIntParameter(job, "maxTokens", DEFAULT_MAX_TOKENS);
            double temperature = getOptionalDoubleParameter(job, "temperature", DEFAULT_TEMPERATURE);

            log.info("Parameters: worldId={}, collection={}, documentId={}, aiModel={}, maxTokens={}, temperature={}",
                    worldIdStr, collection, documentId, aiModel, maxTokens, temperature);

            // Parse worldId
            WorldId worldId = WorldId.of(worldIdStr)
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + worldIdStr));

            // Load document
            log.debug("Loading document: worldId={}, collection={}, documentId={}", worldId, collection, documentId);
            Optional<WDocument> docOpt = documentService.findByDocumentId(worldId, collection, documentId);
            if (docOpt.isEmpty()) {
                throw new JobExecutionException("Document not found: " + documentId + " in collection " + collection);
            }

            WDocument document = docOpt.get();
            String content = document.getContent();

            if (content == null || content.isBlank()) {
                log.warn("Document has no content, skipping summary generation: documentId={}", documentId);
                return JobResult.success("Document has no content - summary generation skipped");
            }

            log.info("Document loaded: title='{}', contentLength={}", document.getTitle(), content.length());

            // Generate summary using AI
            log.debug("Creating AI chat with model: {}", aiModel);
            AiChatOptions options = AiChatOptions.builder()
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            Optional<AiChat> chatOpt = aiModelService.createChat(aiModel, options);
            if (chatOpt.isEmpty()) {
                throw new JobExecutionException("Failed to create AI chat with model: " + aiModel);
            }

            AiChat chat = chatOpt.get();
            log.info("AI chat created: {}", chat.getName());

            // Build prompt
            String prompt = buildSummaryPrompt(document.getTitle(), content);
            log.debug("Sending prompt to AI (length: {})", prompt.length());

            // Ask AI for summary
            String summary;
            try {
                summary = chat.ask(prompt);
                log.info("AI response received (length: {})", summary.length());
            } catch (AiChatException e) {
                throw new JobExecutionException("AI chat failed: " + e.getMessage(), e);
            }

            if (summary == null || summary.isBlank()) {
                throw new JobExecutionException("AI returned empty summary");
            }

            // Clean up summary (remove quotes if AI wrapped it)
            String cleanedSummary = cleanSummary(summary);

            // Save summary back to document
            log.debug("Saving summary to document: documentId={}", documentId);
            documentService.update(worldId, collection, documentId, doc -> {
                doc.setSummary(cleanedSummary);
            });

            log.info("Document summary generated successfully: documentId={}, summaryLength={}",
                    documentId, cleanedSummary.length());

            return JobResult.success("Summary generated: " + cleanedSummary.length() + " characters");

        } catch (JobExecutionException e) {
            log.error("Job execution failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during job execution", e);
            throw new JobExecutionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Build a prompt for the AI to generate a summary.
     */
    private String buildSummaryPrompt(String title, String content) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please provide a concise summary (maximum 2-3 sentences) of the following document");

        if (title != null && !title.isBlank()) {
            prompt.append(" titled '").append(title).append("'");
        }

        prompt.append(":\n\n");
        prompt.append(content);
        prompt.append("\n\nSummary:");

        return prompt.toString();
    }

    /**
     * Clean up AI-generated summary.
     * Removes surrounding quotes and extra whitespace.
     */
    private String cleanSummary(String summary) {
        if (summary == null) {
            return null;
        }

        summary = summary.trim();

        // Remove surrounding quotes if present
        if ((summary.startsWith("\"") && summary.endsWith("\"")) ||
            (summary.startsWith("'") && summary.endsWith("'"))) {
            summary = summary.substring(1, summary.length() - 1).trim();
        }

        return summary;
    }

    // Helper methods for parameter extraction

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

    /**
     * Get optional double parameter from job with default value.
     */
    private double getOptionalDoubleParameter(WJob job, String paramName, double defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double parameter '{}': {}, using default: {}", paramName, value, defaultValue);
            return defaultValue;
        }
    }
}
