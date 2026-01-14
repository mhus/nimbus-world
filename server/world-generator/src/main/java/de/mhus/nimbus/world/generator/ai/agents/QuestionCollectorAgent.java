package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Question Collector Agent that identifies missing information needed for generation.
 *
 * This agent analyzes the user's request and current context to determine what information
 * is still needed before generation can proceed.
 */
public interface QuestionCollectorAgent {

    @UserMessage("""
        You are analyzing a generation request to identify missing information.

        User Request: "{{UserRequestKey}}"
        Generation Type: {{GenerationTypeKey}}
        World ID: {{WorldIdKey}}
        Session ID: {{SessionIdKey}}

        Analyze what information is needed to execute this request:

        **For BLOCK_GENERATION, check if we have:**
        - Layer name (which layer to generate blocks in)
        - Position or reference point (where to place the structure)
        - Size/dimensions (how big should it be)
        - Block types or materials (what blocks to use)
        - Model name (if using MODEL layer)

        **For FLAT_GENERATION, check if we have:**
        - Flat ID (if modifying existing flat)
        - Dimensions (sizeX, sizeZ)
        - Terrain type or characteristics
        - Whether to export after generation
        - Mount point (if creating new flat)

        **For CHAT_RESPONSE:**
        - No additional information needed

        **Response Format:**
        If ALL information is present and sufficient, return exactly:
        READY

        If information is missing, list the questions (one per line):
        Question: What layer should be used for the blocks?
        Question: What size should the structure be?
        Question: What position should it be placed at?

        Be concise and only ask for truly necessary information.
        """)
    @Agent(
        description = "Identifies missing information needed for generation",
        typedOutputKey = MissingInfoKey.class
    )
    String identifyMissingInfo(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationTypeKey.class) String generationType,
        @K(WorldIdKey.class) String worldId,
        @K(SessionIdKey.class) String sessionId
    );
}
