package de.mhus.nimbus.world.shared.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.shared.utils.LocationService.SERVER;
import de.mhus.nimbus.world.shared.access.AccessService;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.commands.WorldCommandController;
import de.mhus.nimbus.world.shared.commands.WorldCommandController.CommandRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbound REST client for inter-server command communication.
 * Provides async command execution with CompletableFuture and timeout support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldClientService {

    @Qualifier("worldRestTemplate")
    private final RestTemplate restTemplate;
    private final WorldClientSettings properties;
    private final ObjectMapper objectMapper;
    private final LocationService locationService;
    private final AccessService accessService;

    /**
     * Command response DTO.
     */
    public record CommandResponse(
            int rc,
            String message,
            List<String> streamMessages
    ) {}

    /**
     * Send command to world-life server.
     *
     * @param worldId World identifier
     * @param commandName Command name
     * @param args Command arguments
     * @param context Optional context (for session data)
     * @return CompletableFuture with CommandResponse
     */
    public CompletableFuture<CommandResponse> sendLifeCommand(
            String worldId,
            String commandName,
            List<String> args,
            CommandContext context) {

        String baseUrl = properties.getLifeBaseUrl();
        prepareContext(context, worldId);
        return sendCommand(baseUrl, commandName, args, context, SERVER.LIFE);
    }

    private void prepareContext(CommandContext context, String worldId) {
        context.setOriginInternal(locationService.getInternalServerUrl());
        context.setOriginExternal(locationService.getExternalServerUrl());
        context.setRequestTime(Instant.now());
        context.setOriginServer(locationService.getMeServer().name());
        context.setWorldId(worldId);
    }

    /**
     * Send command to world-player server.
     *
     * @param worldId World identifier
     * @param sessionId Session identifier (optional)
     * @param playerUrl Player Base URL http://serverIP:port
     * @param commandName Command name
     * @param args Command arguments
     * @param context Optional context
     * @return CompletableFuture with CommandResponse
     */
    public CompletableFuture<CommandResponse> sendPlayerCommand(
            String worldId,
            String sessionId,
            String playerUrl,
            String commandName,
            List<String> args,
            CommandContext context) {

        String baseUrl = Strings.isEmpty(playerUrl) ? properties.getPlayerBaseUrl() : playerUrl;

        // Update context with session if provided
        if (context == null) {
            context = CommandContext.builder()
                    .worldId(worldId)
                    .sessionId(sessionId)
                    .build();
        } else if (sessionId != null) {
            context.setSessionId(sessionId);
        }
        prepareContext(context, worldId);

        return sendCommand(baseUrl, commandName, args, context, SERVER.PLAYER);
    }

    /**
     * Send command to world-control server.
     *
     * @param worldId World identifier
     * @param commandName Command name
     * @param args Command arguments
     * @param context Optional context
     * @return CompletableFuture with CommandResponse
     */
    public CompletableFuture<CommandResponse> sendControlCommand(
            String worldId,
            String commandName,
            List<String> args,
            CommandContext context) {

        prepareContext(context, worldId);
        String baseUrl = properties.getControlBaseUrl();
        return sendCommand(baseUrl, commandName, args, context, SERVER.CONTROL);
    }

    /**
     * Send command to world-control server.
     *
     * @param worldId World identifier
     * @param commandName Command name
     * @param args Command arguments
     * @param context Optional context
     * @return CompletableFuture with CommandResponse
     */
    public CompletableFuture<CommandResponse> sendGeneratorCommand(
            String worldId,
            String commandName,
            List<String> args,
            CommandContext context) {

        prepareContext(context, worldId);
        String baseUrl = properties.getGeneratorBaseUrl();
        return sendCommand(baseUrl, commandName, args, context, SERVER.GENERATOR);
    }

    /**
     * Generic command sender.
     */
    private CompletableFuture<CommandResponse> sendCommand(
            @NotNull String baseUrl,
            @NotNull String commandName,
            @NotNull List<String> args,
            @NotNull CommandContext context,
            @NotNull SERVER targetServer) {

        var worldId = context.getWorldId();
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build request
                CommandRequest request = new CommandRequest(
                        commandName,
                        args != null ? args : List.of(),
                        worldId,
                        context != null ? context.getSessionId() : null,
                        context != null ? context.getUserId() : null,
                        context != null ? context.getTitle() : null,
                        context != null ? context.getOriginServer() : "unknown",
                        context != null ? context.getMetadata() : null
                );

                // Build URL
                String url = baseUrl + "/world/world/command/" + encode(commandName);

                log.debug("Sending command to {}: url={}, cmd={}, worldId={}",
                        targetServer, url, commandName, worldId);

                // Get world token from AccessService
                String bearerToken = accessService.getWorldToken();

                // Add Authorization header with Bearer token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + bearerToken);
                HttpEntity<CommandRequest> httpEntity = new HttpEntity<>(request, headers);

                // Execute REST call with timeout
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        URI.create(url),
                        httpEntity,
                        Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();

                    int rc = body.get("rc") instanceof Number n ? n.intValue() : -4;
                    String message = (String) body.get("message");

                    @SuppressWarnings("unchecked")
                    List<String> streamMessages = body.get("streamMessages") instanceof List list
                            ? (List<String>) list
                            : null;

                    log.debug("Command completed: cmd={}, rc={}, target={}",
                            commandName, rc, targetServer);

                    return new CommandResponse(rc, message, streamMessages);
                }

                log.error("Unexpected response status: {}", response.getStatusCode());
                return new CommandResponse(-4, "Server error: " + response.getStatusCode(), null);

            } catch (RestClientException e) {
                log.error("Command failed: cmd={}, target={}, error={}",
                        commandName, targetServer, e.getMessage(), e);
                return new CommandResponse(-4, "Communication error: " + e.getMessage(), null);

            } catch (Exception e) {
                log.error("Unexpected error sending command", e);
                return new CommandResponse(-4, "Internal error: " + e.getMessage(), null);
            }
        })
        .orTimeout(properties.getCommandTimeoutMs(), TimeUnit.MILLISECONDS)
        .exceptionally(throwable -> {
            if (throwable instanceof TimeoutException) {
                log.error("Command timeout: cmd={}, target={}", commandName, targetServer);
                return new CommandResponse(-5, "Command timeout", null);
            }
            log.error("Command execution failed", throwable);
            return new CommandResponse(-4, "Execution error: " + throwable.getMessage(), null);
        });
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Fire-and-forget: Notify world-control that a session has closed.
     * Used when a session transitions to CLOSED state to cleanup world instances.
     * Does not wait for response (async, no retry on failure).
     *
     * @param worldId The worldId (or instanceId) of the closed session
     * @param playerId The playerId of the closed session
     */
    public void notifySessionClosed(String worldId, String playerId) {
        // Only send if we're in world-player
        if (!locationService.isWorldPlayer()) {
            log.debug("Not sending session-closed notification - not running in world-player");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String controlBaseUrl = properties.getControlBaseUrl();
                String url = controlBaseUrl + "/control/session-lifecycle/session-closed";

                // Create request body
                Map<String, String> requestBody = Map.of(
                        "worldId", worldId,
                        "playerId", playerId
                );

                // Set headers (no auth token needed for internal server-to-server)
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");

                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(requestBody, headers);

                log.debug("Sending session-closed notification to world-control: worldId={}, playerId={}, url={}",
                        worldId, playerId, url);

                // Fire-and-forget POST (don't care about response)
                restTemplate.postForEntity(
                        URI.create(url),
                        httpEntity,
                        Void.class
                );

                log.info("Session-closed notification sent successfully: worldId={}, playerId={}",
                        worldId, playerId);

            } catch (Exception e) {
                // Log error but don't propagate (fire-and-forget)
                log.warn("Failed to send session-closed notification to world-control: worldId={}, playerId={}, error={}",
                        worldId, playerId, e.getMessage());
            }
        });
    }
}
