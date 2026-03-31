package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes dialog effects (give items, set logic flags, update NPC state, etc.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogEffectExecutor {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final String LOGIC_PACKAGE = "dialog";

    private final WProgressService progressService;
    private final RCharacterService characterService;

    /**
     * Execute all effects in order.
     */
    public void executeAll(List<Effect> effects, DialogContext ctx) {
        if (effects == null || effects.isEmpty()) return;
        for (Effect effect : effects) {
            try {
                execute(effect, ctx);
            } catch (Exception e) {
                log.error("Failed to execute effect type={} in dialog playbook={}: {}",
                        effect.type(), ctx.getPlaybookName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Execute a single effect.
     */
    public void execute(Effect effect, DialogContext ctx) {
        switch (effect.type()) {
            case "setLogic" -> executeSetLogic(effect, ctx);
            case "giveItem" -> executeGiveItem(effect, ctx);
            case "takeItem" -> executeTakeItem(effect, ctx);
            case "addReputation" -> executeAddReputation(effect, ctx);
            case "addSkillXP" -> executeAddSkillXP(effect, ctx);
            case "setNpcState" -> executeSetNpcState(effect, ctx);
            case "addNpcFact" -> executeAddNpcFact(effect, ctx);
            case "setMemory" -> executeSetMemory(effect, ctx);
            case "addMemory" -> executeAddMemory(effect, ctx);
            case "setProgress" -> executeSetProgress(effect, ctx);
            case "triggerScrawl" -> log.warn("triggerScrawl effect not yet implemented (script={}, sequence={})",
                    effect.script(), effect.sequence());
            default -> log.warn("Unknown effect type: {}", effect.type());
        }
    }

    private void executeSetLogic(Effect effect, DialogContext ctx) {
        String flag = effect.flag();
        Object value = effect.value() != null ? effect.value() : true;

        // Logic flags are stored in WProgress with playerId="logic", type="logic-flag"
        // State structure: { "dialog": { "flag_name": value } }
        var logicProgress = progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(ctx.getWorldId(), LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .orElse(null);

        if (logicProgress == null) {
            // Create new logic state
            Map<String, Object> state = new HashMap<>();
            Map<String, Object> pkg = new HashMap<>();
            pkg.put(flag, value);
            state.put(LOGIC_PACKAGE, pkg);
            progressService.save(ctx.getWorldId(), LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null, state);
        } else {
            progressService.setProgressDataValue(logicProgress.getProgressId(),
                    LOGIC_PACKAGE + "." + flag, value);
        }

        log.debug("Set logic flag dialog.{} = {} in world {}", flag, value, ctx.getWorldId());
    }

    private void executeGiveItem(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) {
            log.warn("Cannot give item: no character in context");
            return;
        }
        int count = effect.count() != null ? effect.count() : 1;
        boolean success = characterService.addBackpackItem(ctx.getCharacter().getId(), effect.item(), count);
        if (success) {
            log.debug("Gave {} x{} to character {}", effect.item(), count, ctx.getCharacter().getId());
        } else {
            log.warn("Failed to give item {} to character {}", effect.item(), ctx.getCharacter().getId());
        }
    }

    private void executeTakeItem(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) {
            log.warn("Cannot take item: no character in context");
            return;
        }
        int count = effect.count() != null ? effect.count() : 1;
        boolean success = characterService.removeBackpackItem(ctx.getCharacter().getId(), effect.item(), count);
        if (success) {
            log.debug("Took {} x{} from character {}", effect.item(), count, ctx.getCharacter().getId());
        } else {
            log.warn("Failed to take item {} from character {}", effect.item(), ctx.getCharacter().getId());
        }
    }

    private void executeAddReputation(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) return;
        int delta = effect.delta() != null ? effect.delta() : 0;
        characterService.changeReputation(ctx.getCharacter().getId(), effect.faction(), delta);
        log.debug("Changed reputation {} by {} for character {}", effect.faction(), delta, ctx.getCharacter().getId());
    }

    private void executeAddSkillXP(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) return;
        int amount = effect.amount() != null ? effect.amount() : 0;
        characterService.incrementSkillAtomic(ctx.getCharacter().getId(), effect.skill(), amount);
        log.debug("Added {} XP to skill {} for character {}", amount, effect.skill(), ctx.getCharacter().getId());
    }

    private void executeSetNpcState(Effect effect, DialogContext ctx) {
        WProgress npcState = ctx.getNpcStateProgress();
        if (npcState == null) {
            // Create NPC state progress
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : "unknown";
            Map<String, Object> data = new HashMap<>();
            data.put(effect.key(), effect.value());
            var saved = progressService.save(ctx.getWorldId(), "npc:" + entityId, "npc-state", null, data);
            ctx.setNpcStateProgress(saved);
            ctx.setNpcState(new HashMap<>(data));
        } else {
            progressService.setProgressDataValue(npcState.getProgressId(), effect.key(), effect.value());
            ctx.getNpcState().put(effect.key(), effect.value());
        }
        log.debug("Set NPC state {} = {}", effect.key(), effect.value());
    }

    @SuppressWarnings("unchecked")
    private void executeAddNpcFact(Effect effect, DialogContext ctx) {
        List<String> facts = new ArrayList<>(ctx.getNpcKnownFacts());
        if (!facts.contains(effect.fact())) {
            facts.add(effect.fact());
        }
        WProgress npcState = ctx.getNpcStateProgress();
        if (npcState == null) {
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : "unknown";
            Map<String, Object> data = new HashMap<>();
            data.put("knownFacts", facts);
            var saved = progressService.save(ctx.getWorldId(), "npc:" + entityId, "npc-state", null, data);
            ctx.setNpcStateProgress(saved);
            ctx.setNpcState(new HashMap<>(data));
        } else {
            progressService.setProgressDataValue(npcState.getProgressId(), "knownFacts", facts);
            ctx.getNpcState().put("knownFacts", facts);
        }
        log.debug("Added NPC fact: {}", effect.fact());
    }

    private void executeSetMemory(Effect effect, DialogContext ctx) {
        WProgress memory = ctx.getPlayerMemoryProgress();
        if (memory == null) {
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : "unknown";
            Map<String, Object> data = new HashMap<>();
            data.put(effect.key(), effect.value());
            var saved = progressService.save(ctx.getWorldId(), ctx.getPlayerId(), "npc-memory", entityId, data);
            ctx.setPlayerMemoryProgress(saved);
            ctx.setPlayerMemory(new HashMap<>(data));
        } else {
            progressService.setProgressDataValue(memory.getProgressId(), effect.key(), effect.value());
            ctx.getPlayerMemory().put(effect.key(), effect.value());
        }
        log.debug("Set player memory {} = {}", effect.key(), effect.value());
    }

    @SuppressWarnings("unchecked")
    private void executeAddMemory(Effect effect, DialogContext ctx) {
        List<String> remembers = new ArrayList<>(ctx.getPlayerRemembers());
        remembers.add(effect.text());

        WProgress memory = ctx.getPlayerMemoryProgress();
        if (memory == null) {
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : "unknown";
            Map<String, Object> data = new HashMap<>();
            data.put("remembers", remembers);
            var saved = progressService.save(ctx.getWorldId(), ctx.getPlayerId(), "npc-memory", entityId, data);
            ctx.setPlayerMemoryProgress(saved);
            ctx.setPlayerMemory(new HashMap<>(data));
        } else {
            progressService.setProgressDataValue(memory.getProgressId(), "remembers", remembers);
            ctx.getPlayerMemory().put("remembers", remembers);
        }
        log.debug("Added player memory: {}", effect.text());
    }

    private void executeSetProgress(Effect effect, DialogContext ctx) {
        if (ctx.getDialogProgress() == null) return;
        progressService.setProgressDataValue(
                ctx.getDialogProgress().getProgressId(), effect.key(), effect.value());
        log.debug("Set dialog progress {} = {}", effect.key(), effect.value());
    }
}
