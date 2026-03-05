package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
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
public class ItemTools {

    private final WItemService itemService;

    @Tool(name = "list_items", description = "List all items for a region. Returns itemId, itemType, title, and enabled status.")
    public Map<String, Object> listItems(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Optional search query to filter by title or itemType", required = false) String query) {
        log.debug("MCP: List items: worldId={}, query={}", worldId, query);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        List<WItem> entities;
        if (Strings.isBlank(query)) {
            entities = itemService.findEnabledByWorldId(wid);
        } else {
            entities = itemService.findEnabledByWorldIdAndQuery(wid, query);
        }

        var dtos = entities.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("itemId", e.getItemId());
            if (e.getPublicData() != null) {
                map.put("itemType", e.getPublicData().getItemType() != null ? e.getPublicData().getItemType() : "");
                map.put("type", e.getPublicData().getType() != null ? e.getPublicData().getType() : "");
                map.put("title", e.getPublicData().getTitle() != null ? e.getPublicData().getTitle() : "");
                map.put("texture", e.getPublicData().getTexture() != null ? e.getPublicData().getTexture() : "");
            }
            map.put("enabled", e.isEnabled());
            return map;
        }).toList();

        return Map.of(
                "worldId", worldId,
                "count", dtos.size(),
                "items", dtos
        );
    }

    @Tool(name = "get_item", description = "Get a single item by its itemId. Returns full public data including modifier.")
    public Map<String, Object> getItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item ID (e.g. UUID or custom ID)") String itemId) {
        log.debug("MCP: Get item: worldId={}, itemId={}", worldId, itemId);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId and itemId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        WItem entity = itemService.findByItemId(wid, itemId)
                .orElseThrow(() -> new McpToolException("Item not found: " + itemId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", entity.getItemId());
        result.put("enabled", entity.isEnabled());
        if (entity.getPublicData() != null) {
            result.put("publicData", entity.getPublicData());
        }
        if (entity.getServer() != null) {
            result.put("server", entity.getServer());
        }
        return result;
    }

    @Tool(name = "create_item", description = "Create or update an item. If no itemId is provided, one is auto-generated. All rendering properties (texture, scale, pose, etc.) are directly on the item.")
    public Map<String, Object> createItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Optional item ID. If not provided, an ID is auto-generated.", required = false) String itemId,
            @ToolParam(description = "Item type identifier (e.g. 'iron_sword', 'health_potion')") String itemType,
            @ToolParam(description = "Item category type (e.g. 'weapon', 'tool', 'food', 'potion', 'armor', 'material')") String type,
            @ToolParam(description = "Display title (e.g. 'Iron Sword')") String title,
            @ToolParam(description = "Item description", required = false) String description,
            @ToolParam(description = "Texture asset path (e.g. 'textures/items/iron_sword.png')") String texture,
            @ToolParam(description = "Player pose when holding (e.g. 'hold', 'eat', 'drink', 'sword', 'bow')", required = false) String pose,
            @ToolParam(description = "Horizontal scale factor (default 0.3)", required = false) Double scaleX,
            @ToolParam(description = "Vertical scale factor (default 0.3)", required = false) Double scaleY,
            @ToolParam(description = "Whether item is exclusive (default false)", required = false) Boolean exclusive,
            @ToolParam(description = "Whether item is generic/stackable (default false)", required = false) Boolean generic,
            @ToolParam(description = "Additional parameters as key-value pairs", required = false) Map<String, String> parameters,
            @ToolParam(description = "Server-side parameters for gameplay configuration", required = false) Map<String, String> server) {
        log.debug("MCP: Create item: worldId={}, itemId={}, itemType={}", worldId, itemId, itemType);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }
        if (Strings.isBlank(itemType) || Strings.isBlank(title)) {
            throw new McpToolException("itemType and title are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            var builder = Item.builder()
                    .itemType(itemType)
                    .type(type)
                    .title(title)
                    .description(description)
                    .texture(texture)
                    .pose(pose != null ? pose : "hold")
                    .scaleX(scaleX != null ? scaleX : 0.3)
                    .scaleY(scaleY != null ? scaleY : 0.3)
                    .exclusive(exclusive)
                    .generic(generic)
                    .offset(List.of(0, 0, 0))
                    .parameters(parameters);

            if (!Strings.isBlank(itemId)) {
                builder.name(itemId);
            }

            WItem saved = itemService.create(wid, builder.build());
            if (server != null && !server.isEmpty()) {
                saved.setServer(server);
                itemService.saveEntity(saved);
            }
            return Map.of(
                    "itemId", saved.getItemId(),
                    "worldId", saved.getWorldId(),
                    "status", "created"
            );
        } catch (Exception e) {
            throw new McpToolException("Failed to create item: " + e.getMessage());
        }
    }

    @Tool(name = "duplicate_item", description = "Duplicate an existing item. Creates a new item with a new auto-generated itemId, copying all properties from the source item. Optionally override the title.")
    public Map<String, Object> duplicateItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item ID of the existing item to duplicate") String itemId,
            @ToolParam(description = "Optional new title for the duplicate. If blank, keeps the original title.", required = false) String title) {
        log.debug("MCP: Duplicate item: worldId={}, itemId={}, title={}", worldId, itemId, title);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId and itemId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            WItem duplicated = itemService.duplicate(wid, itemId, title);
            return Map.of(
                    "itemId", duplicated.getItemId(),
                    "worldId", duplicated.getWorldId(),
                    "sourceItemId", itemId,
                    "status", "duplicated"
            );
        } catch (IllegalArgumentException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    @Tool(name = "update_items_by_type", description = "Batch update parameters for all items matching a specific itemType. Merges the given parameters into each item's existing parameters.")
    public Map<String, Object> updateItemsByType(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "ItemType to match (e.g. 'sword_iron', 'armor_boots_gold')") String itemType,
            @ToolParam(description = "Parameters to merge into each matching item") Map<String, String> parameters) {
        log.debug("MCP: Update items by type: worldId={}, itemType={}, parameters={}", worldId, itemType, parameters);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemType)) {
            throw new McpToolException("worldId and itemType are required");
        }
        if (parameters == null || parameters.isEmpty()) {
            throw new McpToolException("parameters are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        int count = itemService.updateParametersByItemType(wid, itemType, parameters);
        return Map.of(
                "worldId", worldId,
                "itemType", itemType,
                "updatedCount", count
        );
    }

    @Tool(name = "update_item", description = "Update parameters and/or server properties on an existing item by itemId. Merges the given values into the item's existing maps.")
    public Map<String, Object> updateItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item ID to update") String itemId,
            @ToolParam(description = "Parameters to merge into the item's public parameters", required = false) Map<String, String> parameters,
            @ToolParam(description = "Server-side parameters to merge into the item's server map (e.g. action, effects)", required = false) Map<String, String> server) {
        log.debug("MCP: Update item: worldId={}, itemId={}, parameters={}, server={}", worldId, itemId, parameters, server);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId and itemId are required");
        }
        if ((parameters == null || parameters.isEmpty()) && (server == null || server.isEmpty())) {
            throw new McpToolException("At least one of parameters or server is required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        WItem item = itemService.findByItemId(wid, itemId)
                .orElseThrow(() -> new McpToolException("Item not found: " + itemId));

        if (parameters != null && !parameters.isEmpty()) {
            if (item.getPublicData() != null) {
                var existing = item.getPublicData().getParameters();
                if (existing == null) {
                    item.getPublicData().setParameters(new LinkedHashMap<>(parameters));
                } else {
                    existing.putAll(parameters);
                }
            }
        }

        if (server != null && !server.isEmpty()) {
            if (item.getServer() == null) {
                item.setServer(new LinkedHashMap<>(server));
            } else {
                item.getServer().putAll(server);
            }
        }

        itemService.saveEntity(item);

        return Map.of(
                "itemId", item.getItemId(),
                "worldId", item.getWorldId(),
                "status", "updated"
        );
    }

    @Tool(name = "rename_item", description = "Rename the itemId of an existing item. The new itemId must not already exist in the same world.")
    public Map<String, Object> renameItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Current item ID") String oldItemId,
            @ToolParam(description = "New item ID") String newItemId) {
        log.debug("MCP: Rename item: worldId={}, oldItemId={}, newItemId={}", worldId, oldItemId, newItemId);

        if (Strings.isBlank(worldId) || Strings.isBlank(oldItemId) || Strings.isBlank(newItemId)) {
            throw new McpToolException("worldId, oldItemId, and newItemId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            WItem renamed = itemService.renameItemId(wid, oldItemId, newItemId);
            return Map.of(
                    "oldItemId", oldItemId,
                    "newItemId", renamed.getItemId(),
                    "worldId", renamed.getWorldId(),
                    "status", "renamed"
            );
        } catch (IllegalArgumentException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    @Tool(name = "delete_item", description = "Delete an item by its itemId.")
    public Map<String, Object> deleteItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Item ID to delete") String itemId) {
        log.debug("MCP: Delete item: worldId={}, itemId={}", worldId, itemId);

        if (Strings.isBlank(worldId) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId and itemId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        boolean deleted = itemService.delete(wid, itemId);
        if (!deleted) {
            throw new McpToolException("Item not found: " + itemId);
        }

        return Map.of(
                "deleted", true,
                "itemId", itemId
        );
    }
}
