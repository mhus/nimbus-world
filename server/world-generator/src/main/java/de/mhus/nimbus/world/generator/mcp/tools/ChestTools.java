package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestService;
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
public class ChestTools {

    private final WChestService chestService;

    @Tool(name = "list_chests", description = "List all chests for a world. Returns name, title, type, userId, capacity, and item count.")
    public Map<String, Object> listChests(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId) {
        log.debug("MCP: List chests: worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        List<WChest> entities = chestService.findByWorldId(worldId);

        var dtos = entities.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", e.getName());
            map.put("title", e.getTitle() != null ? e.getTitle() : "");
            map.put("type", e.getType() != null ? e.getType().name() : "");
            map.put("playerId", e.getPlayerId() != null ? e.getPlayerId() : "");
            map.put("capacity", e.getCapacity());
            map.put("itemCount", e.getItems() != null ? e.getItems().size() : 0);
            return map;
        }).toList();

        return Map.of(
                "worldId", worldId,
                "count", dtos.size(),
                "chests", dtos
        );
    }

    @Tool(name = "get_chest", description = "Get a chest by world ID and name. Returns full chest data including items.")
    public Map<String, Object> getChest(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId,
            @ToolParam(description = "Chest name (technical identifier)") String name) {
        log.debug("MCP: Get chest: worldId={}, name={}", worldId, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(name)) {
            throw new McpToolException("worldId and name are required");
        }

        WChest chest = chestService.getByWorldIdAndName(worldId, name)
                .orElseThrow(() -> new McpToolException("Chest not found: " + name));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", chest.getName());
        result.put("title", chest.getTitle());
        result.put("description", chest.getDescription());
        result.put("type", chest.getType() != null ? chest.getType().name() : null);
        result.put("playerId", chest.getPlayerId());
        result.put("capacity", chest.getCapacity());
        result.put("keyId", chest.getKeyId());
        result.put("lockPickingDifficulty", chest.getLockPickingDifficulty());
        result.put("items", chest.getItems());
        return result;
    }

    @Tool(name = "create_chest", description = "Create a new chest. Type must be REGION, WORLD, PLAYER, BANK, or TRANSFER. For PLAYER, BANK, and TRANSFER chests, playerId is required.")
    public Map<String, Object> createChest(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId,
            @ToolParam(description = "Unique chest name (technical identifier)") String name,
            @ToolParam(description = "Chest type: REGION, WORLD, or PLAYER") String type,
            @ToolParam(description = "Display title", required = false) String title,
            @ToolParam(description = "Description", required = false) String description,
            @ToolParam(description = "Player ID for PLAYER type chests (format: @userId:characterId)", required = false) String playerId,
            @ToolParam(description = "Maximum item capacity (default 10)", required = false) Integer capacity,
            @ToolParam(description = "Key item ID required to open", required = false) String keyId,
            @ToolParam(description = "Lock picking difficulty (0 = not possible)", required = false) Integer lockPickingDifficulty) {
        log.debug("MCP: Create chest: worldId={}, name={}, type={}", worldId, name, type);

        if (Strings.isBlank(worldId) || Strings.isBlank(name) || Strings.isBlank(type)) {
            throw new McpToolException("worldId, name, and type are required");
        }

        WChest.ChestType chestType;
        try {
            chestType = WChest.ChestType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new McpToolException("Invalid chest type: " + type + ". Must be REGION, WORLD, PLAYER, BANK, or TRANSFER");
        }

        if ((chestType == WChest.ChestType.PLAYER || chestType == WChest.ChestType.BANK || chestType == WChest.ChestType.TRANSFER)
                && Strings.isBlank(playerId)) {
            throw new McpToolException("playerId is required for " + chestType + " type chests");
        }

        try {
            WChest chest = chestService.createChest(worldId, name, title, description, playerId, chestType);
            if (capacity != null && capacity > 0) chest.setCapacity(capacity);
            else chest.setCapacity(10);
            if (Strings.isNotBlank(keyId)) chest.setKeyId(keyId);
            if (lockPickingDifficulty != null) chest.setLockPickingDifficulty(lockPickingDifficulty);
            chestService.save(chest);

            return Map.of(
                    "name", chest.getName(),
                    "worldId", worldId,
                    "type", chestType.name(),
                    "status", "created"
            );
        } catch (Exception e) {
            throw new McpToolException("Failed to create chest: " + e.getMessage());
        }
    }

    @Tool(name = "add_chest_item", description = "Add an item reference to a chest.")
    public Map<String, Object> addChestItem(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId,
            @ToolParam(description = "Chest name") String chestName,
            @ToolParam(description = "Item ID to add") String itemId,
            @ToolParam(description = "Display name for the item in the chest", required = false) String itemName,
            @ToolParam(description = "Texture path for the item", required = false) String texture,
            @ToolParam(description = "Amount (default 1)", required = false) Integer amount) {
        log.debug("MCP: Add item to chest: worldId={}, chestName={}, itemId={}", worldId, chestName, itemId);

        if (Strings.isBlank(worldId) || Strings.isBlank(chestName) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId, chestName, and itemId are required");
        }

        WChest chest = chestService.getByWorldIdAndName(worldId, chestName)
                .orElseThrow(() -> new McpToolException("Chest not found: " + chestName));

        ItemRef itemRef = ItemRef.builder()
                .itemId(itemId)
                .name(itemName)
                .texture(texture)
                .amount(amount != null && amount > 0 ? amount : 1)
                .build();

        chestService.addItem(chest.getId(), itemRef);

        return Map.of(
                "chestName", chestName,
                "itemId", itemId,
                "amount", itemRef.getAmount(),
                "status", "added"
        );
    }

    @Tool(name = "remove_chest_item", description = "Remove an item reference from a chest by item ID.")
    public Map<String, Object> removeChestItem(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId,
            @ToolParam(description = "Chest name") String chestName,
            @ToolParam(description = "Item ID to remove") String itemId) {
        log.debug("MCP: Remove item from chest: worldId={}, chestName={}, itemId={}", worldId, chestName, itemId);

        if (Strings.isBlank(worldId) || Strings.isBlank(chestName) || Strings.isBlank(itemId)) {
            throw new McpToolException("worldId, chestName, and itemId are required");
        }

        WChest chest = chestService.getByWorldIdAndName(worldId, chestName)
                .orElseThrow(() -> new McpToolException("Chest not found: " + chestName));

        chestService.removeItem(chest.getId(), itemId);

        return Map.of(
                "chestName", chestName,
                "itemId", itemId,
                "status", "removed"
        );
    }

    @Tool(name = "delete_chest", description = "Delete a chest by world ID and name.")
    public Map<String, Object> deleteChest(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist' or '@region:earth616')") String worldId,
            @ToolParam(description = "Chest name to delete") String name) {
        log.debug("MCP: Delete chest: worldId={}, name={}", worldId, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(name)) {
            throw new McpToolException("worldId and name are required");
        }

        WChest chest = chestService.getByWorldIdAndName(worldId, name)
                .orElseThrow(() -> new McpToolException("Chest not found: " + name));

        chestService.deleteChest(worldId, name);

        return Map.of(
                "deleted", true,
                "name", name
        );
    }
}
