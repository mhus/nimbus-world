package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * RoadPlace represents a street, path, or trail within a district.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoadPlace extends Place {

    /**
     * Type of road.
     */
    private RoadKind kind;

    /**
     * Types of roads available.
     */
    public enum RoadKind {
        /**
         * Main street, paved and wide
         */
        STREET,

        /**
         * Narrow path or trail
         */
        TRAIL,

        /**
         * Small alleyway between buildings
         */
        ALLEY
    }
}
