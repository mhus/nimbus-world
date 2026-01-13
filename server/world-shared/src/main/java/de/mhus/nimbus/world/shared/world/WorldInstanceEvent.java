package de.mhus.nimbus.world.shared.world;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event object for world instance lifecycle events.
 * Contains the instance and metadata about the event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldInstanceEvent {

    /**
     * The world instance that triggered the event.
     */
    private WWorldInstance instance;

    /**
     * Timestamp when the event occurred.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Type of event that occurred.
     */
    private EventType eventType;

    /**
     * Optional additional context or message.
     */
    private String context;

    /**
     * Event types for world instance lifecycle.
     */
    public enum EventType {
        CREATED,
        DELETED,
        UPDATED,
        PLAYER_ADDED,
        PLAYER_REMOVED
    }

    /**
     * Create a CREATED event.
     *
     * @param instance The instance that was created
     * @return WorldInstanceEvent
     */
    public static WorldInstanceEvent created(WWorldInstance instance) {
        return WorldInstanceEvent.builder()
                .instance(instance)
                .eventType(EventType.CREATED)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a DELETED event.
     *
     * @param instance The instance that was deleted
     * @return WorldInstanceEvent
     */
    public static WorldInstanceEvent deleted(WWorldInstance instance) {
        return WorldInstanceEvent.builder()
                .instance(instance)
                .eventType(EventType.DELETED)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an UPDATED event.
     *
     * @param instance The instance that was updated
     * @return WorldInstanceEvent
     */
    public static WorldInstanceEvent updated(WWorldInstance instance) {
        return WorldInstanceEvent.builder()
                .instance(instance)
                .eventType(EventType.UPDATED)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a PLAYER_ADDED event.
     *
     * @param instance The instance
     * @param playerId The player that was added
     * @return WorldInstanceEvent
     */
    public static WorldInstanceEvent playerAdded(WWorldInstance instance, String playerId) {
        return WorldInstanceEvent.builder()
                .instance(instance)
                .eventType(EventType.PLAYER_ADDED)
                .timestamp(Instant.now())
                .context("playerId: " + playerId)
                .build();
    }

    /**
     * Create a PLAYER_REMOVED event.
     *
     * @param instance The instance
     * @param playerId The player that was removed
     * @return WorldInstanceEvent
     */
    public static WorldInstanceEvent playerRemoved(WWorldInstance instance, String playerId) {
        return WorldInstanceEvent.builder()
                .instance(instance)
                .eventType(EventType.PLAYER_REMOVED)
                .timestamp(Instant.now())
                .context("playerId: " + playerId)
                .build();
    }
}
