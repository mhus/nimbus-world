package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.ai.tool.DocumentToolService;
import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Planner Agent that creates a detailed generation plan using documentation.
 *
 * This agent searches the 'generator_instructions' collection for best practices
 * and creates a step-by-step plan for the generation task.
 */
public interface PlannerAgent {

    @UserMessage("""
        You are a world generation planner with access to documentation tools.

        User Request: "{{UserRequestKey}}"
        Generation Type: {{GenerationTypeKey}}
        World ID: {{WorldIdKey}}

        **IMPORTANT: First search for relevant documentation:**

        Use the DocumentToolService tools to search the 'generator_instructions' collection:
        - Use lookupDocumentsByContent() to find relevant generation guides
        - Use lookupDocumentsBySummary() to find documentation about {{GenerationTypeKey}}
        - Search for keywords like: "block generation", "flat generation", "manipulators", "best practices"

        **After reviewing documentation, create a detailed plan:**

        For BLOCK_GENERATION:
        1. Determine which block manipulator(s) to use (plateau, cube, sphere, line, etc.)
        2. Specify the sequence of operations
        3. Define parameters for each manipulator (position, size, transform, etc.)
        4. List required block types
        5. Describe expected result

        For FLAT_GENERATION:
        1. Determine operation type (create, manipulate, export)
        2. Specify flat parameters (dimensions, terrain type)
        3. List manipulators if needed (raise, lower, smooth, etc.)
        4. Plan export step (ALWAYS required to activate changes)
        5. Describe expected result

        For CHAT_RESPONSE:
        1. Provide helpful information or answer
        2. Suggest next steps if applicable

        **Format your plan as clear, numbered steps.**
        Be specific about technical details (manipulator names, parameter values).
        """)
    @Agent(
        description = "Creates generation plan using documentation from generator_instructions",
        typedOutputKey = GenerationPlanKey.class
    )
    String createPlan(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationTypeKey.class) String generationType,
        @K(WorldIdKey.class) String worldId
    );
}
