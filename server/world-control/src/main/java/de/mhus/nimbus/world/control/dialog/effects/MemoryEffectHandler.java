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

public class MemoryEffectHandler {

    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class SetMemory implements DialogEffectHandler {

        private final WProgressService progressService;

        @Override
        public String getEffectType() { return "setMemory"; }

        @Override
        public void execute(Effect effect, DialogContext ctx) {
            var memory = ctx.getPlayerMemoryProgress();
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
    }

    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class AddMemory implements DialogEffectHandler {

        private final WProgressService progressService;

        @Override
        public String getEffectType() { return "addMemory"; }

        @Override
        public void execute(Effect effect, DialogContext ctx) {
            List<String> remembers = new ArrayList<>(ctx.getPlayerRemembers());
            remembers.add(effect.text());

            var memory = ctx.getPlayerMemoryProgress();
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
    }
}
