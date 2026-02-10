package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * RiverPlace represents a water feature within a district.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiverPlace extends Place {

    /**
     * Type of water feature.
     */
    private RiverKind kind;

    /**
     * Types of water features available.
     */
    public enum RiverKind {
        /**
         * Small stream
         */
        STREAM,

        /**
         * Man-made canal
         */
        CANAL,

        /**
         * Natural river
         */
        RIVER
    }
}
