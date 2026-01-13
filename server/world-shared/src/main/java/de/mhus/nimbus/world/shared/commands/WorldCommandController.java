package de.mhus.nimbus.world.shared.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for inter-server command execution.
 */
@RestController
@RequestMapping("/world/world")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Commands", description = "Inter-server command execution")
public class WorldCommandController {

    private final CommandService commandService;
    private final ObjectMapper objectMapper;

    /**
     * Command request DTO.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandRequest(
            String cmd,
            List<String> args,
            String worldId,
            String sessionId,
            String userId,
            String title,
            String originServer,
            Map<String, Object> metadata
    ) {}

    /**
     * Execute command via REST.
     * POST /world/world/command/{commandName}
     *
     * Request body: CommandRequest
     * Response: {rc: int, message: string, streamMessages: [string]}
     */
    @PostMapping("/command/{commandName}")
    @Operation(summary = "Execute command", description = "Execute a command on this world server")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Command executed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Execution error")
    })
    public ResponseEntity<Map<String, Object>> executeCommand(
            @PathVariable String commandName,
            @RequestBody CommandRequest request) {

        log.debug("REST command received: cmd={}, worldId={}, sessionId={}",
                commandName, request.worldId(), request.sessionId());

        // Validate request
        if (commandName == null || commandName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("rc", -3, "message", "Command name required"));
        }

        if (request.worldId() == null || request.worldId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("rc", -3, "message", "World ID required"));
        }

        // Build context
        CommandContext context = CommandContext.builder()
                .worldId(request.worldId())
                .sessionId(request.sessionId())
                .userId(request.userId())
                .title(request.title())
                .originServer(request.originServer())
                .requestTime(Instant.now())
                .metadata(request.metadata() != null ? request.metadata() : new HashMap<>())
                .build();

        // Execute command
        List<String> args = request.args() != null ? request.args() : List.of();
        Command.CommandResult result = commandService.execute(context, commandName, args);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("rc", result.getReturnCode());

        if (result.getMessage() != null) {
            response.put("message", result.getMessage());
        }

        if (result.getStreamMessages() != null && !result.getStreamMessages().isEmpty()) {
            response.put("streamMessages", result.getStreamMessages());
        }

        log.debug("REST command completed: cmd={}, rc={}", commandName, result.getReturnCode());

        return ResponseEntity.ok(response);
    }
}
