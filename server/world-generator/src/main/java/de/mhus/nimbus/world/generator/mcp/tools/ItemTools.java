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
                map.put("title", e.getPublicData().getTitle() != null ? e.getPublicData().getTitle() : "");
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
        return result;
    }

    @Tool(name = "create_item", description = "Create or update an item. An item is an instance referencing an itemType. If no itemId is provided, one is auto-generated.")
    public Map<String, Object> createItem(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Optional item ID. If not provided, an ID is auto-generated.", required = false) String itemId,
            @ToolParam(description = "Reference to an itemType ID (e.g. 'iron_sword', 'health_potion')") String itemType,
            @ToolParam(description = "Display title (e.g. 'Iron Sword')") String title,
            @ToolParam(description = "Item description", required = false) String description,
            @ToolParam(description = "Additional parameters as key-value pairs", required = false) Map<String, Object> parameters) {
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
            WItem saved;
            if (Strings.isBlank(itemId)) {
                Item publicData = Item.builder()
                        .itemType(itemType)
                        .title(title)
                        .description(description)
                        .parameters(parameters)
                        .build();
                saved = itemService.create(wid, publicData);
            } else {
                Item publicData = Item.builder()
                        .name(itemId)
                        .itemType(itemType)
                        .title(title)
                        .description(description)
                        .parameters(parameters)
                        .build();
                saved = itemService.save(wid, itemId, publicData);
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
