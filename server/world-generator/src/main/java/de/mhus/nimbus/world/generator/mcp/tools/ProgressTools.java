package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgressTools implements McpToolBean {

    private final WProgressService progressService;

    @Tool(name = "list_progress", description = "List progress entries for a player in a world. Optionally filter by type or quest.")
    public Map<String, Object> listProgress(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Player ID") String playerId,
            @ToolParam(description = "Optional progress type filter (e.g. 'quest', 'achievement', 'skill')", required = false) String type,
            @ToolParam(description = "Optional quest identifier filter", required = false) String quest) {
        log.debug("MCP: List progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);

        if (Strings.isBlank(worldId) || Strings.isBlank(playerId)) {
            throw new McpToolException("worldId and playerId are required");
        }

        List<WProgress> entries;
        if (Strings.isNotBlank(type)) {
            entries = progressService.findByWorldIdAndPlayerIdAndType(worldId, playerId, type);
        } else if (Strings.isNotBlank(quest)) {
            entries = progressService.findByWorldIdAndPlayerIdAndQuest(worldId, playerId, quest);
        } else {
            entries = progressService.findByWorldIdAndPlayerId(worldId, playerId);
        }

        var dtos = entries.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            map.put("type", e.getType());
            map.put("quest", e.getQuest() != null ? e.getQuest() : "");
            map.put("progressData", e.getProgressData());
            return map;
        }).toList();

        return Map.of(
                "worldId", worldId,
                "playerId", playerId,
                "count", dtos.size(),
                "entries", dtos
        );
    }

    @Tool(name = "get_progress", description = "Get a specific progress entry by world, player, type, and optional quest.")
    public Map<String, Object> getProgress(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Player ID") String playerId,
            @ToolParam(description = "Progress type (e.g. 'quest', 'achievement', 'skill')") String type,
            @ToolParam(description = "Optional quest identifier", required = false) String quest) {
        log.debug("MCP: Get progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);

        if (Strings.isBlank(worldId) || Strings.isBlank(playerId) || Strings.isBlank(type)) {
            throw new McpToolException("worldId, playerId, and type are required");
        }

        WProgress progress = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, type, quest)
                .orElseThrow(() -> new McpToolException("Progress not found for type=" + type + ", quest=" + quest));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", progress.getId());
        result.put("worldId", progress.getWorldId());
        result.put("playerId", progress.getPlayerId());
        result.put("type", progress.getType());
        result.put("quest", progress.getQuest());
        result.put("progressData", progress.getProgressData());
        return result;
    }

    @Tool(name = "save_progress", description = "Create or update a progress entry. If a matching entry (worldId + playerId + type + quest) exists, it is updated. Otherwise a new entry is created.")
    public Map<String, Object> saveProgress(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Player ID") String playerId,
            @ToolParam(description = "Progress type (e.g. 'quest', 'achievement', 'skill')") String type,
            @ToolParam(description = "Optional quest identifier", required = false) String quest,
            @ToolParam(description = "Progress data as key-value pairs") Map<String, Object> progressData) {
        log.debug("MCP: Save progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);

        if (Strings.isBlank(worldId) || Strings.isBlank(playerId) || Strings.isBlank(type)) {
            throw new McpToolException("worldId, playerId, and type are required");
        }

        try {
            WProgress saved = progressService.save(worldId, playerId, type, quest, progressData);
            return Map.of(
                    "id", saved.getId(),
                    "worldId", saved.getWorldId(),
                    "playerId", saved.getPlayerId(),
                    "type", saved.getType(),
                    "status", "saved"
            );
        } catch (Exception e) {
            throw new McpToolException("Failed to save progress: " + e.getMessage());
        }
    }

    @Tool(name = "delete_progress", description = "Delete a specific progress entry by its ID.")
    public Map<String, Object> deleteProgress(
            @ToolParam(description = "Progress entry ID") String id) {
        log.debug("MCP: Delete progress: id={}", id);

        if (Strings.isBlank(id)) {
            throw new McpToolException("id is required");
        }

        boolean deleted = progressService.delete(id);
        if (!deleted) {
            throw new McpToolException("Progress not found: " + id);
        }

        return Map.of(
                "deleted", true,
                "id", id
        );
    }

    @Tool(name = "delete_player_progress", description = "Delete all progress entries for a player in a world.")
    public Map<String, Object> deletePlayerProgress(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Player ID") String playerId) {
        log.debug("MCP: Delete player progress: worldId={}, playerId={}", worldId, playerId);

        if (Strings.isBlank(worldId) || Strings.isBlank(playerId)) {
            throw new McpToolException("worldId and playerId are required");
        }

        progressService.deleteByWorldIdAndPlayerId(worldId, playerId);

        return Map.of(
                "deleted", true,
                "worldId", worldId,
                "playerId", playerId
        );
    }
}
