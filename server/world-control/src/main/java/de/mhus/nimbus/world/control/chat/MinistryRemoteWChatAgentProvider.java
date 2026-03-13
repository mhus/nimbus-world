package de.mhus.nimbus.world.control.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Remote chat agent provider for ministry server.
 * Discovers and communicates with chat agents on the ministry server.
 * Only active when NOT running on ministry itself.
 */
@Component
@Slf4j
public class MinistryRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    private final LocationService locationService;

    public MinistryRemoteWChatAgentProvider(WorldClientService worldClientService,
                                            ObjectMapper objectMapper,
                                            LocationService locationService) {
        super(worldClientService, objectMapper);
        this.locationService = locationService;
        log.info("MinistryRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        return "via-worldclient";
    }

    @Override
    public boolean isAvailable() {
        return !locationService.isMinistry();
    }

    @Override
    public String getProviderName() {
        return "ministry";
    }

    @Override
    protected CompletableFuture<CommandResponse> sendCommand(String worldId, String commandName,
                                                            List<String> args, CommandContext context) {
        log.debug("Sending command to ministry: command={}, args={}", commandName, args);
        return worldClientService.sendMinistryCommand(worldId, commandName, args, context);
    }
}
