package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * BuildingPlace represents a plot where a building can be constructed.
 * Defines the building type (kind) and optionally overrides the village style.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildingPlace extends Place {

    /**
     * Building kind/type (freeform string).
     * Defines what type of building this is.
     * Examples: "house", "shop", "tavern", "church", "tower", "warehouse", "smithy"
     */
    private String kind;

    /**
     * Building style override (optional).
     * If set, overrides the village's style for this specific building.
     * If not set, uses the village's style.
     * Examples: "medieval", "modern", "fantasy", "ancient"
     */
    private String style;
}
