package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * List all loaded entities for the current world with their status.
 * Usage: /life-list
 */
@Component
@RequiredArgsConstructor
public class LifeListCommand implements Command {

    private final SimulatorService simulatorService;

    @Override
    public String getName() {
        return "life-list";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String worldIdStr = context.getWorldId();
        if (worldIdStr == null) {
            return CommandResult.error("No worldId in context");
        }
        WorldId worldId = WorldId.of(worldIdStr).orElse(null);
        if (worldId == null) {
            return CommandResult.error("Invalid worldId: " + worldIdStr);
        }

        var states = simulatorService.getSimulationStates(worldId);
        if (states == null || states.isEmpty()) {
            return CommandResult.success("No entities loaded for world " + worldIdStr);
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-20s %-12s %-8s %-10s %-8s", "EntityId", "Model", "State", "Combat", "Health"));
        lines.add("-".repeat(62));

        for (var entry : states.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            SimulationState state = entry.getValue();
            String entityId = entry.getKey();
            String modelId = state.getEntity().getModelId() != null ? state.getEntity().getModelId() : "-";
            String lifecycle = state.getLifecycleState().name();
            String combat = state.isInCombat() ? "COMBAT" : "-";

            String health = "-";
            EntityCombatData cd = state.getCombatData();
            if (cd != null) {
                VitalValue h = cd.getVital("health");
                if (h != null) {
                    health = String.format("%.0f/%.0f", h.getCurrent(), h.getEffectiveMax());
                }
            }

            lines.add(String.format("%-20s %-12s %-8s %-10s %-8s", entityId, modelId, lifecycle, combat, health));
        }

        lines.add("\nTotal: " + states.size() + " entities");
        return CommandResult.success(String.join("\n", lines));
    }

    @Override
    public String getHelp() {
        return "List all loaded entities in the current world\n" +
                "Shows: entityId, model, lifecycle state, combat status, health";
    }
}
