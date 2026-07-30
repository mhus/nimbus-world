package de.mhus.nimbus.world.control.chat;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.shared.chat.RemoteWChatAgentProvider;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.client.WorldClientService.CommandResponse;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Remote chat agent provider for world-generator server.
 * Discovers and communicates with chat agents on the world-generator server.
 * Only active when NOT running on world-generator itself.
 */
@Component
@Slf4j
public class GeneratorRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    private final LocationService locationService;

    public GeneratorRemoteWChatAgentProvider(WorldClientService worldClientService,
                                            ObjectMapper objectMapper,
                                            LocationService locationService) {
        super(worldClientService, objectMapper);
        this.locationService = locationService;
        log.info("GeneratorRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        return "via-worldclient";
    }

    @Override
    public boolean isAvailable() {
        return locationService.getMeServer() != LocationService.SERVER.GENERATOR;
    }

    @Override
    public String getProviderName() {
        return "generator";
    }

    @Override
    protected CompletableFuture<CommandResponse> sendCommand(String worldId, String commandName,
                                                            List<String> args, CommandContext context) {
        log.debug("Sending command to generator: command={}, args={}", commandName, args);
        return worldClientService.sendGeneratorCommand(worldId, commandName, args, context);
    }
}
