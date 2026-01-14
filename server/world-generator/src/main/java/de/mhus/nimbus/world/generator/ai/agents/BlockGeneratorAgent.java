package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.*;
import de.mhus.nimbus.world.generator.blocks.BlockToolService;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Block Generator Agent that generates blocks using manipulators.
 *
 * This agent uses BlockToolService to execute block manipulators with the
 * appropriate context and parameters.
 */
public interface BlockGeneratorAgent {

    @UserMessage("""
        You are a block generation specialist executing the generation plan.

        Generation Plan: {{GenerationPlanKey}}
        Selected Block Types: {{SelectedBlockTypesKey}}
        Layer Context:
        - Layer Name: {{SelectedLayerNameKey}}
        - Layer Data ID: {{SelectedLayerDataIdKey}}
        - Model Name: {{SelectedModelNameKey}}
        World ID: {{WorldIdKey}}
        Session ID: {{SessionIdKey}}

        **Your task:**

        1. **Parse the layer context from SelectedLayerName:**
           - Extract the Layer Data ID
           - Extract Model Name (if present)
           - Extract Layer Type (GROUND or MODEL)

        2. **Execute the block manipulator using BlockToolService:**

           **Available manipulators (common ones):**
           - plateau: Creates a flat platform (params: width, depth, height, transform, block)
           - cube: Creates a solid cube (params: width, depth, height, transform, block)
           - sphere: Creates a sphere (params: radius, transform, block)
           - line: Creates a line of blocks (params: length, direction, transform, block)
           - wall: Creates a wall (params: width, height, transform, block)

           **Use getAvailableManipulators() to see all options**

        3. **Build the execution:**
           - Use executeManipulator() with proper ManipulatorContext
           - Include: worldId, sessionId, layerDataId, layerName, modelName (if MODEL layer)
           - Include manipulator-specific parameters from the plan
           - Map block types from SelectedBlockTypes to block parameters

        4. **Return the result:**

           Format your response:

           Manipulator: <manipulator-name>
           Blocks Generated: <count>
           Success: <true-or-false>
           Message: <result-message>

           Example:
           Manipulator: plateau
           Blocks Generated: 150
           Success: true
           Message: Successfully created a 10x10x3 stone platform using stone:brick

        **Important:**
        - Always include worldId, sessionId, layerDataId, layerName in the context
        - For MODEL layers, include modelName
        - Parse block types from the comma-separated list in SelectedBlockTypes
        - Report actual results from the tool execution
        """)
    @Agent(
        description = "Generates blocks using BlockToolService manipulators",
        typedOutputKey = BlockGenerationResultKey.class
    )
    String generateBlocks(
        @K(GenerationPlanKey.class) String generationPlan,
        @K(SelectedBlockTypesKey.class) String selectedBlockTypes,
        @K(SelectedLayerNameKey.class) String layerContext,
        @K(WorldIdKey.class) String worldId,
        @K(SessionIdKey.class) String sessionId
    );
}
