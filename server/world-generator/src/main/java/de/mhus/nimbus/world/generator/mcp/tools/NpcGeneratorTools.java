package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.generator.npc.NpcGeneratorService;
import de.mhus.nimbus.world.generator.npc.NpcGeneratorService.NpcGenerationRequest;
import de.mhus.nimbus.world.generator.npc.NpcGeneratorService.ScheduleEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NpcGeneratorTools implements McpToolBean {

    private final NpcGeneratorService npcGeneratorService;

    @Tool(name = "generate_npc", description = """
            Generate a complete NPC with Entity, NPC-Profile, and Dialog-Playbook using AI.
            The NPC description should be provided as a WDocument in collection='lore' with name prefix 'npc:'.
            General world lore (documents with name prefix 'lore:world' and 'lore:region') is auto-loaded as context.
            Additional lore documents can be referenced via loreContext parameter.
            """)
    public Map<String, Object> generateNpc(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Unique entity identifier for the NPC") String entityId,
            @ToolParam(description = "Name of the WDocument (collection='lore') describing this NPC, e.g. 'npc:farmer_hans'") String npcDocumentName,
            @ToolParam(description = "Entity model ID (e.g. 'human_male_1')", required = false) String modelId,
            @ToolParam(description = "Position X", required = false) Double posX,
            @ToolParam(description = "Position Y", required = false) Double posY,
            @ToolParam(description = "Position Z", required = false) Double posZ,
            @ToolParam(description = "Path to portrait image", required = false) String portraitPath,
            @ToolParam(description = "AI model to use (e.g. 'gemini:gemini-pro')", required = false) String aiModel,
            @ToolParam(description = "Epoch numbers this NPC belongs to", required = false) List<Integer> epoches,
            @ToolParam(description = "Additional lore document names for context (e.g. ['lore:farmland_region'])", required = false) List<String> loreContext) {

        log.debug("MCP: Generate NPC: worldId={}, entityId={}, npcDoc={}", worldId, entityId, npcDocumentName);

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId)) {
            throw new McpToolException("worldId and entityId are required");
        }
        if (Strings.isBlank(npcDocumentName)) {
            throw new McpToolException("npcDocumentName is required (e.g. 'npc:farmer_hans')");
        }

        // Build lore context list: NPC document + additional context
        List<String> allLore = new java.util.ArrayList<>();
        allLore.add(npcDocumentName);
        if (loreContext != null) {
            allLore.addAll(loreContext);
        }

        try {
            NpcGenerationRequest request = new NpcGenerationRequest(
                    worldId, entityId, modelId, null, // gender from entity or lore
                    posX, posY, posZ,
                    null, null, null, // environment, description, background — loaded from lore document
                    portraitPath, aiModel, epoches,
                    null, // schedule — could be added later
                    allLore
            );

            return npcGeneratorService.generateNpc(request);
        } catch (NpcGeneratorService.NpcGenerationException e) {
            throw new McpToolException("NPC generation failed: " + e.getMessage());
        }
    }
}
