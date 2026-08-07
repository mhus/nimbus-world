package de.mhus.nimbus.world.shared.world;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single phase in an entity's daily schedule (timetable).
 * Entities with a schedule switch location, behavior, and presence based on world hour.
 *
 * Example: A merchant who is at the market during the day and in the tavern at night.
 *
 * <pre>
 * { name: "market_duty", fromHour: 6, toHour: 18, point: "market_stand_1", behavior: "IdleBehavior", roamRadius: 3 }
 * { name: "tavern_rest", fromHour: 18, toHour: 22, point: "tavern_seat_5", behavior: "IdleBehavior", roamRadius: 0 }
 * { name: "sleeping",    fromHour: 22, toHour: 6, present: false }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitySchedulePhase {

    /**
     * Unique technical name for this phase (e.g. "market_duty").
     * Stored in Redis as the current schedule phase for external queries.
     */
    private String name;

    /**
     * Start world-hour (inclusive). 0..hoursPerDay-1.
     */
    private int fromHour;

    /**
     * End world-hour (exclusive). Wrap-around supported:
     * fromHour=22, toHour=6 means 22:00 to 05:59.
     */
    private int toHour;

    /**
     * Whether the NPC is present during this phase.
     * If false, the NPC sends Gone and is removed from simulation.
     * Default: true.
     */
    @Builder.Default
    private boolean present = true;

    /**
     * Target location name (WEntryPoint name or coordinate key).
     * If null, the entity stays at its current location.
     */
    private String point;

    /**
     * Override behavior for this phase (e.g. "IdleBehavior", "PreyAnimalBehavior").
     * If null, uses the entity's default behaviorModel.
     */
    private String behavior;

    /**
     * Override roam radius for this phase (blocks).
     * If null, uses the entity's default radius.
     */
    private Double roamRadius;

    /**
     * Override speed for this phase (blocks/second).
     * If null, uses the entity's default speed.
     */
    private Double speed;

    /**
     * Check if a given world-hour falls within this phase.
     * Handles wrap-around (e.g. fromHour=22, toHour=6).
     */
    public boolean matchesHour(int hour) {
        if (fromHour < toHour) {
            return hour >= fromHour && hour < toHour;
        }
        // Wrap-around
        return hour >= fromHour || hour < toHour;
    }
}
