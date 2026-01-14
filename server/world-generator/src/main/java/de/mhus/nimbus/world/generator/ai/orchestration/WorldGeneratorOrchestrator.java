package de.mhus.nimbus.world.generator.ai.orchestration;

import de.mhus.nimbus.world.generator.ai.agents.*;
import de.mhus.nimbus.world.generator.ai.memory.ChatMemoryId;
import de.mhus.nimbus.world.generator.ai.memory.WChatMemoryStore;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Main orchestrator for the world generator AI system.
 *
 * This orchestrator coordinates multiple agents to handle user requests:
 * - Route requests to appropriate generation type
 * - Collect missing information
 * - Create generation plans
 * - Execute block or flat generation
 * - Format responses
 */
@Slf4j
public class WorldGeneratorOrchestrator {

    private final String worldId;
    private final String sessionId;
    private final String chatId;
    private final String playerId;

    // Individual agents
    private final RouterAgent routerAgent;
    private final QuestionCollectorAgent questionCollectorAgent;
    private final PlannerAgent plannerAgent;
    private final ResponseFormatterAgent responseFormatterAgent;

    // Block generation agents
    private final BlockTypeSearchAgent blockTypeSearchAgent;
    private final LayerPreparationAgent layerPreparationAgent;
    private final BlockGeneratorAgent blockGeneratorAgent;

    // Flat generation agents
    private final FlatValidationAgent flatValidationAgent;
    private final FlatGeneratorAgent flatGeneratorAgent;

    // Memory
    private final WChatMemoryStore memoryStore;
    private final ChatMemoryId memoryId;

    public WorldGeneratorOrchestrator(
            String worldId,
            String sessionId,
            String chatId,
            String playerId,
            RouterAgent routerAgent,
            QuestionCollectorAgent questionCollectorAgent,
            PlannerAgent plannerAgent,
            ResponseFormatterAgent responseFormatterAgent,
            BlockTypeSearchAgent blockTypeSearchAgent,
            LayerPreparationAgent layerPreparationAgent,
            BlockGeneratorAgent blockGeneratorAgent,
            FlatValidationAgent flatValidationAgent,
            FlatGeneratorAgent flatGeneratorAgent,
            WChatMemoryStore memoryStore,
            List<WChatMessage> chatHistory) {

        this.worldId = worldId;
        this.sessionId = sessionId;
        this.chatId = chatId;
        this.playerId = playerId;

        this.routerAgent = routerAgent;
        this.questionCollectorAgent = questionCollectorAgent;
        this.plannerAgent = plannerAgent;
        this.responseFormatterAgent = responseFormatterAgent;

        this.blockTypeSearchAgent = blockTypeSearchAgent;
        this.layerPreparationAgent = layerPreparationAgent;
        this.blockGeneratorAgent = blockGeneratorAgent;

        this.flatValidationAgent = flatValidationAgent;
        this.flatGeneratorAgent = flatGeneratorAgent;

        this.memoryStore = memoryStore;
        this.memoryId = new ChatMemoryId(worldId, chatId);

        // Inject chat history into memory
        if (chatHistory != null && !chatHistory.isEmpty()) {
            memoryStore.injectMessages(memoryId, chatHistory);
        }
    }

    /**
     * Process a user request and return the response.
     */
    public String process(String userRequest) {
        log.info("Processing request for world={}, session={}: {}", worldId, sessionId, userRequest);

        try {
            // Step 1: Route the request
            log.debug("Step 1: Routing request");
            String generationType = routerAgent.routeRequest(userRequest);
            log.info("Request routed to: {}", generationType);

            // Step 2: Check for missing information
            log.debug("Step 2: Checking for missing information");
            String missingInfo = questionCollectorAgent.identifyMissingInfo(
                userRequest, generationType, worldId, sessionId);

            if (!missingInfo.startsWith("READY")) {
                log.info("Missing information detected, returning questions");
                return formatQuestions(missingInfo);
            }

            // Step 3: Execute based on generation type
            log.debug("Step 3: Executing generation workflow");
            String finalResult;

            switch (generationType.trim()) {
                case "BLOCK_GENERATION":
                    log.info("Executing block generation workflow");
                    finalResult = executeBlockWorkflow(userRequest);
                    break;

                case "FLAT_GENERATION":
                    log.info("Executing flat generation workflow");
                    finalResult = executeFlatWorkflow(userRequest);
                    break;

                case "CHAT_RESPONSE":
                    log.info("Providing chat response");
                    finalResult = responseFormatterAgent.formatResponse(
                        userRequest,
                        generationType,
                        "Einfache Chat-Antwort basierend auf der Anfrage.",
                        "",  // No block generation for chat
                        ""   // No flat generation for chat
                    );
                    break;

                default:
                    log.warn("Unknown generation type: {}", generationType);
                    return "Entschuldigung, ich konnte die Art der Anfrage nicht bestimmen.";
            }

            log.info("Request processed successfully");
            return finalResult;

        } catch (Exception e) {
            log.error("Error processing request", e);
            return "Entschuldigung, es ist ein Fehler aufgetreten: " + e.getMessage();
        } finally {
            // Clear session memory
            memoryStore.clearSession(memoryId);
        }
    }

    /**
     * Execute block generation workflow.
     */
    private String executeBlockWorkflow(String userRequest) {
        // Plan generation
        String plan = plannerAgent.createPlan(userRequest, "BLOCK_GENERATION", worldId);

        // Search for block types
        String blockTypes = blockTypeSearchAgent.findBlockTypes(userRequest, plan, worldId);

        // Prepare layer
        String layerInfo = layerPreparationAgent.prepareLayer(userRequest, plan, worldId, sessionId);

        // Generate blocks
        String blockResult = blockGeneratorAgent.generateBlocks(
            plan, blockTypes, layerInfo, worldId, sessionId);

        // Format response
        return responseFormatterAgent.formatResponse(
            userRequest,
            "BLOCK_GENERATION",
            plan,
            blockResult,
            ""  // No flat generation for blocks
        );
    }

    /**
     * Execute flat generation workflow with validation loop.
     */
    private String executeFlatWorkflow(String userRequest) {
        // Plan generation
        String plan = plannerAgent.createPlan(userRequest, "FLAT_GENERATION", worldId);

        // Validation loop (max 3 iterations)
        String validationStatus = null;
        int maxIterations = 3;

        for (int i = 0; i < maxIterations; i++) {
            log.debug("Flat validation iteration {}", i + 1);
            validationStatus = flatValidationAgent.validateFlatGeneration(
                userRequest, plan);

            if ("VALIDATED".equals(validationStatus.trim())) {
                log.info("Flat operations validated on iteration {}", i + 1);
                break;
            }
        }

        if (!"VALIDATED".equals(validationStatus.trim())) {
            log.warn("Flat validation did not pass after {} iterations", maxIterations);
            return "Entschuldigung, die Flat-Operationen konnten nicht validiert werden. " +
                   "Bitte überprüfen Sie Ihre Anfrage.";
        }

        // Generate flat
        String flatResult = flatGeneratorAgent.generateFlat(
            plan, true, worldId);

        // Format response
        return responseFormatterAgent.formatResponse(
            userRequest,
            "FLAT_GENERATION",
            plan,
            "",  // No block generation for flats
            flatResult
        );
    }

    /**
     * Format questions for the user.
     */
    private String formatQuestions(String missingInfo) {
        // Extract questions from the missing info response
        String[] lines = missingInfo.split("\n");
        StringBuilder formatted = new StringBuilder();
        formatted.append("Ich benötige noch einige Informationen:\n\n");

        for (String line : lines) {
            if (line.trim().startsWith("Question:")) {
                String question = line.substring(line.indexOf(":") + 1).trim();
                formatted.append("• ").append(question).append("\n");
            }
        }

        return formatted.toString();
    }
}
