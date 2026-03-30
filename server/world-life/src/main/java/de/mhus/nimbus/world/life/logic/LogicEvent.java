package de.mhus.nimbus.world.life.logic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for Logic Machine event processing.
 * Contains SpEL assignment expressions to evaluate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicEvent {

    private String worldId;

    /**
     * Ordered SpEL assignment expressions to evaluate.
     * Example: ["flags.flag1 = !flags.flag1", "flags.counter = flags.counter + 1"]
     */
    private List<String> eval;

    /**
     * Meta/debug info about the event source (e.g. playerId, entityId).
     */
    private String source;

    /**
     * Optional additional metadata for debugging.
     */
    private Map<String, String> meta;
}
