package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.world.CraftingRecipeDefinition;
import de.mhus.nimbus.world.shared.world.SpellWordService;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for the crafting system.
 * <p>
 * Recipes are stored in WAnything collection "craftingRecipes" (worldId = region collection ID).
 * Known recipes are tracked via WProgress (type = "crafting", quest = recipe name).
 * Spell items are auto-generated as WItems with "baseItem:spell1Lvl_spell2Lvl_spell3Lvl" naming.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CraftingService {

    public static final String COLLECTION_CRAFTING_RECIPES = "craftingRecipes";
    public static final String PROGRESS_TYPE_CRAFTING = "crafting";

    private final WAnythingService anythingService;
    private final WItemService itemService;
    private final WProgressService progressService;
    private final SpellWordService spellWordService;
    private final RCharacterService characterService;

    // ── Recipe Definitions ──────────────────────────────────────────────

    /**
     * Get all crafting recipes for a region.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findAllRecipes(String regionWorldId) {
        return anythingService.findByWorldIdAndCollectionAndEnabled(regionWorldId, COLLECTION_CRAFTING_RECIPES, true);
    }

    /**
     * Get crafting recipes filtered by category (station type).
     */
    @Transactional(readOnly = true)
    public List<WAnything> findRecipesByCategory(String regionWorldId, String category) {
        return anythingService.findByWorldIdAndCollectionAndType(regionWorldId, COLLECTION_CRAFTING_RECIPES, category);
    }

    /**
     * Get a single recipe definition by name.
     */
    @Transactional(readOnly = true)
    public Optional<CraftingRecipeDefinition> findRecipeData(String regionWorldId, String recipeName) {
        return anythingService.findByWorldIdAndCollectionAndName(regionWorldId, COLLECTION_CRAFTING_RECIPES, recipeName)
                .flatMap(a -> a.getDataAs(CraftingRecipeDefinition.class));
    }

    // ── Known Recipes (WProgress) ───────────────────────────────────────

    /**
     * Check if a player knows a recipe.
     */
    @Transactional(readOnly = true)
    public boolean isRecipeKnown(String worldId, String playerId, String recipeName) {
        return progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, PROGRESS_TYPE_CRAFTING, recipeName)
                .isPresent();
    }

    /**
     * Get all known recipe names for a player.
     */
    @Transactional(readOnly = true)
    public List<String> getKnownRecipeNames(String worldId, String playerId) {
        return progressService.findByWorldIdAndPlayerIdAndType(worldId, playerId, PROGRESS_TYPE_CRAFTING)
                .stream()
                .map(WProgress::getQuest)
                .collect(Collectors.toList());
    }

    /**
     * Mark a recipe as known for a player.
     */
    @Transactional
    public void learnRecipe(String worldId, String playerId, String recipeName, String recipeTitle) {
        if (isRecipeKnown(worldId, playerId, recipeName)) return;
        progressService.save(worldId, playerId, PROGRESS_TYPE_CRAFTING, recipeName, recipeTitle, Map.of("learnedBy", "crafting"));
        log.info("Player {} learned recipe: {}", playerId, recipeName);
    }

    // ── Crafting ────────────────────────────────────────────────────────

    /**
     * Try to find a matching recipe for the given materials.
     *
     * @param regionWorldId  region-scoped worldId
     * @param materials      materials the player placed (itemId -> amount)
     * @param category       station category filter
     * @param craftingLevel  player's crafting level for this category
     * @return matching recipe name or empty
     */
    @Transactional(readOnly = true)
    public Optional<String> findMatchingRecipe(String regionWorldId, Map<String, Integer> materials, String category, int craftingLevel) {
        var recipes = findRecipesByCategory(regionWorldId, category);
        for (WAnything recipeEntity : recipes) {
            var recipeDef = recipeEntity.getDataAs(CraftingRecipeDefinition.class);
            if (recipeDef.isEmpty()) continue;
            var recipe = recipeDef.get();
            if (recipe.getMinLevel() > craftingLevel) continue;
            if (materialsMatch(materials, recipe.getMaterials())) {
                return Optional.of(recipeEntity.getName());
            }
        }
        return Optional.empty();
    }

    /**
     * Execute crafting: consume materials, create result item, learn recipe, grant XP.
     *
     * @param worldId        the world instance ID (for WProgress)
     * @param regionWorldId  region-scoped worldId (for WAnything/WItem lookups)
     * @param characterId    MongoDB character document id
     * @param playerId       player identifier for WProgress
     * @param recipeName     recipe name
     * @param spellWords     optional spell words (up to 3: element, form, modifier). Null or empty for no spell.
     * @return the result item ID (with spell suffix if applicable), or empty on failure
     */
    @Transactional
    public Optional<String> craft(String worldId, WorldId regionWorldId, String characterId, String playerId,
                                   String recipeName, List<String> spellWords) {

        // Load recipe
        var recipeDef = findRecipeData(regionWorldId.getId(), recipeName);
        if (recipeDef.isEmpty()) {
            log.warn("Recipe not found: {}", recipeName);
            return Optional.empty();
        }
        var recipe = recipeDef.get();

        // Verify ALL materials are available before consuming any. MongoTemplate
        // writes commit immediately (no active Mongo transaction), so a per-item
        // removal loop that fails midway would irreversibly lose already-removed
        // materials without producing a result item.
        var craftingChar = characterService.getCharacter(characterId).orElse(null);
        if (craftingChar == null) {
            log.warn("Crafting failed: character not found: {}", characterId);
            return Optional.empty();
        }
        Map<String, Integer> backpackItems = craftingChar.getBackpack() != null
                ? craftingChar.getBackpack().getItemIds() : Map.of();
        for (var entry : recipe.getMaterials().entrySet()) {
            if (backpackItems.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                log.warn("Crafting failed: insufficient material {} x{} for character {}",
                        entry.getKey(), entry.getValue(), characterId);
                return Optional.empty();
            }
        }

        // Remove materials. If a concurrent backpack change makes a removal fail,
        // restore the already-removed materials and abort (compensation).
        List<Map.Entry<String, Integer>> removedMaterials = new ArrayList<>();
        for (var entry : recipe.getMaterials().entrySet()) {
            if (characterService.removeBackpackItem(characterId, entry.getKey(), entry.getValue())) {
                removedMaterials.add(entry);
            } else {
                for (var done : removedMaterials) {
                    characterService.addBackpackItem(characterId, done.getKey(), done.getValue());
                }
                log.warn("Crafting aborted: insufficient material {} x{} for character {} (concurrent change)",
                        entry.getKey(), entry.getValue(), characterId);
                return Optional.empty();
            }
        }

        // Determine result item ID
        String resultItemId = recipe.getResultItemId();
        if (spellWords != null && !spellWords.isEmpty() && recipe.isAllowSpells()) {
            resultItemId = buildSpellItemId(resultItemId, spellWords, characterId);
        }

        // Ensure spell WItem exists
        if (resultItemId.contains(":")) {
            ensureSpellItemExists(regionWorldId, resultItemId, recipe.getResultItemId(), spellWords, characterId);
        }

        // Add result to backpack
        characterService.addBackpackItem(characterId, resultItemId, recipe.getResultAmount());

        // Learn recipe if not known
        var recipeEntity = anythingService.findByWorldIdAndCollectionAndName(
                regionWorldId.getId(), COLLECTION_CRAFTING_RECIPES, recipeName);
        String title = recipeEntity.map(WAnything::getTitle).orElse(recipeName);
        learnRecipe(worldId, playerId, recipeName, title);

        // Grant spell word XP
        if (spellWords != null && recipe.getSpellWordXpReward() > 0) {
            for (String word : spellWords) {
                spellWordService.addWordXp(characterId, word, recipe.getSpellWordXpReward());
            }
        }

        log.info("Crafting success: character={}, recipe={}, result={}", characterId, recipeName, resultItemId);
        return Optional.of(resultItemId);
    }

    // ── Internal ────────────────────────────────────────────────────────

    /**
     * Build the spell item ID from base item + spell words + levels.
     * Format: "baseItem:word1Level_word2Level_word3Level" (sorted alphabetically by word)
     */
    String buildSpellItemId(String baseItemId, List<String> words, String characterId) {
        var character = characterService.getCharacter(characterId);
        if (character.isEmpty()) return baseItemId;
        var charSpellWords = character.get().getSpellWords();

        String suffix = words.stream()
                .sorted()
                .map(word -> {
                    int level = spellWordService.getWordLevel(charSpellWords, word);
                    return word + (level >= 0 ? level : 0);
                })
                .collect(Collectors.joining("_"));

        return baseItemId + ":" + suffix;
    }

    /**
     * Ensure the spell variant WItem exists. If not, create it based on the base item
     * with spell properties merged in.
     */
    void ensureSpellItemExists(WorldId regionWorldId, String spellItemId, String baseItemId,
                                List<String> words, String characterId) {
        if (itemService.findByItemId(regionWorldId, spellItemId).isPresent()) return;

        var baseItem = itemService.findByItemId(regionWorldId, baseItemId);
        if (baseItem.isEmpty()) {
            log.warn("Base item not found for spell item creation: {}", baseItemId);
            return;
        }

        var character = characterService.getCharacter(characterId);
        if (character.isEmpty()) return;
        var charSpellWords = character.get().getSpellWords();

        // Build spell parameters from word definitions
        Map<String, String> spellParams = new HashMap<>();
        for (String word : words) {
            int level = spellWordService.getWordLevel(charSpellWords, word);
            var definition = spellWordService.findDefinitionData(regionWorldId.getId(), word);
            if (definition.isPresent() && definition.get().getProperties() != null) {
                for (var prop : definition.get().getProperties().entrySet()) {
                    String value = scalePropertyValue(prop.getValue(), level);
                    spellParams.put(prop.getKey(), value);
                }
            }
        }

        // Clone base item with spell properties
        Item basePublicData = baseItem.get().getPublicData();
        Item spellPublicData = Item.builder()
                .name(spellItemId)
                .itemType(basePublicData.getItemType())
                .type(basePublicData.getType())
                .title(basePublicData.getTitle())
                .description(basePublicData.getDescription())
                .texture(basePublicData.getTexture())
                .scaleX(basePublicData.getScaleX())
                .scaleY(basePublicData.getScaleY())
                .offset(basePublicData.getOffset())
                .color(basePublicData.getColor())
                .pose(basePublicData.getPose())
                .onUseEffect(basePublicData.getOnUseEffect())
                .actionScript(basePublicData.getActionScript())
                .actionTargeting(basePublicData.getActionTargeting())
                .exclusive(basePublicData.getExclusive())
                .generic(basePublicData.getGeneric())
                .parameters(new HashMap<>())
                .build();

        if (basePublicData.getParameters() != null) {
            spellPublicData.getParameters().putAll(basePublicData.getParameters());
        }
        spellPublicData.getParameters().putAll(spellParams);

        itemService.create(regionWorldId, spellPublicData);
        log.info("Created spell item: {}", spellItemId);
    }

    /**
     * Scale a property value by word level. Numeric values are multiplied by (1 + level * 0.5).
     */
    String scalePropertyValue(Object value, int level) {
        if (value instanceof Number num) {
            double scaled = num.doubleValue() * (1.0 + level * 0.5);
            if (value instanceof Integer || value instanceof Long) {
                return String.valueOf(Math.round(scaled));
            }
            return String.valueOf(scaled);
        }
        return String.valueOf(value);
    }

    /**
     * Check if provided materials exactly match the recipe requirements.
     */
    boolean materialsMatch(Map<String, Integer> provided, Map<String, Integer> required) {
        if (required == null || required.isEmpty()) return false;
        if (provided == null || provided.size() != required.size()) return false;
        for (var entry : required.entrySet()) {
            Integer providedAmount = provided.get(entry.getKey());
            if (providedAmount == null || !providedAmount.equals(entry.getValue())) return false;
        }
        return true;
    }

}
