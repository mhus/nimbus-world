package de.mhus.nimbus.world.generator.ai.orchestration;

import de.mhus.nimbus.world.ai.tool.BlockTypeToolService;
import de.mhus.nimbus.world.ai.tool.DocumentToolService;
import de.mhus.nimbus.world.ai.tool.LayerToolService;
import de.mhus.nimbus.world.generator.ai.agents.*;
import de.mhus.nimbus.world.generator.ai.memory.WChatMemoryStore;
import de.mhus.nimbus.world.generator.blocks.BlockToolService;
import de.mhus.nimbus.world.generator.flat.FlatToolService;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating WorldGeneratorOrchestrator instances.
 *
 * This factory builds all the agents and workflows needed for the orchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorldGeneratorOrchestratorFactory {

    private final ChatModel chatModel;
    private final DocumentToolService documentToolService;
    private final BlockTypeToolService blockTypeToolService;
    private final LayerToolService layerToolService;
    private final BlockToolService blockToolService;
    private final FlatToolService flatToolService;
    private final WChatMemoryStore memoryStore;

    /**
     * Create a new orchestrator instance for a specific world/session/chat.
     */
    public WorldGeneratorOrchestrator create(
            String worldId,
            String sessionId,
            String chatId,
            String playerId,
            List<WChatMessage> chatHistory) {

        log.debug("Creating orchestrator for world={}, session={}, chat={}", worldId, sessionId, chatId);

        // Build individual agents
        RouterAgent routerAgent = buildRouterAgent();
        QuestionCollectorAgent questionCollectorAgent = buildQuestionCollectorAgent();
        PlannerAgent plannerAgent = buildPlannerAgent();
        ResponseFormatterAgent responseFormatterAgent = buildResponseFormatterAgent();

        // Build block generation agents
        BlockTypeSearchAgent blockTypeSearchAgent = buildBlockTypeSearchAgent();
        LayerPreparationAgent layerPreparationAgent = buildLayerPreparationAgent();
        BlockGeneratorAgent blockGeneratorAgent = buildBlockGeneratorAgent();

        // Build flat generation agents
        FlatValidationAgent flatValidationAgent = buildFlatValidationAgent();
        FlatGeneratorAgent flatGeneratorAgent = buildFlatGeneratorAgent();

        // Create and return orchestrator with all agents
        return new WorldGeneratorOrchestrator(
            worldId,
            sessionId,
            chatId,
            playerId,
            routerAgent,
            questionCollectorAgent,
            plannerAgent,
            responseFormatterAgent,
            blockTypeSearchAgent,
            layerPreparationAgent,
            blockGeneratorAgent,
            flatValidationAgent,
            flatGeneratorAgent,
            memoryStore,
            chatHistory
        );
    }

    private RouterAgent buildRouterAgent() {
        return AgenticServices.agentBuilder(RouterAgent.class)
            .chatModel(chatModel)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.GenerationTypeKey.class)
            .build();
    }

    private QuestionCollectorAgent buildQuestionCollectorAgent() {
        return AgenticServices.agentBuilder(QuestionCollectorAgent.class)
            .chatModel(chatModel)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.MissingInfoKey.class)
            .build();
    }

    private PlannerAgent buildPlannerAgent() {
        return AgenticServices.agentBuilder(PlannerAgent.class)
            .chatModel(chatModel)
            .tools(documentToolService)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.GenerationPlanKey.class)
            .build();
    }

    private ResponseFormatterAgent buildResponseFormatterAgent() {
        return AgenticServices.agentBuilder(ResponseFormatterAgent.class)
            .chatModel(chatModel)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.FinalResponseKey.class)
            .build();
    }

    private BlockTypeSearchAgent buildBlockTypeSearchAgent() {
        return AgenticServices.agentBuilder(BlockTypeSearchAgent.class)
            .chatModel(chatModel)
            .tools(blockTypeToolService)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.SelectedBlockTypesKey.class)
            .build();
    }

    private LayerPreparationAgent buildLayerPreparationAgent() {
        return AgenticServices.agentBuilder(LayerPreparationAgent.class)
            .chatModel(chatModel)
            .tools(layerToolService)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.SelectedLayerNameKey.class)
            .build();
    }

    private BlockGeneratorAgent buildBlockGeneratorAgent() {
        return AgenticServices.agentBuilder(BlockGeneratorAgent.class)
            .chatModel(chatModel)
            .tools(blockToolService)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.BlockGenerationResultKey.class)
            .build();
    }

    private FlatValidationAgent buildFlatValidationAgent() {
        return AgenticServices.agentBuilder(FlatValidationAgent.class)
            .chatModel(chatModel)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.FlatValidationConfirmedKey.class)
            .build();
    }

    private FlatGeneratorAgent buildFlatGeneratorAgent() {
        return AgenticServices.agentBuilder(FlatGeneratorAgent.class)
            .chatModel(chatModel)
            .tools(flatToolService)
            .outputKey(de.mhus.nimbus.world.generator.ai.keys.FlatGenerationResultKey.class)
            .build();
    }
}
