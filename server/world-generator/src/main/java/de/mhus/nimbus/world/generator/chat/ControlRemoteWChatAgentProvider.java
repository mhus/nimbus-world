package de.mhus.nimbus.world.generator.chat;

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
 * Remote chat agent provider for world-control server.
 * Discovers and communicates with chat agents on world-control (e.g., eliza).
 * Uses the load balancer URL from WorldClientService — routing to the correct
 * pod for active sessions is handled by WChatExecutorService via Redis.
 * Only active when NOT running on world-control itself.
 */
@Component
@Slf4j
public class ControlRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    private final LocationService locationService;

    public ControlRemoteWChatAgentProvider(WorldClientService worldClientService,
                                           ObjectMapper objectMapper,
                                           LocationService locationService) {
        super(worldClientService, objectMapper);
        this.locationService = locationService;
        log.info("ControlRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        // URL is managed by WorldClientService, just return non-null to indicate available
        return "via-worldclient";
    }

    @Override
    public boolean isAvailable() {
        // Don't discover remote control agents if we ARE world-control
        return !locationService.isWorldControl();
    }

    @Override
    public String getProviderName() {
        return "control";
    }

    @Override
    protected CompletableFuture<CommandResponse> sendCommand(String worldId, String commandName,
                                                            List<String> args, CommandContext context) {
        log.debug("Sending command to control: command={}, args={}", commandName, args);
        return worldClientService.sendControlCommand(worldId, commandName, args, context);
    }
}
