package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * WallPlace represents a defensive or boundary structure within a district.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WallPlace extends Place {

    /**
     * Type of wall or fence.
     */
    private WallKind kind;

    /**
     * Types of walls and fences available.
     */
    public enum WallKind {
        /**
         * Stone wall for defense
         */
        STONE_WALL,

        /**
         * Wooden fence for boundaries
         */
        WOODEN_FENCE,

        /**
         * Hedge fence for natural boundaries
         */
        HEDGE_FENCE
    }
}
