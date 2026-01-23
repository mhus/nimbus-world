package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a plaza/square in a village template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlazaDefinition {
    /**
     * Local X position in village coordinate system
     */
    private int localX;

    /**
     * Local Z position in village coordinate system
     */
    private int localZ;

    /**
     * Plaza radius in blocks
     */
    private int size;

    /**
     * Plaza height level
     */
    private int level;

    /**
     * Plaza material (e.g., "street", "cobblestone")
     */
    private String material;
}
