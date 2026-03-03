package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.ItemModifier;
import de.mhus.nimbus.generated.types.ItemType;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WItemType;
import de.mhus.nimbus.world.shared.world.WItemTypeService;
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
public class ItemTypeTools {

    private final WItemTypeService itemTypeService;

    @Tool(name = "list_itemtypes", description = "List all item types for a region. Returns itemType, name, type, title, and enabled status.")
    public Map<String, Object> listItemTypes(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Optional search query to filter by name or type", required = false) String query) {
        log.debug("MCP: List item types: worldId={}, query={}", worldId, query);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        List<WItemType> entities;
        if (Strings.isBlank(query)) {
            entities = itemTypeService.findByWorldId(wid);
        } else {
            entities = itemTypeService.findByWorldIdAndQuery(wid, query);
        }

        var dtos = entities.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("itemType", e.getItemType());
            if (e.getPublicData() != null) {
                map.put("name", e.getPublicData().getName() != null ? e.getPublicData().getName() : "");
                map.put("type", e.getPublicData().getType() != null ? e.getPublicData().getType() : "");
                map.put("title", e.getPublicData().getTitle() != null ? e.getPublicData().getTitle() : "");
            }
            map.put("enabled", e.isEnabled());
            return map;
        }).toList();

        return Map.of(
                "worldId", worldId,
                "count", dtos.size(),
                "itemTypes", dtos
        );
    }

    @Tool(name = "get_itemtype", description = "Get a single item type by its itemType ID. Returns full public data including modifier.")
    public Map<String, Object> getItemType(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item type ID (e.g. 'iron_sword', 'health_potion')") String itemType) {
        log.debug("MCP: Get item type: worldId={}, itemType={}", worldId, itemType);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemType)) {
            throw new McpToolException("worldId and itemType are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        WItemType entity = itemTypeService.findByItemType(wid, itemType)
                .orElseThrow(() -> new McpToolException("ItemType not found: " + itemType));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemType", entity.getItemType());
        result.put("enabled", entity.isEnabled());
        if (entity.getPublicData() != null) {
            result.put("publicData", entity.getPublicData());
        }
        return result;
    }

    @Tool(name = "create_itemtype", description = "Create or update an item type. Provide itemType ID and the item properties. The texture path should reference an existing asset (e.g. 'textures/items/iron_sword.png').")
    public Map<String, Object> createItemType(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Unique item type ID (e.g. 'iron_sword', 'health_potion')") String itemType,
            @ToolParam(description = "Item category type (e.g. 'weapon', 'tool', 'food', 'potion', 'armor', 'material')") String type,
            @ToolParam(description = "Display name (e.g. 'Iron Sword')") String name,
            @ToolParam(description = "Item description", required = false) String description,
            @ToolParam(description = "Display title (optional, uses name if not set)", required = false) String title,
            @ToolParam(description = "Texture asset path (e.g. 'textures/items/iron_sword.png')") String texture,
            @ToolParam(description = "Player pose when holding (e.g. 'hold', 'eat', 'drink', 'sword', 'bow')", required = false) String pose,
            @ToolParam(description = "Horizontal scale factor (default 0.3)", required = false) Double scaleX,
            @ToolParam(description = "Vertical scale factor (default 0.3)", required = false) Double scaleY,
            @ToolParam(description = "Whether item is exclusive (default false)", required = false) Boolean exclusive,
            @ToolParam(description = "Additional parameters as key-value pairs", required = false) Map<String, Object> parameters) {
        log.debug("MCP: Create item type: worldId={}, itemType={}", worldId, itemType);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemType)) {
            throw new McpToolException("worldId and itemType are required");
        }
        if (Strings.isBlank(type) || Strings.isBlank(name) || Strings.isBlank(texture)) {
            throw new McpToolException("type, name, and texture are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        ItemModifier modifier = ItemModifier.builder()
                .texture(texture)
                .offset(List.of(0, 0, 0))
                .pose(pose != null ? pose : "hold")
                .exclusive(exclusive != null ? exclusive : false)
                .scaleX(scaleX != null ? scaleX : 0.3)
                .scaleY(scaleY != null ? scaleY : 0.3)
                .build();

        ItemType publicData = ItemType.builder()
                .type(type)
                .name(name)
                .title(title)
                .description(description)
                .modifier(modifier)
                .parameters(parameters)
                .build();

        try {
            WItemType saved = itemTypeService.save(wid, itemType, publicData);
            return Map.of(
                    "itemType", saved.getItemType(),
                    "worldId", saved.getWorldId(),
                    "status", "created"
            );
        } catch (Exception e) {
            throw new McpToolException("Failed to create item type: " + e.getMessage());
        }
    }

    @Tool(name = "delete_itemtype", description = "Delete an item type by its itemType ID.")
    public Map<String, Object> deleteItemType(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item type ID to delete") String itemType) {
        log.debug("MCP: Delete item type: worldId={}, itemType={}", worldId, itemType);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemType)) {
            throw new McpToolException("worldId and itemType are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        boolean deleted = itemTypeService.delete(wid, itemType);
        if (!deleted) {
            throw new McpToolException("ItemType not found: " + itemType);
        }

        return Map.of(
                "deleted", true,
                "itemType", itemType
        );
    }
}
