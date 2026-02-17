package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.workflow.WorkflowJobExecutor;
import de.mhus.nimbus.world.shared.workflow.WorkflowService;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Genesis Workflow - Orchestrates complete world generation from instructions.
 *
 * Workflow Steps:
 * 1. Generate world name from instructions using AI
 * 2. Validate world name (only a-zA-Z0-9_, NO '-')
 * 3. Check if world doesn't already exist
 * 4. Create worldId from regionId:worldName
 * 5. Save instructions as document in 'generator_instructions' collection
 * 6. Execute Day1WorldCreate workflow to create world
 * 7. Execute Day2Planning workflow to plan world composition
 * 8. Execute Day3Generation workflow to generate terrain
 *
 * Parameters:
 * - instructions: Textual description of the world to generate
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenesisWorkflow extends MethodBasedWorkflow {

    private static final String INSTRUCTIONS_COLLECTION = "generator_instructions";
    private static final String PARAM_INSTRUCTIONS = "instructions";

    private final AiModelService aiModelService;
    private final WWorldService worldService;
    private final WDocumentService documentService;
    private final LocationService locationService;
    private final WorkflowService workflowService;

    @Override
    public String name() {
        return "genesis";
    }

    @Override
    public Map<String, Object> initialize(String contextWorldId, Map<String, String> params) throws WorkflowException {
        // Validate instructions parameter
        var instructions = params.get(PARAM_INSTRUCTIONS);
        if (Strings.isBlank(instructions)) {
            throw new WorkflowException(null, "Parameter 'instructions' is required");
        }

        return Map.of(
                PARAM_INSTRUCTIONS, instructions
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.updateWorkflowStatus("generateWorldName");

        String instructions = (String) context.getParameters().get(PARAM_INSTRUCTIONS);

        // Get region from context worldId
        WorldId contextWorld = WorldId.of(context.getWorldId())
            .orElseThrow(() -> new WorkflowException(null, "Invalid context worldId: " + context.getWorldId()));
        String regionId = contextWorld.getCollectionRegion();

        // Try to generate a valid and unique world name
        String worldName = null;
        String newWorldId = null;
        final int maxAttempts = 10;
        final List<String> rejectedNames = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("Generating world name, attempt {}/{}", attempt, maxAttempts);

            // Generate world name using AI
            String candidateName = generateWorldName(instructions, rejectedNames, attempt);

            // Validate world name
            if (!isValidWorldName(candidateName)) {
                log.warn("Generated world name contains invalid characters: {}", candidateName);
                rejectedNames.add(candidateName);
                continue;
            }

            // Create worldId and check if it exists
            String candidateWorldId = regionId + ":" + candidateName;
            if (worldService.existsWorld(candidateWorldId)) {
                log.warn("World already exists: {}", candidateWorldId);
                rejectedNames.add(candidateName);
                continue;
            }

            // Found a valid and unique name!
            worldName = candidateName;
            newWorldId = candidateWorldId;
            log.info("Successfully generated world name: {} -> worldId: {}", worldName, newWorldId);
            break;
        }

        // Check if we found a valid name
        if (worldName == null) {
            throw new WorkflowException(null,
                "Failed to generate a valid and unique world name after " + maxAttempts + " attempts. " +
                "Rejected names: " + String.join(", ", rejectedNames));
        }

        // Make variables effectively final for use in lambdas
        final String finalWorldName = worldName;
        final String finalNewWorldId = newWorldId;

        // Store worldId and worldName in journal
        context.addRecord(new NewWorldIdRecord(finalNewWorldId));
        context.addRecord(new WorldNameRecord(finalWorldName));

        // Save instructions as document in the NEW world
        WorldId newWorld = WorldId.of(finalNewWorldId)
            .orElseThrow(() -> new WorkflowException(null, "Failed to create WorldId: " + finalNewWorldId));

        String instructionsDocId = UUID.randomUUID().toString();
        documentService.save(newWorld, INSTRUCTIONS_COLLECTION, instructionsDocId, doc -> {
            doc.setName("instructions-" + finalWorldName);
            doc.setTitle("Generation Instructions for " + finalWorldName);
            doc.setContent(instructions);
        });
        log.info("Saved instructions document: {}", instructionsDocId);

        // Store instructions document ID in journal
        context.addRecord(new InstructionsDocIdRecord(instructionsDocId));

        // Start Day1: Create World
        context.updateWorkflowStatus("day1WorldCreate");
        context.enqueueJob(
            WorkflowJobExecutor.NAME,
            "genesis-day1-world-create",
            locationService.getApplicationServiceName(),
            "Day1: Create World " + finalWorldName,
            Map.of(GenesisConst.WORLD_ID, finalNewWorldId)
        );
    }

    @OnSuccess("day1WorldCreate")
    public void onDay1Success(WorkflowContext context) throws WorkflowException {
        log.info("Day1 completed successfully");

        String newWorldId = context.getLastJournalRecord(NewWorldIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "newWorldId not found"))
                .getValue();

        log.info("Migrate workflow {} to the new worldId {}", context.getWorkflowId(), newWorldId);
        workflowService.emigrateToWorld(context, newWorldId, "migrateToNewWorld");
    }

    @OnSuccess("migrateToNewWorld")
    public void onMigrateToNewWorldSuccess(WorkflowContext context) throws WorkflowException {

        log.info("Starting Day2 in world: {}", context.getWorldId());

        String instructionsDocId = context.getLastJournalRecord(InstructionsDocIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "instructionsDocId not found"))
                .getValue();

        // Start Day2: Planning (in context of NEW world)
        // location format: serviceName or serviceName:worldId
        context.updateWorkflowStatus("day2Planning");
        context.enqueueJob(
            WorkflowJobExecutor.NAME,
            "genesis-day2-planning",
            locationService.getApplicationServiceName(),
            "Day2: Planning",
            Map.of(GenesisConst.INSTRUCTIONS_DOCUMENT_ID, instructionsDocId)
        );
    }

    @OnSuccess("day2Planning")
    public void onDay2Success(WorkflowContext context) throws WorkflowException {
        log.info("Day2 completed successfully");

        // Extract composition document ID from Day2 result
        String compositionDocId = context.getJobResultString("documentId")
                .orElseThrow(() -> new WorkflowException(null,
                        "Day2Planning did not return 'documentId' in result"));

        // Store composition document ID in journal
        context.addRecord(new CompositionDocIdRecord(compositionDocId));

        String newWorldId = context.getLastJournalRecord(NewWorldIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "newWorldId not found"))
                .getValue();
        log.info("Starting Day3 in world: {} with composition: {}", newWorldId, compositionDocId);

        // Start Day3: Generation (in context of NEW world)
        // location format: serviceName or serviceName:worldId
        context.updateWorkflowStatus("day3Generation");
        context.enqueueJob(
            WorkflowJobExecutor.NAME,
            "genesis-day3-generation",
            locationService.getApplicationServiceName(),
            "Day3: Generation",
            Map.of(GenesisConst.COMPOSITION_ID, compositionDocId)
        );
    }

    @OnSuccess("day3Generation")
    public void onDay3Success(WorkflowContext context) throws WorkflowException {
        log.info("Day3 completed successfully");

        String newWorldId = context.getLastJournalRecord(NewWorldIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "newWorldId not found"))
                .getValue();
        String worldName = context.getLastJournalRecord(WorldNameRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "worldName not found"))
                .getValue();
        String instructionsDocId = context.getLastJournalRecord(InstructionsDocIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "instructionsDocId not found"))
                .getValue();
        String compositionDocId = context.getLastJournalRecord(CompositionDocIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "compositionDocId not found"))
                .getValue();

        log.info("Genesis workflow completed successfully for world: {} ({})", worldName, newWorldId);

        context.doComplete(Map.of(
                "worldId", newWorldId,
                "worldName", worldName,
                "instructionsDocId", instructionsDocId,
                "compositionDocId", compositionDocId
        ));
    }

    /**
     * Generate a valid world name from instructions using AI.
     * The AI is prompted to create a short, memorable name that follows naming conventions.
     *
     * @param instructions The world generation instructions
     * @param rejectedNames List of previously rejected names to avoid
     * @param attempt Current attempt number
     */
    private String generateWorldName(String instructions, List<String> rejectedNames, int attempt) throws WorkflowException {
        log.info("Generating world name from instructions (length: {} chars), attempt: {}", instructions.length(), attempt);

        // Create AI chat model with slightly higher temperature for retries
        double temperature = 0.7 + (attempt - 1) * 0.05;  // Increase creativity with each attempt
        AiChatOptions options = AiChatOptions.builder()
                .temperature(Math.min(temperature, 1.0))  // Cap at 1.0
                .maxTokens(100)    // Short response - just a name
                .build();

        Optional<AiChat> chatOpt = aiModelService.createChat("default:chat", options);
        if (chatOpt.isEmpty()) {
            throw new WorkflowException(null,
                "AI model not available. Cannot generate world name.");
        }

        AiChat chat = chatOpt.get();

        // Build prompt with rejected names if any
        String rejectedNamesSection = "";
        if (!rejectedNames.isEmpty()) {
            rejectedNamesSection = """

                IMPORTANT: The following names have already been rejected (either invalid or already exist):
                %s

                Please generate a DIFFERENT name that is unique.
                """.formatted(String.join(", ", rejectedNames));
        }

        String prompt = """
            Based on the following world generation instructions, create a short, memorable world name.

            Requirements:
            - Only use letters (a-z, A-Z), numbers (0-9), and underscores (_)
            - NO hyphens (-) or other special characters
            - Between 3 and 20 characters long
            - Should be descriptive and related to the world's theme
            - Use snake_case or PascalCase style
            %s
            Instructions:
            %s

            Respond with ONLY the world name, nothing else.
            Example good names: "fantasy_realm", "SciFiStation", "medieval_kingdom"
            """.formatted(rejectedNamesSection, instructions);

        // Call AI
        String response;
        try {
            response = chat.ask(prompt);
        } catch (AiChatException e) {
            log.error("AI chat failed during world name generation", e);
            throw new WorkflowException(null, "Failed to generate world name: " + e.getMessage());
        }

        if (Strings.isBlank(response)) {
            throw new WorkflowException(null, "AI returned empty world name");
        }

        // Clean response (remove any surrounding whitespace or quotes)
        String worldName = response.trim()
                .replaceAll("^[\"']|[\"']$", "")  // Remove surrounding quotes
                .replaceAll("\\s+", "_");         // Replace spaces with underscores

        log.info("Generated world name: {}", worldName);
        return worldName;
    }

    /**
     * Validate world name contains only allowed characters.
     * Allowed: a-zA-Z0-9_
     * NOT allowed: - (hyphen) and other special characters
     */
    private boolean isValidWorldName(String worldName) {
        if (Strings.isBlank(worldName)) {
            return false;
        }

        // Check length
        if (worldName.length() < 3 || worldName.length() > 20) {
            log.warn("World name length invalid: {} (must be 3-20 characters)", worldName.length());
            return false;
        }

        // Check characters - only a-zA-Z0-9_ allowed
        if (!worldName.matches("^[a-zA-Z0-9_]+$")) {
            log.warn("World name contains invalid characters: {}", worldName);
            return false;
        }

        return true;
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
        // Cleanup if needed
    }
}
