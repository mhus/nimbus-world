package de.mhus.nimbus.world.life.model;

import de.mhus.nimbus.generated.types.Vector3;
import lombok.Builder;
import lombok.Data;

/**
 * Context for pathway generation.
 * Provides all necessary information for behaviors to generate pathways.
 */
@Data
@Builder
public class PathwayGenerationContext {

    /**
     * World identifier.
     */
    private String worldId;

    /**
     * Entity's current position.
     */
    private Vector3 position;

    /**
     * Entity's current rotation.
     */
    private de.mhus.nimbus.generated.types.Rotation rotation;

    /**
     * Entity movement speed (blocks per second).
     */
    private double speed;

    /**
     * Current simulation time (milliseconds).
     */
    private long currentTime;

    /**
     * Entity's spawn/home position (for behaviors that roam around a center point).
     */
    private Vector3 homePosition;

    /**
     * Roaming radius (for behaviors that stay within a certain distance).
     */
    private Double roamingRadius;

    /**
     * Behavior-specific configuration.
     * Can be used to pass custom parameters to behaviors.
     */
    private java.util.Map<String, Object> behaviorConfig;
}
