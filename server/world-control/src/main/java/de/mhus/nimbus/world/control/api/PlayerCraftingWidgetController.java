package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.CraftingService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.SpellWordService;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for the crafting widget.
 * Accessed by players via /control/player/crafting-widget.
 * Uses WProgress as contract item (type "crafting-station") to validate access.
 */
@RestController
@RequestMapping("/control/player/crafting-widget")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Crafting Widget", description = "Crafting station widget for players")
public class PlayerCraftingWidgetController extends BaseEditorController {

    private final WProgressService progressService;
    private final RCharacterService characterService;
    private final CraftingService craftingService;
    private final SpellWordService spellWordService;
    private final WItemService wItemService;

    /**
     * Get crafting station config and player data via progress reference.
     */
    @GetMapping
    @Operation(summary = "Get crafting station config via progress reference")
    public ResponseEntity<?> getStation(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        // Validate WProgress contract
        var progress = validateProgress(progressId, worldId, userId);
        if (progress == null) {
            return notFound("Crafting station not found");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        var character = characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
        if (character == null) return notFound("Character not found");

        String category = (String) progress.getProgressData().getOrDefault("category", "");
        int slots = progress.getProgressData().get("slots") instanceof Number n ? n.intValue() : 4;
        boolean allowSpells = Boolean.TRUE.equals(progress.getProgressData().get("allowSpells"));

        // Get backpack items with details
        List<Map<String, Object>> backpackItems = buildBackpackItemList(character, parsedWorldId);

        // Get spell words if allowed
        List<Map<String, Object>> spellWordList = new ArrayList<>();
        if (allowSpells) {
            String regionWorldId = parsedWorldId.toRegionCollection().getId();
            var definitions = spellWordService.findAllDefinitions(regionWorldId);
            Map<String, Integer> learnedWords = character.getSpellWords();
            for (var def : definitions) {
                Integer xp = learnedWords.get(def.getName());
                if (xp == null) continue;
                Map<String, Object> word = new LinkedHashMap<>();
                word.put("name", def.getName());
                word.put("title", def.getTitle());
                word.put("category", def.getType());
                word.put("level", RCharacterService.calculateSpellWordLevel(xp));
                spellWordList.add(word);
            }
        }

        // Get crafting level for this category
        int craftingLevel = character.getSkills().getOrDefault("crafting_" + category, 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("slots", slots);
        result.put("allowSpells", allowSpells);
        result.put("craftingLevel", craftingLevel);
        result.put("backpackItems", backpackItems);
        result.put("spellWords", spellWordList);

        return ResponseEntity.ok(result);
    }

    /**
     * Try to match materials to a recipe (no consumption).
     */
    @PostMapping("/try")
    @Operation(summary = "Try to find a matching recipe for materials")
    public ResponseEntity<?> tryRecipe(
            @RequestParam String progressId,
            @RequestBody Map<String, Integer> materials,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var progress = validateProgress(progressId, worldId, userId);
        if (progress == null) return notFound("Crafting station not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        var character = characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
        if (character == null) return notFound("Character not found");

        String category = (String) progress.getProgressData().getOrDefault("category", "");
        int craftingLevel = character.getSkills().getOrDefault("crafting_" + category, 0);
        String regionWorldId = parsedWorldId.toRegionCollection().getId();

        var match = craftingService.findMatchingRecipe(regionWorldId, materials, category, craftingLevel);

        if (match.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        // Load recipe details for preview
        var recipeDef = craftingService.findRecipeData(regionWorldId, match.get());
        if (recipeDef.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        var recipe = recipeDef.get();

        // Load result item details for preview
        String resultTitle = recipe.getResultItemId();
        String resultTexture = null;
        var resultItem = wItemService.findByItemId(parsedWorldId, recipe.getResultItemId());
        if (resultItem.isPresent() && resultItem.get().getPublicData() != null) {
            var pd = resultItem.get().getPublicData();
            if (pd.getTitle() != null) resultTitle = pd.getTitle();
            resultTexture = pd.getTexture();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", true);
        response.put("recipeName", match.get());
        response.put("resultItemId", recipe.getResultItemId());
        response.put("resultTitle", resultTitle);
        response.put("resultTexture", resultTexture);
        response.put("resultAmount", recipe.getResultAmount());
        response.put("allowSpells", recipe.isAllowSpells());
        response.put("allowedSpellWords", recipe.getAllowedSpellWords() != null ? recipe.getAllowedSpellWords() : List.of());
        return ResponseEntity.ok(response);
    }

    /**
     * Execute crafting.
     */
    @PostMapping("/craft")
    @Operation(summary = "Execute crafting")
    public ResponseEntity<?> craft(
            @RequestParam String progressId,
            @RequestBody CraftRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.recipeName())) {
            return bad("recipeName required");
        }

        var progress = validateProgress(progressId, worldId, userId);
        if (progress == null) return notFound("Crafting station not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        var character = characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
        if (character == null) return notFound("Character not found");

        // Validate spell words
        if (body.spellWords() != null && !body.spellWords().isEmpty()) {
            if (body.spellWords().size() > 3) return bad("Maximum 3 spell words");
            for (String word : body.spellWords()) {
                if (!character.getSpellWords().containsKey(word)) {
                    return bad("Spell word not learned: " + word);
                }
            }
        }

        String playerId = userId + ":" + characterId;
        var result = craftingService.craft(worldId, parsedWorldId.toRegionCollection(),
                character.getId(), playerId, body.recipeName(), body.spellWords());

        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Crafting fehlgeschlagen"));
        }

        // Load result item details
        String resultItemId = result.get();
        String resultTitle = resultItemId;
        String resultTexture = null;
        var resultItem = wItemService.findByItemId(parsedWorldId, resultItemId);
        if (resultItem.isPresent() && resultItem.get().getPublicData() != null) {
            var pd = resultItem.get().getPublicData();
            if (pd.getTitle() != null) resultTitle = pd.getTitle();
            resultTexture = pd.getTexture();
        }

        // Reload backpack for updated view
        var updated = characterService.getCharacter(character.getId()).orElse(character);
        List<Map<String, Object>> backpackItems = buildBackpackItemList(updated, parsedWorldId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("resultItemId", resultItemId);
        response.put("resultTitle", resultTitle);
        response.put("resultTexture", resultTexture);
        response.put("backpackItems", backpackItems);
        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> buildBackpackItemList(RCharacter character, WorldId worldId) {
        Map<String, Integer> itemIds = character.getBackpack() != null && character.getBackpack().getItemIds() != null
                ? character.getBackpack().getItemIds()
                : Map.of();

        List<Map<String, Object>> items = new ArrayList<>();
        for (var entry : itemIds.entrySet()) {
            Map<String, Object> itemInfo = new LinkedHashMap<>();
            itemInfo.put("itemId", entry.getKey());
            itemInfo.put("count", entry.getValue());

            // Load item details for texture and title
            var wItem = wItemService.findByItemId(worldId, entry.getKey());
            if (wItem.isPresent()) {
                Item publicData = wItem.get().getPublicData();
                itemInfo.put("name", publicData.getTitle() != null ? publicData.getTitle() : entry.getKey());
                itemInfo.put("texture", publicData.getTexture());
            } else {
                itemInfo.put("name", entry.getKey());
                itemInfo.put("texture", null);
            }
            items.add(itemInfo);
        }
        return items;
    }

    private WProgress validateProgress(String progressId, String worldId, String userId) {
        var progressOpt = progressService.findByProgressId(progressId);
        if (progressOpt.isEmpty()) {
            log.warn("Progress not found: progressId={}", progressId);
            return null;
        }

        var progress = progressOpt.get();
        if (!progress.getWorldId().equals(worldId)) {
            log.warn("Progress worldId mismatch: expected={}, actual={}", worldId, progress.getWorldId());
            return null;
        }
        // playerId can be "@userId:characterName" or "userId:characterName" or just "userId"
        String playerId = progress.getPlayerId();
        if (!playerId.equals(userId)
                && !playerId.startsWith(userId + ":")
                && !playerId.equals("@" + userId)
                && !playerId.startsWith("@" + userId + ":")) {
            log.warn("Progress playerId mismatch: userId={}, playerId={}", userId, playerId);
            return null;
        }
        if (!"crafting-station".equals(progress.getType())) {
            log.warn("Progress type mismatch: expected=crafting-station, actual={}", progress.getType());
            return null;
        }

        return progress;
    }

    record CraftRequest(String recipeName, List<String> spellWords) {}
}
