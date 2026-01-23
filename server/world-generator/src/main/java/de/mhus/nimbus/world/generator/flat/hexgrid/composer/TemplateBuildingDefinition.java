package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a building in a village template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateBuildingDefinition {
    /**
     * Unique identifier for this building
     */
    private String id;

    /**
     * Type of building (CHURCH, HOUSE, etc.)
     */
    private BuildingType type;

    /**
     * Local X position in village coordinate system
     */
    private int localX;

    /**
     * Local Z position in village coordinate system
     */
    private int localZ;

    /**
     * Rotation in 90° steps (0-3)
     */
    private int rotation;

    /**
     * Size category: "small", "medium", "large"
     */
    private String size;

    /**
     * If true, a road will be created connecting this building to the plaza
     */
    private boolean connectToPlaza;
}
