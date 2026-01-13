package de.mhus.nimbus.world.control.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.client.WorldClientService.CommandResponse;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Remote chat agent provider for world-life server.
 * Discovers and communicates with chat agents on the world-life server.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "nimbus.wchat.remote.life.enabled", havingValue = "true")
public class LifeRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    @Value("${nimbus.wchat.remote.life.server-url}")
    private String serverUrl;

    @Autowired
    public LifeRemoteWChatAgentProvider(WorldClientService worldClientService,
                                       ObjectMapper objectMapper) {
        super(worldClientService, objectMapper);
        log.info("LifeRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        return serverUrl;
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
