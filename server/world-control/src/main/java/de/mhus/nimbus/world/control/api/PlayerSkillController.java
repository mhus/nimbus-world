package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.Skill;
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
@RequestMapping("/control/player/skills")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Skills", description = "Skill management for players")
public class PlayerSkillController extends BaseEditorController {

    private final RCharacterService characterService;

    @GetMapping
    @Operation(summary = "Get skill overview with all skill definitions and character state")
    public ResponseEntity<?> getSkills(HttpServletRequest request) {

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

        Map<String, Integer> skills = character.getSkills();

        // Build skill definitions list
        List<Map<String, Object>> skillDefs = new ArrayList<>();
        for (Skill skill : AdventureSkills.ALL) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", skill.getName());
            def.put("title", skill.getTitle());
            def.put("description", skill.getDescription());
            def.put("group", skill.getGroup());
            def.put("free", skill.isFree());
            def.put("start", skill.getStart());
            def.put("min", skill.getMin());
            def.put("max", skill.getMax());
            def.put("current", skill.getValue(skills));
            skillDefs.add(def);
        }

        // Calculate total invested skill points for experience-to-next calculation
        int totalSkillPoints = characterService.calculateTotalSkillPoints(character, name -> {
            Skill s = AdventureSkills.byName(name);
            if (s == null) return null;
            return new int[]{s.getStart(), s.getMin(), s.getMax()};
        });

        long experienceToNext = characterService.calculateSkillExperienceToNext(totalSkillPoints);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillPoints", character.getSkillPoints());
        result.put("skillExperience", character.getSkillExperience());
        result.put("experienceToNext", experienceToNext);
        result.put("totalSkillPoints", totalSkillPoints);
        result.put("skills", skillDefs);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/convert-experience")
    @Operation(summary = "Convert experience into a skill point if enough experience is available")
    public ResponseEntity<?> convertExperience(HttpServletRequest request) {

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

        int totalSkillPoints = characterService.calculateTotalSkillPoints(character, name -> {
            Skill s = AdventureSkills.byName(name);
            if (s == null) return null;
            return new int[]{s.getStart(), s.getMin(), s.getMax()};
        });

        long experienceToNext = characterService.calculateSkillExperienceToNext(totalSkillPoints);
        boolean converted = characterService.convertExperienceToSkillPoint(character.getId(), experienceToNext);

        if (!converted) {
            return ResponseEntity.ok(Map.of(
                    "converted", false,
                    "skillPoints", character.getSkillPoints(),
                    "skillExperience", character.getSkillExperience(),
                    "experienceToNext", experienceToNext
            ));
        }

        // Re-read character to get updated values
        var updated = findCharacter(worldId, userId, characterId);
        int newTotal = characterService.calculateTotalSkillPoints(updated, name -> {
            Skill s = AdventureSkills.byName(name);
            if (s == null) return null;
            return new int[]{s.getStart(), s.getMin(), s.getMax()};
        });
        long newExperienceToNext = characterService.calculateSkillExperienceToNext(newTotal);

        return ResponseEntity.ok(Map.of(
                "converted", true,
                "skillPoints", updated.getSkillPoints(),
                "skillExperience", updated.getSkillExperience(),
                "experienceToNext", newExperienceToNext
        ));
    }

    @PostMapping("/spend")
    @Operation(summary = "Spend skill points on skills")
    public ResponseEntity<?> spendSkillPoints(
            @RequestBody SpendRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.allocations() == null || body.allocations().isEmpty()) {
            return bad("allocations required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Validate all allocations before applying
        for (var entry : body.allocations().entrySet()) {
            String skillName = entry.getKey();
            int points = entry.getValue();
            if (points <= 0) continue;

            Skill skill = AdventureSkills.byName(skillName);
            if (skill == null) {
                return bad("Unknown skill: " + skillName);
            }
            if (!skill.isFree()) {
                return bad("Skill is not freely distributable: " + skillName);
            }
            // Check max level
            int currentLevel = skill.getValue(character.getSkills());
            if (currentLevel + points > skill.getMax()) {
                return bad("Skill " + skillName + " would exceed maximum level");
            }
        }

        // Apply one point at a time atomically
        int totalSpent = 0;
        for (var entry : body.allocations().entrySet()) {
            String skillName = entry.getKey();
            int points = entry.getValue();
            for (int i = 0; i < points; i++) {
                boolean spent = characterService.spendSkillPoint(character.getId(), skillName);
                if (!spent) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "spent", totalSpent,
                            "message", "Not enough skill points"
                    ));
                }
                totalSpent++;
            }
        }

        log.info("Spent {} skill points: userId={}, characterId={}, allocations={}",
                totalSpent, userId, characterId, body.allocations());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "spent", totalSpent
        ));
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        return characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
    }

    record SpendRequest(Map<String, Integer> allocations) {}
}
