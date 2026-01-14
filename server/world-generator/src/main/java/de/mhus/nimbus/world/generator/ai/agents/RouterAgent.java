package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.GenerationTypeKey;
import de.mhus.nimbus.world.generator.ai.keys.UserRequestKey;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Router Agent that decides whether to provide a chat response, generate blocks, or generate flats.
 *
 * This agent analyzes the user's request and returns one of three generation types:
 * - CHAT_RESPONSE: For questions, information requests, or clarifications
 * - BLOCK_GENERATION: For creating/modifying structures, buildings, objects
 * - FLAT_GENERATION: For creating initial terrain or modifying ground/flats
 */
public interface RouterAgent {

    @UserMessage("""
        You are a world generator assistant router.
        Analyze the user request and determine the appropriate action.

        User Request: "{{UserRequestKey}}"

        Decide which action is needed:

        **CHAT_RESPONSE**: Choose this if the user:
        - Asks questions or wants information
        - Needs clarification or help understanding something
        - Is having a general conversation
        - The request is unclear or ambiguous

        **BLOCK_GENERATION**: Choose this if the user wants to:
        - Create structures (houses, towers, bridges, etc.)
        - Build objects (furniture, decorations, etc.)
        - Modify or extend existing structures
        - Place blocks in specific patterns or shapes
        - Generate 3D constructions

        **FLAT_GENERATION**: Choose this if the user wants to:
        - Create initial terrain or ground
        - Generate flat worlds or regions
        - Modify base terrain (hills, valleys, plains)
        - Work with the world's ground layer
        - Make irreversible terrain changes

        **Important Decision Guidelines:**
        - Flats are IRREVERSIBLE and affect base terrain - only choose FLAT_GENERATION if clearly indicated
        - Blocks are for structures and can be undone via layers - prefer BLOCK_GENERATION for constructions
        - If unclear or need more information, choose CHAT_RESPONSE

        Return ONLY one of these exact values (no additional text):
        CHAT_RESPONSE
        BLOCK_GENERATION
        FLAT_GENERATION
        """)
    @Agent(
        description = "Decides between chat response, block generation, or flat generation",
        typedOutputKey = GenerationTypeKey.class
    )
    String routeRequest(@K(UserRequestKey.class) String userRequest);
}
