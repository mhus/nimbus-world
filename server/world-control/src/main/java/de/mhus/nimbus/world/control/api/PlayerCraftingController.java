package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.CraftingService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.CraftingRecipeDefinition;
import de.mhus.nimbus.world.shared.world.SpellWordService;
import de.mhus.nimbus.world.shared.world.WAnything;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/control/player/crafting")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Crafting", description = "Crafting system for players")
public class PlayerCraftingController extends BaseEditorController {

    private final CraftingService craftingService;
    private final RCharacterService characterService;
    private final SpellWordService spellWordService;

    @GetMapping("/recipes")
    @Operation(summary = "Get known recipes for the player, optionally filtered by category")
    public ResponseEntity<?> getKnownRecipes(
            @RequestParam(required = false) String category,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");
        String regionWorldId = parsedWorldId.toRegionCollection().getId();
        String playerId = userId + ":" + characterId;

        // Get known recipe names
        List<String> knownNames = craftingService.getKnownRecipeNames(worldId, playerId);

        // Load recipe definitions and filter
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (String recipeName : knownNames) {
            var recipeEntity = craftingService.findRecipeData(regionWorldId, recipeName);
            if (recipeEntity.isEmpty()) continue;
            var recipe = recipeEntity.get();
            if (category != null && !category.equals(recipe.getCategory())) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", recipeName);
            entry.put("category", recipe.getCategory());
            entry.put("minLevel", recipe.getMinLevel());
            entry.put("allowSpells", recipe.isAllowSpells());
            entry.put("allowedSpellWords", recipe.getAllowedSpellWords());
            entry.put("materials", recipe.getMaterials());
            entry.put("resultItemId", recipe.getResultItemId());
            entry.put("resultAmount", recipe.getResultAmount());
            entry.put("successChance", recipe.getSuccessChance());
            recipes.add(entry);
        }

        return ResponseEntity.ok(Map.of("recipes", recipes));
    }

    @PostMapping("/try")
    @Operation(summary = "Try to find a matching recipe for given materials (free experimentation)")
    public ResponseEntity<?> tryRecipe(
            @RequestBody TryRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.materials() == null || body.materials().isEmpty()) {
            return bad("materials required");
        }
        if (Strings.isBlank(body.category())) {
            return bad("category required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) return notFound("Character not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");
        String regionWorldId = parsedWorldId.toRegionCollection().getId();

        // Get crafting level for this category from skills
        int craftingLevel = character.getSkills().getOrDefault("crafting_" + body.category(), 0);

        var match = craftingService.findMatchingRecipe(regionWorldId, body.materials(), body.category(), craftingLevel);

        if (match.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false, "message", "Diese Kombination ergibt nichts"));
        }

        return ResponseEntity.ok(Map.of("found", true, "recipeName", match.get()));
    }

    @PostMapping("/craft")
    @Operation(summary = "Execute crafting: consume materials, create result, learn recipe")
    public ResponseEntity<?> craft(
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

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) return notFound("Character not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        // Validate spell words if provided
        if (body.spellWords() != null && !body.spellWords().isEmpty()) {
            if (body.spellWords().size() > 3) {
                return bad("Maximum 3 spell words allowed");
            }
            Map<String, Integer> learned = character.getSpellWords();
            for (String word : body.spellWords()) {
                if (!learned.containsKey(word)) {
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

        return ResponseEntity.ok(Map.of(
                "success", true,
                "resultItemId", result.get()
        ));
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        return characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
    }

    record TryRequest(Map<String, Integer> materials, String category) {}
    record CraftRequest(String recipeName, List<String> spellWords) {}

}
