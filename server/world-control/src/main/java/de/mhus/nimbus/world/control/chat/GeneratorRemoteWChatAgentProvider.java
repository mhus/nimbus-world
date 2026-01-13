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
 * Remote chat agent provider for world-generator server.
 * Discovers and communicates with chat agents on the world-generator server.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "nimbus.wchat.remote.generator.enabled", havingValue = "true")
public class GeneratorRemoteWChatAgentProvider extends RemoteWChatAgentProvider {

    @Value("${nimbus.wchat.remote.generator.server-url}")
    private String serverUrl;

    @Autowired
    public GeneratorRemoteWChatAgentProvider(WorldClientService worldClientService,
                                            ObjectMapper objectMapper) {
        super(worldClientService, objectMapper);
        log.info("GeneratorRemoteWChatAgentProvider initialized");
    }

    @Override
    protected String getServerUrl() {
        return serverUrl;
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
