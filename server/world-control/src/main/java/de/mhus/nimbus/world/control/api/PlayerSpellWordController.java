package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
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
@RequestMapping("/control/player/spell-words")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Spell Words", description = "Spell word management for players")
public class PlayerSpellWordController extends BaseEditorController {

    private final RCharacterService characterService;
    private final SpellWordService spellWordService;

    @GetMapping
    @Operation(summary = "Get all learned spell words with XP, levels, and definitions")
    public ResponseEntity<?> getSpellWords(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");
        String regionWorldId = parsedWorldId.toRegionCollection().getId();

        // Load all spell word definitions for this region
        List<WAnything> definitions = spellWordService.findAllDefinitions(regionWorldId);

        Map<String, Integer> learnedWords = character.getSpellWords();

        // Build response: for each learned word, include definition + XP + level
        List<Map<String, Object>> words = new ArrayList<>();
        for (WAnything def : definitions) {
            Integer xp = learnedWords.get(def.getName());
            if (xp == null) continue; // not learned

            Map<String, Object> word = new LinkedHashMap<>();
            word.put("name", def.getName());
            word.put("title", def.getTitle());
            word.put("description", def.getDescription());
            word.put("category", def.getType()); // type = category (element/form/modifier)
            word.put("xp", xp);
            word.put("level", RCharacterService.calculateSpellWordLevel(xp));

            def.getDataAs(de.mhus.nimbus.world.shared.world.SpellWordDefinition.class)
                    .ifPresent(data -> {
                        word.put("icon", data.getIcon());
                        word.put("properties", data.getProperties());
                    });

            words.add(word);
        }

        // Group by category
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("words", words);
        result.put("totalLearned", words.size());
        result.put("totalAvailable", definitions.size());

        return ResponseEntity.ok(result);
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        return characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
    }

}
