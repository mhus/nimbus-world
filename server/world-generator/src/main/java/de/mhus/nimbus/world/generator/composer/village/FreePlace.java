package de.mhus.nimbus.world.generator.composer.village;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * FreePlace represents an open space within a district.
 * Can be parks, squares, gardens, or plazas.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FreePlace extends Place {

    /**
     * Type of free space.
     */
    private FreeKind kind;

    /**
     * Types of free spaces available.
     */
    public enum FreeKind {
        /**
         * Park with trees and nature
         */
        PARK,

        /**
         * Open square for gatherings
         */
        SQUARE,

        /**
         * Garden with plants and flowers
         */
        GARDEN,

        /**
         * Plaza for markets and events
         */
        PLAZA
    }
}
