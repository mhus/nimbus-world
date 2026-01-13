package de.mhus.nimbus.world.shared.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context for command execution.
 * Encapsulates metadata for both local (session-based) and remote (session-less) command execution.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommandContext {

    /**
     * World identifier (required).
     */
    private String worldId;

    /**
     * Session identifier (optional - present for player sessions).
     */
    private String sessionId;

    /**
     * User identifier (optional).
     */
    private String userId;

    /**
     * Display name (optional - for logging).
     */
    private String title;

    /**
     * Request ID for async tracking (optional).
     */
    private String requestId;

    /**
     * When the command was initiated.
     */
    private Instant requestTime;

    /**
     * Which server initiated the command (e.g., "world-player", "world-life").
     */
    private String originServer;

    /**
     * Origin intenal url address (optional - for server-initiated commands).
     */
    private String originInternal;

    /**
     * Origin external url address (optional - for server-initiated commands).
     */
    private String originExternal;

    /**
     * Extensible metadata map.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Check if this context has an active session.
     *
     * @return true if sessionId is present
     */
    public boolean hasSession() {
        return sessionId != null && !sessionId.isBlank();
    }
}
