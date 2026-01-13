package de.mhus.nimbus.world.control.commands;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.world.control.service.EditService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InsertBlockCommand implements Command {

    private final EditService editService;

    @Override
    public String getName() {
        return "InsertBlock";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String worldId = context.getWorldId();
        String sessionId = context.getSessionId();

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("InsertBlock called without sessionId");
            return CommandResult.error(-3, "sessionId required");
        }

        try {
            int x = Integer.parseInt(args.get(0));
            int y = Integer.parseInt(args.get(1));
            int z = Integer.parseInt(args.get(2));
            String blockTypeId = args.get(3);

            Block block = Block.builder()
                    .blockTypeId(blockTypeId)
                    .build();

            editService.setBlock(
                    worldId,
                    sessionId,
                    block,
                    x, y, z
            );

        } catch (Exception e) {
            log.error("InsertBlock failed", e);
            return CommandResult.error(-4, "InsertBlock failed: " + e.getMessage());
        }

        return CommandResult.success("ok");
    }

    @Override
    public String getHelp() {
        return "InsertBlock x,y,z,blockTypeId";
    }
}
