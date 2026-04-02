package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.TraderType;
import de.mhus.nimbus.world.shared.world.WTrader;
import de.mhus.nimbus.world.shared.world.WTraderService;
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
public class TraderTools implements McpToolBean {

    private final WTraderService traderService;

    @Tool(name = "list_traders", description = "List all traders for a world. Returns entityId, traderType, categories, silverAmount.")
    public Map<String, Object> listTraders(
            @ToolParam(description = "World ID (e.g. 'earth616:westview')") String worldId) {

        if (Strings.isBlank(worldId)) throw new McpToolException("worldId is required");

        List<WTrader> traders = traderService.findByWorldId(worldId);

        var dtos = traders.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entityId", t.getEntityId());
            map.put("traderType", t.getTraderType().name());
            map.put("categories", t.getCategories());
            map.put("silverAmount", t.getSilverAmount());
            map.put("chestId", t.getChestId());
            map.put("poolChestId", t.getPoolChestId());
            map.put("enabled", t.isEnabled());
            return map;
        }).toList();

        return Map.of("worldId", worldId, "count", dtos.size(), "traders", dtos);
    }

    @Tool(name = "get_trader", description = "Get a trader by world ID and entityId. Returns full trader data.")
    public Map<String, Object> getTrader(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Entity ID of the NPC") String entityId) {

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId))
            throw new McpToolException("worldId and entityId are required");

        WTrader trader = traderService.findByWorldIdAndEntityId(worldId, entityId)
                .orElseThrow(() -> new McpToolException("Trader not found: " + entityId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", trader.getEntityId());
        result.put("worldId", trader.getWorldId());
        result.put("traderType", trader.getTraderType().name());
        result.put("categories", trader.getCategories());
        result.put("personalityModifier", trader.getPersonalityModifier());
        result.put("silverAmount", trader.getSilverAmount());
        result.put("chestId", trader.getChestId());
        result.put("poolChestId", trader.getPoolChestId());
        result.put("questItems", trader.getQuestItems());
        result.put("maxDisplayItems", trader.getMaxDisplayItems());
        result.put("goldExchangeRate", trader.getGoldExchangeRate());
        result.put("trainableSkills", trader.getTrainableSkills());
        result.put("maxSkillPoints", trader.getMaxSkillPoints());
        result.put("costPerSkillPoint", trader.getCostPerSkillPoint());
        result.put("repairTypes", trader.getRepairTypes());
        result.put("repairCostPerPoint", trader.getRepairCostPerPoint());
        result.put("poolSyncIntervalSeconds", trader.getPoolSyncIntervalSeconds());
        result.put("enabled", trader.isEnabled());
        return result;
    }

    @Tool(name = "create_trader", description = "Create or update a trader linked to an NPC entity. Requires entityId, worldId, and at least chestId. The trader type defaults to MERCHANT.")
    public Map<String, Object> createTrader(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Entity ID of the NPC to link") String entityId,
            @ToolParam(description = "Trader type: MERCHANT, TRAINER, or SERVICE", required = false) String traderType,
            @ToolParam(description = "Item categories the trader handles (e.g. ['food', 'material'])", required = false) List<String> categories,
            @ToolParam(description = "Personality price modifier (positive=expensive, negative=cheap)", required = false) Double personalityModifier,
            @ToolParam(description = "Available silver for buying/selling", required = false) Long silverAmount,
            @ToolParam(description = "Chest name for the visible shop") String chestId,
            @ToolParam(description = "Chest name for the hidden pool", required = false) String poolChestId,
            @ToolParam(description = "Max items displayed to player (default 12)", required = false) Integer maxDisplayItems,
            @ToolParam(description = "Gold to silver exchange rate (default 10)", required = false) Double goldExchangeRate,
            @ToolParam(description = "Pool sync interval in seconds (default 3600)", required = false) Integer poolSyncIntervalSeconds) {

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId) || Strings.isBlank(chestId))
            throw new McpToolException("worldId, entityId, and chestId are required");

        TraderType type = TraderType.MERCHANT;
        if (!Strings.isBlank(traderType)) {
            try {
                type = TraderType.valueOf(traderType.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                throw new McpToolException("Invalid traderType: " + traderType);
            }
        }

        // Check if exists — update if so
        var existing = traderService.findByWorldIdAndEntityId(worldId, entityId);
        WTrader trader;
        if (existing.isPresent()) {
            trader = existing.get();
        } else {
            trader = WTrader.builder()
                    .worldId(worldId)
                    .entityId(entityId)
                    .build();
        }

        trader.setTraderType(type);
        trader.setChestId(chestId);
        if (poolChestId != null) trader.setPoolChestId(poolChestId);
        if (categories != null) trader.setCategories(categories);
        if (personalityModifier != null) trader.setPersonalityModifier(personalityModifier);
        if (silverAmount != null) trader.setSilverAmount(silverAmount);
        if (maxDisplayItems != null) trader.setMaxDisplayItems(maxDisplayItems);
        if (goldExchangeRate != null) trader.setGoldExchangeRate(goldExchangeRate);
        if (poolSyncIntervalSeconds != null) trader.setPoolSyncIntervalSeconds(poolSyncIntervalSeconds);

        trader = traderService.save(trader);

        return Map.of(
                "entityId", trader.getEntityId(),
                "worldId", trader.getWorldId(),
                "traderType", trader.getTraderType().name(),
                "status", existing.isPresent() ? "updated" : "created"
        );
    }

    @Tool(name = "delete_trader", description = "Delete a trader by world ID and entityId.")
    public Map<String, Object> deleteTrader(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Entity ID of the NPC") String entityId) {

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId))
            throw new McpToolException("worldId and entityId are required");

        boolean deleted = traderService.delete(worldId, entityId);
        if (!deleted) throw new McpToolException("Trader not found: " + entityId);

        return Map.of("entityId", entityId, "deleted", true);
    }
}
