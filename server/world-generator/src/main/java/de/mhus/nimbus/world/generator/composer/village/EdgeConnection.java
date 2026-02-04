package de.mhus.nimbus.world.generator.composer.village;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the connection point where a street crosses the boundary between two districts.
 * Contains the edge coordinates on both sides of the boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeConnection {

    /**
     * X coordinate at the edge of district 1 (local coordinates)
     */
    private int edge1X;

    /**
     * Z coordinate at the edge of district 1 (local coordinates)
     */
    private int edge1Z;

    /**
     * X coordinate at the edge of district 2 (local coordinates)
     */
    private int edge2X;

    /**
     * Z coordinate at the edge of district 2 (local coordinates)
     */
    private int edge2Z;
}
