package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Show detailed info for a specific entity.
 * Usage: /life-detail <entityId>
 */
@Component
@RequiredArgsConstructor
public class LifeDetailCommand implements Command {

    private final SimulatorService simulatorService;

    @Override
    public String getName() {
        return "life-detail";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        if (args == null || args.isEmpty()) {
            return CommandResult.error("Usage: /life-detail <entityId>");
        }
        String entityId = args.get(0);

        String worldIdStr = context.getWorldId();
        if (worldIdStr == null) {
            return CommandResult.error("No worldId in context");
        }
        WorldId worldId = WorldId.of(worldIdStr).orElse(null);
        if (worldId == null) {
            return CommandResult.error("Invalid worldId: " + worldIdStr);
        }

        SimulationState state = simulatorService.findSimulationState(worldId, entityId);
        if (state == null) {
            return CommandResult.error("Entity '" + entityId + "' not loaded in world " + worldIdStr);
        }

        List<String> lines = new ArrayList<>();
        var entity = state.getEntity();

        lines.add("=== Entity: " + entityId + " ===");
        lines.add("Model: " + entity.getModelId());
        lines.add("Lifecycle: " + state.getLifecycleState());
        lines.add("Position: " + formatPos(entity.getPosition()));
        lines.add("Speed: " + entity.getSpeed());
        lines.add("Behavior: " + entity.getBehaviorModel());

        // Combat state
        lines.add("");
        lines.add("--- Combat ---");
        lines.add("InCombat: " + state.isInCombat());
        lines.add("Strategy: " + state.getCombatStrategy());
        lines.add("AttackCount: " + state.getCombatAttackCount());
        if (!state.getAttackerSessions().isEmpty()) {
            lines.add("Attackers: " + state.getAttackerSessions().keySet());
        }
        if (!state.getAttackers().isEmpty()) {
            lines.add("LootEligible: " + state.getAttackers());
        }

        // Combat data
        EntityCombatData cd = state.getCombatData();
        if (cd != null) {
            lines.add("WeaponItemId: " + cd.getWeaponItemId());

            lines.add("");
            lines.add("--- Vitals ---");
            for (var v : cd.getVitals().entrySet()) {
                VitalValue vv = v.getValue();
                lines.add(String.format("  %-12s %.1f / %.1f (base=%.1f, regen=%.2f/s)",
                        v.getKey(), vv.getCurrent(), vv.getEffectiveMax(), vv.getBase(), vv.getEffectiveRegenRate()));
            }

            lines.add("");
            lines.add("--- Combat Stats ---");
            for (var s : cd.getCombatStats().entrySet()) {
                CombatStat cs = s.getValue();
                lines.add(String.format("  %-22s effective=%.2f (base=%.2f, buff=%.2f)",
                        s.getKey(), cs.getEffective(), cs.getBase(), cs.getBuffFlat()));
            }

            if (!cd.getActiveEffects().isEmpty()) {
                lines.add("");
                lines.add("--- Active Effects ---");
                for (var e : cd.getActiveEffects()) {
                    lines.add(String.format("  %s: %s %.2f (%.1f/%.1fs, src=%s)",
                            e.getId().substring(0, Math.min(8, e.getId().length())),
                            e.getStat(), e.getValue(),
                            e.getDuration(), e.getMaxDuration(), e.getSource()));
                }
            }
        } else {
            lines.add("\nNo combat data.");
        }

        // Server properties
        if (entity.getServer() != null && !entity.getServer().isEmpty()) {
            lines.add("");
            lines.add("--- Server Properties ---");
            entity.getServer().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> lines.add("  " + e.getKey() + " = " + e.getValue()));
        }

        return CommandResult.success(String.join("\n", lines));
    }

    private String formatPos(de.mhus.nimbus.generated.types.Vector3 pos) {
        if (pos == null) return "null";
        return String.format("(%.1f, %.1f, %.1f)", pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public String getHelp() {
        return "Show detailed info for a specific entity\n" +
                "Usage: /life-detail <entityId>\n" +
                "Shows: position, combat state, vitals, stats, effects, server properties";
    }
}
