package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NpcStateEffectHandler implements DialogEffectHandler {

    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "setNpcState"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        var npcState = ctx.getNpcStateProgress();
        if (npcState == null) {
            String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getName() : "unknown";
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
}
