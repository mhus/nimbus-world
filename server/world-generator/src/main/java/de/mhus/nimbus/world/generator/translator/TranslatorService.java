package de.mhus.nimbus.world.generator.translator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.fauna.FaunaIndex;
import de.mhus.nimbus.world.generator.flora.FloraIndex;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for translating textual instructions into Composer Model JSON format.
 * Uses AI to understand natural language descriptions and convert them to structured world definitions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslatorService {

    private static final String COMPOSER_MODEL_DESCRIPTION_DOCUMENT_NAME = "composer-model-description.md";
    private static final String COMPOSER_MODEL_README_DOCUMENT_NAME = "composer-model-readme.md";
    private static final String LESSONS_LEARNED_DOCUMENT_NAME = "composer-model-lessons-learned.md";
    private static final String DOCUMENT_COLLECTION = "generator";
    private static final String SHARED_WORLD_ID = "@shared:n";
    private static final String PROMPT_TEMPLATE_PATH = "prompts/translator/translate-instruction.txt";

    private final AiModelService aiModelService;
    private final WDocumentService documentService;
    private final WAnythingService anythingService;
    private final ObjectMapper objectMapper;

    // Cache for loaded composer model description
    private String cachedComposerModelDescription;
    // Cache for loaded composer model readme
    private String cachedComposerModelReadme;
    // Cache for loaded lessons learned
    private String cachedLessonsLearned;
    // Cache for loaded prompt template
    private String cachedPromptTemplate;

    /**
     * Load the Composer Model description document from the shared world collection.
     * The document is loaded from worldId '@shared:n' in collection 'generator'.
     * Results are cached for performance.
     *
     * @return Composer Model description as string, or empty if not found
     */
    public void loadComposerModelDescription() {


        try {
            // Create WorldId for @shared:n
            WorldId sharedWorldId = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                    .orElseThrow(() -> new IllegalStateException("Failed to create shared WorldId"));

            if (cachedComposerModelDescription == null) {
                log.info("Loading Composer Model description from WDocumentService");
                // Load document by name
                Optional<WDocument> documentDescriptionOpt = documentService.findByName(
                        sharedWorldId,
                        DOCUMENT_COLLECTION,
                        COMPOSER_MODEL_DESCRIPTION_DOCUMENT_NAME
                );

                if (documentDescriptionOpt.isPresent()) {
                    WDocument document = documentDescriptionOpt.get();
                    String content = document.getContent();

                    if (content == null || content.isBlank()) {
                        log.warn("Composer Model description document found but content is empty");
                    } else {
                        // Cache the content
                        cachedComposerModelDescription = content;
                        log.info("Successfully loaded Composer Model description ({} characters)", content.length());
                    }
                } else {
                    log.warn("Composer Model description document not found: worldId={}, collection={}, name={}",
                            SHARED_WORLD_ID, DOCUMENT_COLLECTION, COMPOSER_MODEL_DESCRIPTION_DOCUMENT_NAME);
                }
            }

            if (cachedComposerModelReadme == null) {
                log.info("Loading Composer Model readme from WDocumentService");
                // Load document by name
                Optional<WDocument> documentReadmeOpt = documentService.findByName(
                        sharedWorldId,
                        DOCUMENT_COLLECTION,
                        COMPOSER_MODEL_README_DOCUMENT_NAME
                );
                if (documentReadmeOpt.isPresent()) {
                    WDocument document = documentReadmeOpt.get();
                    String content = document.getContent();

                    if (content == null || content.isBlank()) {
                        log.warn("Composer Model readme document found but content is empty");
                    } else {
                        // Cache the content
                        cachedComposerModelReadme = content;
                        log.info("Successfully loaded Composer Model readme ({} characters)", content.length());
                    }
                } else {
                    log.warn("Composer Model readme document not found: worldId={}, collection={}, name={}",
                            SHARED_WORLD_ID, DOCUMENT_COLLECTION, COMPOSER_MODEL_README_DOCUMENT_NAME);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load Composer Model description", e);
        }
    }

    /**
     * Create an AI chat model for translation tasks.
     * The model is configured with appropriate settings for code generation.
     *
     * @param modelName Full model name (e.g., "openai:gpt-4", "gemini:gemini-pro")
     * @return AI chat instance, or empty if model cannot be created
     */
    public Optional<AiChat> createTranslatorChatModel(String modelName) {
        log.info("Creating translator chat model: {}", modelName);

        try {
            // Create chat options optimized for translation
            AiChatOptions options = AiChatOptions.builder()
                    .temperature(0.2)  // Low temperature for deterministic, structured output
                    .maxTokens(0)      // Use model maximum for large JSON outputs
                    .build();

            Optional<AiChat> chatOpt = aiModelService.createChat(modelName, options);

            if (chatOpt.isPresent()) {
                log.info("Successfully created translator chat model: {}", chatOpt.get().getName());
                return chatOpt;
            } else {
                log.warn("Failed to create chat model: {}", modelName);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("Error creating translator chat model: {}", modelName, e);
            return Optional.empty();
        }
    }

    /**
     * Create an AI chat model using default model configuration.
     * Attempts to use "default:translator" first, falling back to "default:chat".
     *
     * @return AI chat instance, or empty if no model can be created
     */
    public Optional<AiChat> createDefaultTranslatorChatModel() {
        log.info("Creating default translator chat model");

        // Try "default:translator" first (can be configured via AiModelService mappings)
        Optional<AiChat> chatOpt = createTranslatorChatModel("default:translator");
        if (chatOpt.isPresent()) {
            return chatOpt;
        }

        // Fallback to "default:chat"
        log.info("Fallback to default:chat");
        return createTranslatorChatModel("default:chat");
    }

    /**
     * Load the Lessons Learned document from the shared world collection.
     * The document is loaded from worldId '@shared:n' in collection 'generator'.
     * Results are cached for performance.
     *
     * @return Lessons learned as string, or empty if not found
     */
    public Optional<String> loadLessonsLearned() {
        // Return cached version if available
        if (cachedLessonsLearned != null) {
            log.debug("Returning cached Lessons Learned");
            return Optional.of(cachedLessonsLearned);
        }

        log.info("Loading Lessons Learned from WDocumentService");

        try {
            // Create WorldId for @shared:n
            WorldId sharedWorldId = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                    .orElseThrow(() -> new IllegalStateException("Failed to create shared WorldId"));

            // Load document by name
            Optional<WDocument> documentOpt = documentService.findByName(
                    sharedWorldId,
                    DOCUMENT_COLLECTION,
                    LESSONS_LEARNED_DOCUMENT_NAME
            );

            if (documentOpt.isPresent()) {
                WDocument document = documentOpt.get();
                String content = document.getContent();

                if (content == null || content.isBlank()) {
                    log.warn("Lessons Learned document found but content is empty");
                    return Optional.empty();
                }

                // Cache the content
                cachedLessonsLearned = content;
                log.info("Successfully loaded Lessons Learned ({} characters)", content.length());
                return Optional.of(content);
            } else {
                log.info("Lessons Learned document not found (optional): worldId={}, collection={}, name={}",
                        SHARED_WORLD_ID, DOCUMENT_COLLECTION, LESSONS_LEARNED_DOCUMENT_NAME);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.warn("Failed to load Lessons Learned (optional document)", e);
            return Optional.empty();
        }
    }

    /**
     * Clear the cached Composer Model description and Lessons Learned.
     * Useful after document updates to force reload.
     */
    public void clearCache() {
        log.info("Clearing cached Composer Model description and Lessons Learned");
        cachedComposerModelDescription = null;
        cachedComposerModelReadme = null;
        cachedLessonsLearned = null;
    }

    /**
     * Build a TranslatorContext for the given worldId.
     * Loads FloraIndex and FaunaIndex from the region's WAnything entries.
     *
     * @param worldId The world ID to resolve region from
     * @return TranslatorContext with flora and fauna indices
     */
    public TranslatorContext buildTranslatorContext(String worldId) {
        try {
            WorldId wid = WorldId.of(worldId).orElseThrow();
            WorldId regionWorldId = wid.toRegionCollection();
            String regionCollectionId = regionWorldId.getId();

            log.info("Building TranslatorContext for worldId={}, regionCollectionId={}", worldId, regionCollectionId);

            List<WAnything> floraEntities = anythingService.findByWorldIdAndCollection(regionCollectionId, "flora");
            List<WAnything> faunaEntities = anythingService.findByWorldIdAndCollection(regionCollectionId, "fauna");

            FloraIndex floraIndex = new FloraIndex(floraEntities);
            FaunaIndex faunaIndex = new FaunaIndex(faunaEntities);

            return TranslatorContext.builder()
                    .floraIndex(floraIndex)
                    .faunaIndex(faunaIndex)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to build TranslatorContext for worldId={}, proceeding without context", worldId, e);
            return TranslatorContext.builder().build();
        }
    }

    /**
     * Translate textual instructions into Composer Model JSON format.
     * Optionally includes error feedback from a previous translation attempt.
     *
     * @param instruction Textual world description
     * @param previousError Error message from previous translation attempt (optional)
     * @return Translation result with JSON or errors
     */
    public TranslationResult translateInstruction(String instruction, String previousError) {
        return translateInstruction(instruction, previousError, null);
    }

    /**
     * Translate textual instructions into Composer Model JSON format.
     * Optionally includes error feedback from a previous translation attempt
     * and a translator context with flora/fauna options.
     *
     * @param instruction Textual world description
     * @param previousError Error message from previous translation attempt (optional)
     * @param context TranslatorContext with flora/fauna indices (optional)
     * @return Translation result with JSON or errors
     */
    public TranslationResult translateInstruction(String instruction, String previousError, TranslatorContext context) {
        log.info("Translating instruction (length: {} chars, has previous error: {})",
                instruction != null ? instruction.length() : 0,
                previousError != null);

        // Validate input
        if (Strings.isBlank(instruction)) {
            return TranslationResult.failure("Instruction cannot be empty");
        }

        try {
            // 1. Load Composer Model description and readme
            loadComposerModelDescription();

            // 2. Load Lessons Learned (optional)
            Optional<String> lessonsLearnedOpt = loadLessonsLearned();
            String lessonsLearned = lessonsLearnedOpt.orElse("");
            if (!lessonsLearned.isEmpty()) {
                log.info("Loaded Lessons Learned ({} characters)", lessonsLearned.length());
            }

            // 3. Load prompt template
            Optional<String> promptTemplateOpt = loadPromptTemplate();
            if (promptTemplateOpt.isEmpty()) {
                return TranslationResult.failure("Prompt template could not be loaded");
            }
            String promptTemplateText = promptTemplateOpt.get();

            // 3. Create chat model
            Optional<AiChat> chatOpt = createDefaultTranslatorChatModel();
            if (chatOpt.isEmpty()) {
                return TranslationResult.failure(
                        "AI chat model not available. " +
                        "Please configure an AI model via AiModelService.");
            }
            AiChat chat = chatOpt.get();

            // 4. Build prompt with template
            PromptTemplate template = PromptTemplate.from(promptTemplateText);
            Map<String, Object> variables = new HashMap<>();
            variables.put("composerModelDescription", cachedComposerModelDescription == null ? "" : cachedComposerModelDescription);
            variables.put("composerModelReadme", cachedComposerModelReadme == null ? "" : cachedComposerModelReadme);
            variables.put("instruction", instruction);

            // Add flora/fauna options section if context is available
            String floraFaunaOptions = "";
            if (context != null) {
                floraFaunaOptions = context.toPromptSection();
            }
            variables.put("floraFaunaOptions", floraFaunaOptions);

            // Add lessons learned section if available
            String lessonsLearnedSection = "";
            if (!lessonsLearned.isEmpty()) {
                lessonsLearnedSection = """
                        ## Lessons Learned - Common Errors to Avoid

                        %s
                        """.formatted(lessonsLearned);
            }
            variables.put("lessonsLearnedSection", lessonsLearnedSection);

            // Build previousError section
            String previousErrorSection = "";
            if (previousError != null && !previousError.isBlank()) {
                previousErrorSection = """
                        ## Previous Translation Attempt

                        The previous translation attempt resulted in the following errors:

                        %s

                        Please fix these errors in your new translation.
                        """.formatted(previousError);
            }
            variables.put("previousErrorSection", previousErrorSection);

            Prompt prompt = template.apply(variables);
            String promptText = prompt.text();

            log.debug("Generated prompt (length: {} chars)", promptText.length());

            // 5. Call AI model
            String response;
            try {
                response = chat.ask(promptText);
            } catch (AiChatException e) {
                log.error("AI chat failed", e);
                return TranslationResult.failure("AI chat error: " + e.getMessage());
            }

            if (Strings.isBlank(response)) {
                return TranslationResult.failure("AI returned empty response");
            }

            log.debug("Received AI response (length: {} chars)", response.length());

            // 6. Clean response (remove markdown code blocks if present)
            String cleanedJson = cleanJsonResponse(response);

            // 7. Validate JSON
            try {
                // Try to parse to ensure it's valid JSON
                objectMapper.readTree(cleanedJson);
                log.info("Successfully translated instruction to valid JSON");
                return TranslationResult.success(cleanedJson);
            } catch (Exception e) {
                log.warn("Generated JSON is invalid", e);
                String errorMessage = "Generated JSON is invalid: " + e.getMessage() + "\n\nGenerated content:\n" + cleanedJson;

                // Update Lessons Learned with this error
                try {
                    updateLessonsLearnedWithError(errorMessage, instruction, cleanedJson);
                } catch (Exception lessonsEx) {
                    log.warn("Failed to update Lessons Learned", lessonsEx);
                }

                return TranslationResult.failure(errorMessage);
            }

        } catch (Exception e) {
            log.error("Unexpected error during translation", e);
            return TranslationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Load the prompt template from resources.
     * Results are cached for performance.
     *
     * @return Prompt template as string, or empty if not found
     */
    private Optional<String> loadPromptTemplate() {
        // Return cached version if available
        if (cachedPromptTemplate != null) {
            log.debug("Returning cached prompt template");
            return Optional.of(cachedPromptTemplate);
        }

        log.info("Loading prompt template from: {}", PROMPT_TEMPLATE_PATH);

        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            if (!resource.exists()) {
                log.error("Prompt template not found at: {}", PROMPT_TEMPLATE_PATH);
                return Optional.empty();
            }

            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            cachedPromptTemplate = template;
            log.info("Successfully loaded prompt template ({} characters)", template.length());
            return Optional.of(template);

        } catch (IOException e) {
            log.error("Failed to load prompt template", e);
            return Optional.empty();
        }
    }

    /**
     * Translate textual instructions into HexComposition object.
     * This method first translates the instruction to JSON, then parses it to HexComposition.
     * Does NOT arrange or build the composition - only parses the model structure.
     *
     * @param instruction Textual world description
     * @return Composition result with HexComposition object or errors
     */
    public CompositionResult translateInstructionToComposite(String instruction) {
        return translateInstructionToComposite(instruction, null, null);
    }

    /**
     * Translate textual instructions into HexComposition object.
     * Optionally includes error feedback from a previous translation attempt.
     * Does NOT arrange or build the composition - only parses the model structure.
     *
     * @param instructions Textual world description
     * @param previousError Error message from previous translation attempt (optional)
     * @return Composition result with HexComposition object or errors
     */
    public CompositionResult translateInstructionToComposite(String instructions, String previousError) {
        return translateInstructionToComposite(instructions, previousError, null);
    }

    /**
     * Translate textual instructions into HexComposition object.
     * Optionally includes error feedback from a previous translation attempt
     * and a translator context with flora/fauna options.
     * Does NOT arrange or build the composition - only parses the model structure.
     *
     * @param instructions Textual world description
     * @param previousError Error message from previous translation attempt (optional)
     * @param context TranslatorContext with flora/fauna indices (optional)
     * @return Composition result with HexComposition object or errors
     */
    public CompositionResult translateInstructionToComposite(String instructions, String previousError, TranslatorContext context) {
        log.info("Translating instruction to HexComposition (length: {} chars, has previous error: {}, has context: {})",
                instructions != null ? instructions.length() : 0,
                previousError != null,
                context != null);

        // Step 1: Translate to JSON
        TranslationResult translationResult = translateInstruction(instructions, previousError, context);

        if (translationResult.hasFailed()) {
            log.warn("Translation to JSON failed with {} errors", translationResult.getErrors().size());
            return CompositionResult.failure(translationResult.getErrors());
        }

        String json = translationResult.getComposerModelJson();
        log.debug("Successfully translated to JSON ({} characters)", json.length());

        // Step 2: Parse JSON to HexComposition
        try {
            // Configure ObjectMapper to allow comments (like in the test)
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);

            // Parse JSON to HexComposition
            HexComposition composition = mapper.readValue(json, HexComposition.class);

            if (composition == null) {
                log.error("ObjectMapper returned null composition");
                return CompositionResult.failure("Failed to parse JSON: ObjectMapper returned null", json);
            }

            log.info("Successfully parsed HexComposition: name='{}', worldId='{}', features={}",
                    composition.getName(),
                    composition.getWorldId(),
                    composition.getFeatures() != null ? composition.getFeatures().size() : 0);

            return CompositionResult.success(composition, json);

        } catch (Exception e) {
            log.error("Failed to parse JSON to HexComposition", e);

            String errorMessage = String.format(
                    "Failed to parse JSON to HexComposition: %s\n\n" +
                    "JSON parsing error: %s\n\n" +
                    "Generated JSON:\n%s",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    json
            );

            // Update Lessons Learned with this error
            try {
                updateLessonsLearnedWithError(errorMessage, instructions, json);
            } catch (Exception lessonsEx) {
                log.warn("Failed to update Lessons Learned", lessonsEx);
            }

            List<String> errors = new ArrayList<>();
            errors.add(errorMessage);
            return CompositionResult.failure(errors, json);
        }
    }

    /**
     * Clean JSON response by removing markdown code blocks and extra whitespace.
     *
     * @param response Raw AI response
     * @return Cleaned JSON string
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length());
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Update the Lessons Learned document with a new error.
     * Uses AI to analyze the error and extend the lessons learned document.
     *
     * @param errorMessage The error message to add to lessons learned
     * @param instruction The original instruction that caused the error
     * @param generatedJson The JSON that was generated (if available)
     */
    public void updateLessonsLearnedWithError(String errorMessage, String instruction, String generatedJson) {
        log.info("Updating Lessons Learned with new error");

        try {
            // 1. Load current Lessons Learned document
            Optional<String> currentLessonsOpt = loadLessonsLearned();
            String currentLessons = currentLessonsOpt.orElse("");

            // 2. Create AI chat for analyzing the error
            Optional<AiChat> chatOpt = createDefaultTranslatorChatModel();
            if (chatOpt.isEmpty()) {
                log.warn("Cannot update Lessons Learned: AI model not available");
                return;
            }
            AiChat chat = chatOpt.get();

            // 3. Build prompt for updating lessons learned
            String updatePrompt = buildLessonsLearnedUpdatePrompt(
                    currentLessons,
                    errorMessage,
                    instruction,
                    generatedJson
            );

            // 4. Ask AI to create updated version
            String updatedLessons;
            try {
                updatedLessons = chat.ask(updatePrompt);
            } catch (AiChatException e) {
                log.error("Failed to get AI response for lessons learned update", e);
                return;
            }

            // 5. Clean response (remove markdown if present)
            updatedLessons = cleanMarkdownResponse(updatedLessons);

            // 6. Validate that updated lessons is not empty or suspiciously short
            if (updatedLessons.isBlank()) {
                log.error("AI returned empty lessons learned document - not updating");
                return;
            }

            if (updatedLessons.length() < 100) {
                log.warn("Updated lessons learned suspiciously short ({} chars) - not updating", updatedLessons.length());
                return;
            }

            // 7. Save updated lessons learned
            saveLessonsLearned(updatedLessons);

            log.info("Successfully updated Lessons Learned document (new length: {} chars)", updatedLessons.length());

        } catch (Exception e) {
            log.error("Failed to update Lessons Learned", e);
        }
    }

    /**
     * Build prompt for updating lessons learned document.
     */
    private String buildLessonsLearnedUpdatePrompt(
            String currentLessons,
            String errorMessage,
            String instruction,
            String generatedJson) {

        String currentLessonsSection = currentLessons.isBlank()
                ? "No previous lessons learned available yet."
                : currentLessons;

        String generatedJsonSection = (generatedJson != null && !generatedJson.isBlank())
                ? "\n\n## Generated JSON\n\n```json\n" + generatedJson + "\n```"
                : "";

        return String.format("""
                You are an expert system that helps improve the Composer Model translation process by maintaining
                a "Lessons Learned" document. This document captures common errors and pitfalls to help avoid them
                in future translations.

                ## Current Lessons Learned Document

                %s

                ## New Error to Analyze

                **Original Instruction:**
                %s

                **Error Message:**
                %s%s

                ## Your Task

                Analyze this error and update the Lessons Learned document:

                1. Identify the root cause of this error
                2. Extract a general lesson that applies beyond this specific case
                3. Add this lesson to the document in a clear, actionable format
                4. Keep existing lessons unless they are outdated or redundant
                5. Organize lessons by category (e.g., JSON structure, naming conventions, data types, etc.)
                6. Make lessons concise but specific enough to be helpful

                ## Output Format

                Return ONLY the updated Lessons Learned document in markdown format.
                Do NOT include any explanation or meta-commentary.
                Do NOT wrap the output in code blocks.

                The document should start with "# Composer Model - Lessons Learned" as the main heading.
                """,
                currentLessonsSection,
                instruction,
                errorMessage,
                generatedJsonSection
        );
    }

    /**
     * Clean markdown response by removing code blocks.
     */
    private String cleanMarkdownResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```markdown")) {
            cleaned = cleaned.substring("```markdown".length());
        } else if (cleaned.startsWith("```md")) {
            cleaned = cleaned.substring("```md".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length());
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Save updated lessons learned document to the database.
     */
    private void saveLessonsLearned(String content) {
        try {
            WorldId sharedWorldId = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                    .orElseThrow(() -> new IllegalStateException("Failed to create shared WorldId"));

            // Check if document exists
            Optional<WDocument> existingDocOpt = documentService.findByName(
                    sharedWorldId,
                    DOCUMENT_COLLECTION,
                    LESSONS_LEARNED_DOCUMENT_NAME
            );

            if (existingDocOpt.isPresent()) {
                // Update existing document
                WDocument existingDoc = existingDocOpt.get();
                documentService.save(sharedWorldId, DOCUMENT_COLLECTION, existingDoc.getDocumentId(), doc -> {
                    doc.setContent(content);
                });
                log.info("Updated existing Lessons Learned document");
            } else {
                // Create new document
                String documentId = java.util.UUID.randomUUID().toString();
                documentService.save(sharedWorldId, DOCUMENT_COLLECTION, documentId, doc -> {
                    doc.setName(LESSONS_LEARNED_DOCUMENT_NAME);
                    doc.setTitle("Composer Model - Lessons Learned");
                    doc.setContent(content);
                    doc.setFormat("markdown");
                    doc.setType("documentation");
                });
                log.info("Created new Lessons Learned document");
            }

            // Clear cache to force reload on next access
            cachedLessonsLearned = null;

        } catch (Exception e) {
            log.error("Failed to save Lessons Learned document", e);
            throw new RuntimeException("Failed to save Lessons Learned", e);
        }
    }
}
