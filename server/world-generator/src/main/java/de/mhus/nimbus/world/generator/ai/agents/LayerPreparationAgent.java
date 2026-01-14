package de.mhus.nimbus.world.generator.ai.agents;

import de.mhus.nimbus.world.ai.tool.LayerToolService;
import de.mhus.nimbus.world.generator.ai.keys.*;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;

/**
 * Layer Preparation Agent that prepares layers and models for block generation.
 *
 * This agent uses LayerToolService to list existing layers, select appropriate ones,
 * or create new layers and models as needed.
 */
public interface LayerPreparationAgent {

    @UserMessage("""
        You are a layer management specialist preparing the environment for block generation.

        User Request: "{{UserRequestKey}}"
        Generation Plan: {{GenerationPlanKey}}
        World ID: {{WorldIdKey}}
        Session ID: {{SessionIdKey}}

        **Your task:**

        1. **List existing layers:**
           - Use listLayers() to see all available layers in this world
           - Review layer types (GROUND vs MODEL) and properties

        2. **Decide on layer strategy:**

           **Option A: Use existing layer**
           - If a suitable layer exists (check name, type, purpose)
           - Use selectLayerForEditing() to select it
           - For MODEL layers: check listLayerModels() and select or create a model

           **Option B: Create new layer**
           - For structures/buildings: Create MODEL layer with createModelLayer()
           - For terrain modifications: Create GROUND layer with createGroundLayer()
           - Choose a descriptive layer name (e.g., "structures", "buildings", "decorations")
           - For MODEL layers: Create a model using createLayerModel() with appropriate mount point

        3. **Return layer information:**

           Format your response with these exact fields:

           Layer Name: <layer-name>
           Layer Data ID: <layer-data-id>
           Model Name: <model-name-or-NONE>
           Layer Type: <GROUND-or-MODEL>
           Action: <SELECTED-or-CREATED>

           Example:
           Layer Name: structures
           Layer Data ID: layer_abc123
           Model Name: house-model-1
           Layer Type: MODEL
           Action: CREATED

        **Guidelines:**
        - Prefer using existing layers if they fit the purpose
        - Create new layers only when necessary
        - Use descriptive, lowercase names with hyphens
        - For MODEL layers, always ensure a model exists or create one
        """)
    @Agent(
        description = "Prepares layer and model for block generation using LayerToolService",
        typedOutputKey = SelectedLayerNameKey.class
    )
    String prepareLayer(
        @K(UserRequestKey.class) String userRequest,
        @K(GenerationPlanKey.class) String generationPlan,
        @K(WorldIdKey.class) String worldId,
        @K(SessionIdKey.class) String sessionId
    );
}
