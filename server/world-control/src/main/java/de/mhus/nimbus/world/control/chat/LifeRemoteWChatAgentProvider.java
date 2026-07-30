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
 * Remote chat agent provider for world-life server.
 * Discovers and communicates with chat agents on the world-life server.
 * Only active when NOT running on world-life itself.
 */
@Component
@Slf4j
public class LifeRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    private final LocationService locationService;

    public LifeRemoteWChatAgentProvider(WorldClientService worldClientService,
                                       ObjectMapper objectMapper,
                                       LocationService locationService) {
        super(worldClientService, objectMapper);
        this.locationService = locationService;
        log.info("LifeRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        return "via-worldclient";
    }

    @Override
    public boolean isAvailable() {
        return locationService.getMeServer() != LocationService.SERVER.LIFE;
    }

    @Override
    public String getProviderName() {
        return "life";
    }

    @Override
    protected CompletableFuture<CommandResponse> sendCommand(String worldId, String commandName,
                                                            List<String> args, CommandContext context) {
        log.debug("Sending command to life: command={}, args={}", commandName, args);
        return worldClientService.sendLifeCommand(worldId, commandName, args, context);
    }
}
