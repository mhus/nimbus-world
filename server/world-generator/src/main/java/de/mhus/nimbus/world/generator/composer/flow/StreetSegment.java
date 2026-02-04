package de.mhus.nimbus.world.generator.composer.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a street segment within a district.
 * Street segments connect places and districts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreetSegment {

    /**
     * Starting local X coordinate
     */
    private int fromX;

    /**
     * Starting local Z coordinate
     */
    private int fromZ;

    /**
     * Ending local X coordinate
     */
    private int toX;

    /**
     * Ending local Z coordinate
     */
    private int toZ;

    /**
     * Street width in blocks
     */
    private int width;

    /**
     * Street type (e.g., "street", "path", "alley")
     */
    private String type;

    /**
     * Y-level of the street
     */
    private int level;
}
