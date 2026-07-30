package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorldTools implements McpToolBean {

    private final WWorldService worldService;
    private final ObjectMapper objectMapper;

    @Tool(name = "list_worlds", description = "List all available worlds")
    public Map<String, Object> listWorlds() {
        log.debug("MCP: List worlds");

        List<WWorld> worlds = worldService.findAll();
        List<Map<String, Object>> worldDtos = worlds.stream()
                .map(this::toWorldDto)
                .collect(Collectors.toList());

        return Map.of(
                "worlds", worldDtos,
                "count", worldDtos.size()
        );
    }

    @Tool(name = "get_world", description = "Get detailed information about a specific world including publicData and settings")
    public Map<String, Object> getWorld(
            @ToolParam(description = "World ID") String worldId) {
        log.debug("MCP: Get world: worldId={}", worldId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new McpToolException("World not found: " + worldId)
        );

        Map<String, Object> result = toWorldDto(world);
        // Add publicData as JSON-safe map
        if (world.getPublicData() != null) {
            result.put("publicData", objectMapper.convertValue(world.getPublicData(), Map.class));
        }
        return result;
    }

    @Tool(name = "update_world_settings", description = "Update world settings (environmentScripts, worldTime, shadows, etc.). Merges provided fields into existing settings.")
    public Map<String, Object> updateWorldSettings(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Settings fields to update as JSON object") Map<String, Object> settings) {
        log.debug("MCP: Update world settings: worldId={}, settings={}", worldId, settings);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new McpToolException("World not found: " + worldId)
        );

        var publicData = world.getPublicData();
        if (publicData == null) {
            throw new McpToolException("World has no publicData: " + worldId);
        }

        // Ensure settings exist
        if (publicData.getSettings() == null) {
            publicData.setSettings(new de.mhus.nimbus.generated.types.WorldInfoSettingsDTO());
        }

        // Merge provided settings into existing settings via ObjectMapper
        var existingSettings = objectMapper.convertValue(publicData.getSettings(), Map.class);
        existingSettings.putAll(settings);
        var updatedSettings = objectMapper.convertValue(existingSettings, de.mhus.nimbus.generated.types.WorldInfoSettingsDTO.class);
        publicData.setSettings(updatedSettings);

        worldService.save(world);

        return Map.of(
                "worldId", worldId,
                "message", "Settings updated successfully",
                "updatedFields", settings.keySet()
        );
    }

    private Map<String, Object> toWorldDto(WWorld world) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", world.getId());
        dto.put("worldId", world.getWorldId());
        dto.put("regionId", world.getRegionId());
        dto.put("enabled", world.isEnabled());
        if (world.getPublicData() != null) {
            dto.put("name", world.getPublicData().getTitle());
            dto.put("description", world.getPublicData().getDescription());
        }
        dto.put("createdAt", world.getCreatedAt());
        dto.put("updatedAt", world.getUpdatedAt());
        return dto;
    }
}
