package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddNpcFactEffectHandler implements DialogEffectHandler {

    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "addNpcFact"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        List<String> facts = new ArrayList<>(ctx.getNpcKnownFacts());
        if (!facts.contains(effect.fact())) {
            facts.add(effect.fact());
        }
        var npcState = ctx.getNpcStateProgress();
        if (npcState == null) {
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getName() : "unknown";
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
}
