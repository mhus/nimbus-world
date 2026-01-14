package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.generator.ai.keys.*;
import de.mhus.nimbus.world.generator.flat.FlatToolService;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Flat Generator Agent that generates flat terrain.
 *
 * This agent uses FlatToolService to execute flat operations:
 * create, manipulate, and export.
 */
public interface FlatGeneratorAgent {

    @UserMessage("""
        You are a flat terrain generation specialist.

        Generation Plan: {{GenerationPlanKey}}
        Validation Status: {{FlatValidationConfirmedKey}}
        World ID: {{WorldIdKey}}

        **IMPORTANT: Only proceed if FlatValidationConfirmed is true!**

        **Your task:**

        1. **Determine operation type from plan:**
           - CREATE: New flat with dimensions
           - MANIPULATE: Modify existing flat (requires flatId)
           - EXPORT: Finalize flat to GROUND layer (REQUIRED after create/manipulate)

        2. **Execute using FlatToolService:**

           **For CREATE operation:**
           - Use executeCreate() with parameters:
             - flatId: Generate unique ID like "flat-<worldId>-<timestamp>"
             - worldId, layerDataId (can be empty for flats)
             - sizeX, sizeZ: Dimensions from plan
             - mountX, mountZ: Mount point (default 0, 0)
             - title, description: From request
           - Store the returned flatId for subsequent operations

           **For MANIPULATE operation:**
           - Use executeManipulator() with parameters:
             - manipulator: raise, lower, smooth, plateau, etc.
             - flatId: From previous create or specified in request
             - x, z: Region position
             - sizeX, sizeZ: Region dimensions
             - parameters: Manipulator-specific (e.g., height for raise)

           **For EXPORT operation (ALWAYS REQUIRED):**
           - Use executeExport() with parameters:
             - flatId: The flat to export
             - worldId: Target world
             - layerName: "GROUND" (default ground layer)
             - smoothCorners: true (recommended)
             - optimizeFaces: true (recommended)
           - This ACTIVATES the flat in the world

        3. **Return the result:**

           Format your response:

           Operation: <CREATE|MANIPULATE|EXPORT>
           Flat ID: <flat-id>
           Success: <true-or-false>
           Details: <dimensions-or-affected-area>
           Message: <result-message>

           Example:
           Operation: CREATE
           Flat ID: flat-world1-20260114
           Success: true
           Details: 100x100 blocks
           Message: Created flat terrain with gentle hills

           Operation: EXPORT
           Flat ID: flat-world1-20260114
           Success: true
           Details: 10000 columns exported
           Message: Successfully exported and activated flat in world

        **Critical reminders:**
        - Always EXPORT after CREATE or MANIPULATE
        - Export is what makes changes visible in the world
        - Use smoothCorners and optimizeFaces for better results
        - Report actual tool execution results
        """)
    @Agent(
        description = "Generates flat terrain using FlatToolService",
        typedOutputKey = FlatGenerationResultKey.class
    )
    String generateFlat(
        @K(GenerationPlanKey.class) String generationPlan,
        @K(FlatValidationConfirmedKey.class) Boolean validationConfirmed,
        @K(WorldIdKey.class) String worldId
    );
}
