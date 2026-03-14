package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.Builder;
import lombok.Getter;

/**
 * Context object passed to chat agents for each request.
 * Provides access to request-scoped data like the full worldId (with instance suffix).
 */
@Getter
@Builder
public class WChatContext {

    /**
     * Full world identifier including instance suffix (e.g. "ymir:Mist::x0").
     * Use this for operations that need the instance context (e.g. WEditCache writes).
     * Falls back to the base worldId if no instance is active.
     */
    private final WorldId fullWorldId;

    /**
     * Session identifier for accessing session-specific context.
     */
    private final String sessionId;
}
