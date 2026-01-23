package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

/**
 * Prepared position with concrete distance ranges and direction angles.
 */
@Data
public class PreparedPosition {
    private RelativePosition original;

    // Direction as enum and as angle
    private Direction direction;
    private int directionAngle;  // 0-360 degrees (0=N, 60=NE, 120=E, etc.)

    // Concrete distance values
    private int distanceFrom;
    private int distanceTo;

    // Reference point
    private String anchor;  // null = origin (0,0)

    // Priority for positioning (1-10)
    private int priority;
}
