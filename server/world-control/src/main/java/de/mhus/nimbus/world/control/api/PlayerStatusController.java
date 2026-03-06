package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.Skill;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.session.WSessionService;
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
@RequestMapping("/control/player/status")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Status", description = "Player status overview (skills, constitution, vitals)")
public class PlayerStatusController extends BaseEditorController {

    private final RCharacterService characterService;
    private final RUserService userService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Get full player status: skills, constitution, and live vitals/combat stats")
    public ResponseEntity<?> getStatus(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // Currency & identity
        result.put("characterName", character.getName());
        result.put("username", userId);
        result.put("silver", character.getSilver());

        long gold = 0;
        var userOpt = userService.getByUsername(userId);
        if (userOpt.isPresent()) {
            gold = userOpt.get().getGold();
        }
        result.put("gold", gold);

        // Skills
        Map<String, Integer> skills = character.getSkills();
        List<Map<String, Object>> skillDefs = new ArrayList<>();
        for (Skill skill : AdventureSkills.ALL) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", skill.getName());
            def.put("title", skill.getTitle());
            def.put("description", skill.getDescription());
            def.put("group", skill.getGroup());
            def.put("free", skill.isFree());
            def.put("current", skill.getValue(skills));
            def.put("min", skill.getMin());
            def.put("max", skill.getMax());
            skillDefs.add(def);
        }
        result.put("skills", skillDefs);

        // Skill points & experience
        result.put("skillPoints", character.getSkillPoints());
        result.put("skillExperience", character.getSkillExperience());

        // Constitution
        Map<String, Double> constitution = character.getConstitution();
        List<Map<String, Object>> conList = new ArrayList<>();
        for (var entry : constitution.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("category", entry.getKey());
            c.put("value", Math.round(entry.getValue() * 1000.0) / 1000.0);
            c.put("percent", Math.round(entry.getValue() * 100.0));
            conList.add(c);
        }
        result.put("constitution", conList);

        // Live vitals & combat stats from session via command
        result.put("vitals", List.of());
        result.put("combatStats", List.of());

        if (!Strings.isBlank(sessionId)) {
            try {
                var wSession = wSessionService.getWithPlayerUrl(sessionId);
                if (wSession.isPresent() && !Strings.isBlank(wSession.get().getPlayerUrl())) {
                    var response = worldClientService.sendPlayerCommand(
                            worldId, sessionId, wSession.get().getPlayerUrl(),
                            "GetStatus", List.of(), null
                    ).get();

                    if (response.rc() == 0 && response.message() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> statusData = objectMapper.readValue(response.message(), Map.class);
                        if (statusData.containsKey("vitals")) {
                            result.put("vitals", statusData.get("vitals"));
                        }
                        if (statusData.containsKey("combatStats")) {
                            result.put("combatStats", statusData.get("combatStats"));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get live status from world-player for session {}: {}", sessionId, e.getMessage());
            }
        }

        return ResponseEntity.ok(result);
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        return characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
    }
}
