package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WProgress;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated context for a dialog interaction.
 * Holds all loaded data needed for condition evaluation, effect execution, and text generation.
 */
@Data
@Builder
public class DialogContext {

    // Dialog session
    private WProgress dialogProgress;
    private DialogDtos.Playbook playbook;
    private String playbookName;

    // NPC identity
    private DialogDtos.NpcProfile npcProfile;
    private WEntity npcEntity;
    private String npcTitle;
    private String npcPortrait;

    // NPC world-instance state (WProgress playerId="npc:{entityId}", type="npc-state")
    private WProgress npcStateProgress;
    @Builder.Default
    private Map<String, Object> npcState = new HashMap<>();

    // NPC-player memory (WProgress type="npc-memory", quest=entityId)
    private WProgress playerMemoryProgress;
    @Builder.Default
    private Map<String, Object> playerMemory = new HashMap<>();

    // Player character
    private RCharacter character;

    // IDs
    private String worldId;
    private String playerId;
    private String characterId;

    // Active situation (set by selectSituation)
    private DialogDtos.Situation activeSituation;
    private String activeSituationName;
    @Builder.Default
    private List<DialogDtos.Situation> backgroundSituations = List.of();

    // Current node within dialog
    private String currentNodeId;

    // Helpers

    public Object getNpcStateValue(String key) {
        return npcState != null ? npcState.get(key) : null;
    }

    public Object getMemoryValue(String key) {
        return playerMemory != null ? playerMemory.get(key) : null;
    }

    public int getConversationCount() {
        Object val = getMemoryValue("conversationCount");
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    public List<String> getNpcKnownFacts() {
        Object facts = getNpcStateValue("knownFacts");
        if (facts instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPlayerRemembers() {
        Object remembers = getMemoryValue("remembers");
        if (remembers instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    public String getMainWorldId() {
        var parsed = de.mhus.nimbus.shared.types.WorldId.of(worldId).orElse(null);
        if (parsed != null && parsed.isInstance()) {
            return parsed.toMainWorld().getId();
        }
        return worldId;
    }
}
