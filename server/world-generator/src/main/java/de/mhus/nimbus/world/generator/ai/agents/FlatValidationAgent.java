package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Flat Validation Agent that validates flat generation before execution.
 *
 * This agent performs safety checks before flat generation, as flat operations
 * are IRREVERSIBLE and affect the base terrain.
 */
public interface FlatValidationAgent {

    @UserMessage("""
        You are a safety validator for flat terrain generation.

        **CRITICAL: Flat generation is IRREVERSIBLE and affects the base terrain!**

        User Request: "{{UserRequestKey}}"
        Generation Plan: {{GenerationPlanKey}}

        **Your validation task:**

        1. **Verify clear intent:**
           - Does the user clearly want to modify terrain/ground?
           - Is the request explicit about flat/terrain generation?
           - Are they aware this affects the base layer?

        2. **Check parameters:**
           - Are dimensions reasonable? (1-800 blocks typical)
           - Is the operation type clear? (create, manipulate, export)
           - Are all required parameters present?

        3. **Assess risks:**
           - Could this destroy existing terrain unintentionally?
           - Is the scale appropriate (not too large)?
           - Are there any safety concerns?

        4. **Return validation result:**

           **If validation passes**, return exactly:
           VALIDATED

           **If validation requires confirmation**, return:
           REQUIRES_CONFIRMATION: <specific reason>

           Example failure responses:
           REQUIRES_CONFIRMATION: Dimensions 1000x1000 are very large. Please confirm.
           REQUIRES_CONFIRMATION: Flat ID not specified for manipulation operation.
           REQUIRES_CONFIRMATION: Request unclear about terrain modification intent.

        **Validation Guidelines:**
        - Be conservative - better to ask than destroy terrain
        - Dimensions > 500 blocks should require confirmation
        - Ambiguous requests should require confirmation
        - Missing critical parameters should require confirmation
        - Clear, well-specified requests should pass validation
        """)
    @Agent(
        description = "Validates flat generation for safety before execution",
        typedOutputKey = FlatValidationConfirmedKey.class
    )
    String validateFlatGeneration(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationPlanKey.class) String generationPlan
    );
}
