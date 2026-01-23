package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Defines a street/road in a village template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateStreetDefinition {
    /**
     * Unique identifier for this street
     */
    private String id;

    /**
     * Path through local coordinates
     */
    private List<LocalPosition> path;

    /**
     * Street width in blocks
     */
    private int width;

    /**
     * Street type (e.g., "street", "trail", "cobblestone")
     */
    private String type;
}
