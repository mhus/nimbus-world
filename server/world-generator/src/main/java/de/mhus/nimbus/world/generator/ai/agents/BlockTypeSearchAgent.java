package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.ai.tool.BlockTypeToolService;
import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * BlockType Search Agent that finds appropriate block types for generation.
 *
 * This agent uses BlockTypeToolService to search for suitable block types
 * based on the user's request and generation plan.
 */
public interface BlockTypeSearchAgent {

    @UserMessage("""
        You are a block type specialist helping to find the right blocks for generation.

        User Request: "{{UserRequestKey}}"
        Generation Plan: {{GenerationPlanKey}}
        World ID: {{WorldIdKey}}

        **Your task:**

        1. **Analyze the requirements:**
           - What materials are mentioned? (stone, wood, metal, glass, etc.)
           - What style is needed? (medieval, modern, fantasy, natural, etc.)
           - What properties? (solid, transparent, decorative, functional)

        2. **Search for block types using BlockTypeToolService:**
           - Use lookupBlockTypesByQuery() to find blocks
           - Search for material keywords (e.g., "stone", "wood", "metal")
           - Search for functional keywords (e.g., "wall", "floor", "roof")
           - Use lookupAllBlockTypes() if you need an overview

        3. **Select appropriate blocks:**
           - Prefer blocks from @shared:n scope (most common)
           - Consider enabled blocks only
           - Choose blocks that match the visual style
           - Select 3-8 different block types for variety

        4. **Return your selection:**
           Format as a comma-separated list of block IDs with brief explanations:

           stone:brick, wood:plank, glass:clear

           Explanation: stone:brick for walls (solid, medieval look), wood:plank for floors
           (warm, natural), glass:clear for windows (transparent)

        Be concise and practical in your choices.
        """)
    @Agent(
        description = "Searches for appropriate block types using BlockTypeToolService",
        typedOutputKey = SelectedBlockTypesKey.class
    )
    String findBlockTypes(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationPlanKey.class) String generationPlan,
        @K(WorldIdKey.class) String worldId
    );
}
