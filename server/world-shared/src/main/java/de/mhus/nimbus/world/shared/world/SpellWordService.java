package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.world.shared.region.RCharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing spell word definitions and player spell word state.
 * <p>
 * Spell word definitions are stored in WAnything collection "spellWords" (worldId = region collection ID).
 * Player spell word XP is stored on {@link de.mhus.nimbus.world.shared.region.RCharacter#getSpellWords()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpellWordService {

    public static final String COLLECTION_SPELL_WORDS = "spellWords";

    public static final String CATEGORY_ELEMENT = "element";
    public static final String CATEGORY_FORM = "form";
    public static final String CATEGORY_MODIFIER = "modifier";

    private final WAnythingService anythingService;
    private final RCharacterService characterService;

    // ── Spell Word Definitions (WAnything) ──────────────────────────────

    /**
     * Get all spell word definitions for a region.
     *
     * @param regionWorldId region-scoped worldId (e.g. "@region:regionId")
     * @return list of WAnything entries from the spellWords collection
     */
    @Transactional(readOnly = true)
    public List<WAnything> findAllDefinitions(String regionWorldId) {
        return anythingService.findByWorldIdAndCollectionAndEnabled(regionWorldId, COLLECTION_SPELL_WORDS, true);
    }

    /**
     * Get all spell word definitions for a region, filtered by category.
     *
     * @param regionWorldId region-scoped worldId
     * @param category      "element", "form", or "modifier"
     * @return filtered list
     */
    @Transactional(readOnly = true)
    public List<WAnything> findDefinitionsByCategory(String regionWorldId, String category) {
        return anythingService.findByWorldIdAndCollectionAndType(regionWorldId, COLLECTION_SPELL_WORDS, category);
    }

    /**
     * Get a single spell word definition by name.
     *
     * @param regionWorldId region-scoped worldId
     * @param wordName      technical name of the word (e.g. "fire")
     * @return the definition or empty
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findDefinition(String regionWorldId, String wordName) {
        return anythingService.findByWorldIdAndCollectionAndName(regionWorldId, COLLECTION_SPELL_WORDS, wordName);
    }

    /**
     * Get the typed definition data for a spell word.
     *
     * @param regionWorldId region-scoped worldId
     * @param wordName      technical name of the word
     * @return the SpellWordDefinition or empty
     */
    @Transactional(readOnly = true)
    public Optional<SpellWordDefinition> findDefinitionData(String regionWorldId, String wordName) {
        return findDefinition(regionWorldId, wordName)
                .flatMap(a -> a.getDataAs(SpellWordDefinition.class));
    }

    // ── Player Spell Words ──────────────────────────────────────────────

    /**
     * Learn a new spell word for a character. Sets XP to 0.
     *
     * @param characterId MongoDB character document id
     * @param wordName    technical name of the word
     * @return true if the word was learned (false if already known)
     */
    public boolean learnWord(String characterId, String wordName) {
        return characterService.learnSpellWord(characterId, wordName);
    }

    /**
     * Add XP to a spell word after successful crafting usage.
     *
     * @param characterId MongoDB character document id
     * @param wordName    technical name of the word
     * @param xp          XP to add
     * @return true if XP was added
     */
    public boolean addWordXp(String characterId, String wordName, int xp) {
        return characterService.addSpellWordXp(characterId, wordName, xp);
    }

    /**
     * Get the level of a spell word for a character.
     *
     * @param spellWords the character's spellWords map
     * @param wordName   technical name of the word
     * @return the level (0-5), or -1 if the word is not learned
     */
    public int getWordLevel(Map<String, Integer> spellWords, String wordName) {
        if (spellWords == null) return -1;
        Integer xp = spellWords.get(wordName);
        if (xp == null) return -1;
        return RCharacterService.calculateSpellWordLevel(xp);
    }

    /**
     * Get all learned spell words with their levels for a character.
     *
     * @param spellWords the character's spellWords map
     * @return map of word name to level
     */
    public Map<String, Integer> getWordLevels(Map<String, Integer> spellWords) {
        if (spellWords == null) return Collections.emptyMap();
        return spellWords.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> RCharacterService.calculateSpellWordLevel(e.getValue())
                ));
    }

}
