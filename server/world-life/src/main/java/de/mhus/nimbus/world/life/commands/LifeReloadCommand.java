package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reload all entities for the current world from DB.
 * Clears all simulation states and re-loads from active chunks.
 * Usage: /life-reload
 */
@Component
@RequiredArgsConstructor
public class LifeReloadCommand implements Command {

    private final SimulatorService simulatorService;

    @Override
    public String getName() {
        return "life-reload";
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

        int result = simulatorService.reloadEntities(worldId);
        return CommandResult.success("Reloaded " + result + " entities for world " + worldIdStr);
    }

    @Override
    public String getHelp() {
        return "Reload all entities for the current world from DB\n" +
                "Clears simulation states and re-loads from active chunks.\n" +
                "Usage: /life-reload";
    }
}
