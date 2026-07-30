package de.mhus.nimbus.world.generator.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.world.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * Service that uses AI to categorize items and calculate base prices.
 * Processes items in batches, asking AI for ItemTier, RarityCategory,
 * and material price estimation. Then calculates basePrice from components.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPriceGeneratorService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private static final String DEFAULT_AI_MODEL = "default:chat";

    private final WItemService itemService;
    private final AiModelService aiModelService;

    /**
     * Generate prices for all items in a world.
     *
     * @param worldId   the world to process
     * @param aiModel   AI model name (e.g., "default:chat", "openai:gpt-4")
     * @param batchSize items per AI query
     * @return summary of processed items
     */
    public Map<String, Object> generatePrices(WorldId worldId, String aiModel, int batchSize) {
        if (aiModel == null || aiModel.isBlank()) aiModel = DEFAULT_AI_MODEL;
        if (batchSize <= 0) batchSize = 15;

        List<WItem> items = itemService.findEnabledByWorldId(worldId);
        if (items.isEmpty()) {
            return Map.of("status", "no items found", "worldId", worldId.getId());
        }

        log.info("Starting price generation for {} items in world {}", items.size(), worldId.getId());

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        // Process in batches
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            List<WItem> batch = items.subList(i, end);

            try {
                processBatch(batch, aiModel, worldId);
                processed += batch.size();
            } catch (Exception e) {
                log.error("Batch {}-{} failed: {}", i, end, e.getMessage(), e);
                failed += batch.size();
                errors.add("Batch " + i + "-" + end + ": " + e.getMessage());
            }
        }

        log.info("Price generation complete: {} processed, {} failed", processed, failed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("worldId", worldId.getId());
        result.put("totalItems", items.size());
        result.put("processed", processed);
        result.put("failed", failed);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    private void processBatch(List<WItem> batch, String aiModel, WorldId worldId) {
        // Build item descriptions for AI
        List<Map<String, String>> itemDescriptions = new ArrayList<>();
        for (WItem item : batch) {
            Map<String, String> desc = new LinkedHashMap<>();
            desc.put("itemId", item.getName());
            if (item.getPublicData() != null) {
                desc.put("name", item.getPublicData().getName());
                desc.put("type", item.getPublicData().getType());
                desc.put("itemType", item.getPublicData().getItemType());
                desc.put("description", item.getPublicData().getDescription());
            }
            itemDescriptions.add(desc);
        }

        String itemsJson;
        try {
            itemsJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(itemDescriptions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize item descriptions", e);
        }

        // Ask AI to categorize
        String prompt = buildCategorizationPrompt(itemsJson);
        String response = askAi(aiModel, prompt);
        List<AiItemCategorization> categorizations = parseResponse(response);

        // Map results by itemId
        Map<String, AiItemCategorization> catMap = new HashMap<>();
        for (AiItemCategorization cat : categorizations) {
            if (cat.itemId != null) {
                catMap.put(cat.itemId, cat);
            }
        }

        // Apply categorizations and calculate prices
        for (WItem item : batch) {
            AiItemCategorization cat = catMap.get(item.getName());
            if (cat == null) {
                log.warn("No AI categorization for item {}", item.getName());
                continue;
            }

            applyCategorizationToItem(item, cat);
            itemService.saveEntity(item);
        }
    }

    private void applyCategorizationToItem(WItem item, AiItemCategorization cat) {
        // Set tier
        ItemTier tier = parseItemTier(cat.itemTier);
        item.setItemTier(tier);

        // Set rarity
        RarityCategory rarity = parseRarityCategory(cat.rarityCategory);
        item.setRarityCategory(rarity);

        // Set material price from AI estimate
        double materialPrice = cat.materialPrice != null ? cat.materialPrice : 1.0;
        item.setMaterialPrice(materialPrice);

        // Calculate crafting cost based on tier
        double craftingCost = calculateCraftingCost(tier);
        item.setCraftingCost(craftingCost);

        // Calculate usage bonus from AI estimate or item effects
        double usageBonus = cat.usageBonus != null ? cat.usageBonus : 0.0;
        item.setUsageBonus(usageBonus);

        // Calculate rarity bonus
        double rarityBonus = calculateRarityBonus(rarity);
        item.setRarityBonus(rarityBonus);

        // Calculate base price
        double basePrice = materialPrice + craftingCost + usageBonus + rarityBonus;
        item.setBasePrice(basePrice);

        log.debug("Item {} priced: tier={}, rarity={}, basePrice={} (mat={}, craft={}, use={}, rare={})",
                item.getName(), tier, rarity, basePrice, materialPrice, craftingCost, usageBonus, rarityBonus);
    }

    private double calculateCraftingCost(ItemTier tier) {
        return switch (tier) {
            case NONE -> 0;
            case LEATHER -> 2;
            case IRON -> 4;
            case STEEL -> 7;
            case SILVER -> 10;
            case GOLD -> 15;
            case MYTHRIL -> 25;
            case ADAMANT -> 40;
            case ORICHALCUM -> 60;
        };
    }

    private double calculateRarityBonus(RarityCategory rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case UNCOMMON -> 3;
            case RARE -> 10;
            case EPIC -> 30;
            case LEGENDARY -> 80;
            case MYTHIC -> 200;
        };
    }

    private ItemTier parseItemTier(String value) {
        if (value == null || value.isBlank()) return ItemTier.NONE;
        try {
            return ItemTier.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown ItemTier: {}", value);
            return ItemTier.NONE;
        }
    }

    private RarityCategory parseRarityCategory(String value) {
        if (value == null || value.isBlank()) return RarityCategory.COMMON;
        try {
            return RarityCategory.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown RarityCategory: {}", value);
            return RarityCategory.COMMON;
        }
    }

    private String askAi(String aiModel, String prompt) {
        AiChatOptions options = AiChatOptions.builder()
                .systemMessage(buildSystemPrompt())
                .temperature(0.3)
                .maxTokens(4000)
                .timeoutSeconds(120)
                .build();

        Optional<AiChat> chatOpt = aiModelService.createChat(aiModel, options);
        if (chatOpt.isEmpty()) {
            throw new RuntimeException("AI model not available: " + aiModel);
        }

        try {
            return chatOpt.get().ask(prompt);
        } catch (Exception e) {
            throw new RuntimeException("AI query failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a game designer assistant for an RPG game. Your task is to categorize items \
                and estimate their economic value. You must respond with valid JSON only, no explanations.

                ItemTier values (material/quality level): \
                NONE, LEATHER, IRON, STEEL, SILVER, GOLD, MYTHRIL, ADAMANT, ORICHALCUM

                RarityCategory values: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC

                Guidelines for materialPrice (in silver coins):
                - Basic materials (wood, herbs, cloth): 1-3
                - Common metals (iron, copper): 4-7
                - Refined materials (steel, leather): 8-12
                - Precious materials (silver, gold): 13-20
                - Rare materials (mythril, gems): 25-50
                - Legendary materials (adamant, orichalcum): 60-100

                Guidelines for usageBonus:
                - No direct combat/utility use: 0
                - Basic weapon/armor/tool: 3-8
                - Good weapon/armor/tool: 10-20
                - Powerful weapon/armor/tool: 25-50
                - Potions/consumables: 2-15 based on effect strength
                """;
    }

    private String buildCategorizationPrompt(String itemsJson) {
        return """
                Categorize the following RPG items. For each item, determine:
                - itemTier: the material/quality tier
                - rarityCategory: how rare this item is
                - materialPrice: estimated base material value in silver
                - usageBonus: bonus value from the item's utility (combat stats, healing, etc.)

                Items to categorize:
                %s

                Respond with a JSON array. Each element must have:
                {"itemId": "...", "itemTier": "...", "rarityCategory": "...", "materialPrice": number, "usageBonus": number}

                Respond with ONLY the JSON array, no markdown, no explanation.
                """.formatted(itemsJson);
    }

    private List<AiItemCategorization> parseResponse(String response) {
        try {
            String json = response.trim();
            // Strip markdown code blocks if present
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            // Find array boundaries
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            return MAPPER.readValue(json, new TypeReference<List<AiItemCategorization>>() {});
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", response, e);
            throw new RuntimeException("Failed to parse AI categorization response: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiItemCategorization(
            String itemId,
            String itemTier,
            String rarityCategory,
            Double materialPrice,
            Double usageBonus
    ) {}
}
