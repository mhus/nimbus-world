package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
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
public class AnythingTools {

    private final WAnythingService anythingService;

    @Tool(name = "list_anything", description = "List WAnything entities by worldId and collection. Returns name, title, type, and enabled status.")
    public Map<String, Object> listAnything(
            @ToolParam(description = "World ID or region collection (e.g. '@region:ymir')") String worldId,
            @ToolParam(description = "Collection name (e.g. 'flora-models')") String collection) {
        log.debug("MCP: List anything: worldId={}, collection={}", worldId, collection);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection)) {
            throw new McpToolException("worldId and collection are required");
        }

        List<WAnything> entities = anythingService.findByWorldIdAndCollection(worldId, collection);
        var dtos = entities.stream().map(e -> Map.<String, Object>of(
                "id", e.getId(),
                "name", e.getName(),
                "title", e.getTitle() != null ? e.getTitle() : "",
                "type", e.getType() != null ? e.getType() : "",
                "enabled", e.isEnabled()
        )).toList();

        return Map.of(
                "worldId", worldId,
                "collection", collection,
                "count", dtos.size(),
                "entities", dtos
        );
    }

    @Tool(name = "get_anything", description = "Get a single WAnything entity by worldId, collection, and name. Returns full data.")
    public Map<String, Object> getAnything(
            @ToolParam(description = "World ID or region collection (e.g. '@region:ymir')") String worldId,
            @ToolParam(description = "Collection name") String collection,
            @ToolParam(description = "Entity name") String name) {
        log.debug("MCP: Get anything: worldId={}, collection={}, name={}", worldId, collection, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection) || Strings.isBlank(name)) {
            throw new McpToolException("worldId, collection, and name are required");
        }

        Optional<WAnything> entity = anythingService.findByWorldIdAndCollectionAndName(worldId, collection, name);
        if (entity.isEmpty()) {
            throw new McpToolException("Entity not found: worldId=" + worldId
                    + ", collection=" + collection + ", name=" + name);
        }

        WAnything e = entity.get();
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", e.getId());
        dto.put("worldId", e.getWorldId() != null ? e.getWorldId() : "");
        dto.put("regionId", e.getRegionId() != null ? e.getRegionId() : "");
        dto.put("collection", e.getCollection());
        dto.put("name", e.getName());
        dto.put("title", e.getTitle() != null ? e.getTitle() : "");
        dto.put("description", e.getDescription() != null ? e.getDescription() : "");
        dto.put("type", e.getType() != null ? e.getType() : "");
        dto.put("data", e.getData());
        dto.put("enabled", e.isEnabled());
        return dto;
    }

    @Tool(name = "create_anything", description = "Create a new WAnything entity scoped by worldId. Data can be any JSON object.")
    public Map<String, Object> createAnything(
            @ToolParam(description = "World ID or region collection (e.g. '@region:ymir')") String worldId,
            @ToolParam(description = "Collection name") String collection,
            @ToolParam(description = "Unique entity name within collection") String name,
            @ToolParam(description = "Display title", required = false) String title,
            @ToolParam(description = "Description", required = false) String description,
            @ToolParam(description = "Type category within collection", required = false) String type,
            @ToolParam(description = "Arbitrary JSON data payload") Object data) {
        log.debug("MCP: Create anything: worldId={}, collection={}, name={}", worldId, collection, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection) || Strings.isBlank(name)) {
            throw new McpToolException("worldId, collection, and name are required");
        }
        if (data == null) {
            throw new McpToolException("data is required");
        }

        try {
            WAnything entity = anythingService.createWithWorldId(
                    worldId, collection, name, title, description, type, data
            );

            return Map.of(
                    "id", entity.getId(),
                    "worldId", entity.getWorldId() != null ? entity.getWorldId() : "",
                    "regionId", entity.getRegionId() != null ? entity.getRegionId() : "",
                    "collection", entity.getCollection(),
                    "name", entity.getName()
            );
        } catch (IllegalStateException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    @Tool(name = "delete_anything", description = "Delete a WAnything entity by worldId, collection, and name.")
    public Map<String, Object> deleteAnything(
            @ToolParam(description = "World ID or region collection (e.g. '@region:ymir')") String worldId,
            @ToolParam(description = "Collection name") String collection,
            @ToolParam(description = "Entity name") String name) {
        log.debug("MCP: Delete anything: worldId={}, collection={}, name={}", worldId, collection, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection) || Strings.isBlank(name)) {
            throw new McpToolException("worldId, collection, and name are required");
        }

        anythingService.deleteByWorldIdAndCollectionAndName(worldId, collection, name);
        return Map.of(
                "deleted", true,
                "worldId", worldId,
                "collection", collection,
                "name", name
        );
    }
}
