package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Response Formatter Agent that creates the final response to the user.
 *
 * This agent takes the execution results and formats them into a friendly,
 * informative message for the user.
 */
public interface ResponseFormatterAgent {

    @UserMessage("""
        You are formatting the final response to the user.

        Original Request: "{{UserRequestKey}}"
        Generation Type: {{GenerationTypeKey}}
        Generation Plan: {{GenerationPlanKey}}
        Block Generation Result: {{BlockGenerationResultKey}}
        Flat Generation Result: {{FlatGenerationResultKey}}

        **Note:** Block/Flat Generation Result fields may be empty if not applicable.

        **Format a friendly, informative response that:**

        1. **Confirms what was done**
           - Summarize the action taken
           - Reference the user's original request

        2. **Provides key details**
           - For blocks: mention layer name, block count, structure type
           - For flats: mention dimensions, flat ID, export status
           - For chat: provide the answer or information

        3. **Mentions any issues or warnings**
           - If there were errors, explain them clearly
           - If something was adjusted from the original request, mention it

        4. **Suggests next steps (if relevant)**
           - How to view the generated content
           - What they can do next
           - Any follow-up actions needed

        **Tone Guidelines:**
        - Be friendly and conversational (in German)
        - Be concise but informative
        - Use clear, non-technical language when possible
        - Show enthusiasm for successful generations
        - Be empathetic if there were issues

        **Response Language:** German (since the user speaks German)

        Keep the response to 2-4 sentences maximum.
        """)
    @Agent(
        description = "Formats final response to user based on execution results",
        typedOutputKey = FinalResponseKey.class
    )
    String formatResponse(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationTypeKey.class) String generationType,
        @K(GenerationPlanKey.class) String generationPlan,
        @K(BlockGenerationResultKey.class) String blockGenerationResult,
        @K(FlatGenerationResultKey.class) String flatGenerationResult
    );
}
