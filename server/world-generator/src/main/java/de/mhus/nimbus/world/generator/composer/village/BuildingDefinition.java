package de.mhus.nimbus.world.generator.composer.village;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.Vector3Int;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BuildingDefinition represents a building type that can be placed in a village.
 * Defines the building's properties including style, kind, and physical dimensions.
 *
 * Buildings are indexed by style and kind, allowing the village builder to
 * select appropriate buildings for each building place.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildingDefinition {

    /**
     * Unique identifier for this building definition.
     * Example: "medieval-house-01", "fantasy-tower-large", "modern-shop-corner"
     */
    private String buildingId;

    /**
     * Display name of the building.
     * Human-readable name shown to users.
     * Example: "Thatched Cottage", "Wizard's Tower", "Corner Shop"
     */
    private String title;

    /**
     * Building style.
     * Matches the village or place style.
     * Examples: "medieval", "modern", "fantasy", "ancient", "industrial"
     */
    private String style;

    /**
     * Building kind/type.
     * Defines the functional type of the building.
     * Examples: "house", "tavern", "shop", "workshop", "church", "tower"
     */
    private String kind;

    /**
     * Physical dimensions of the building in blocks.
     * - x: Width
     * - y: Height
     * - z: Depth
     *
     * Used to determine if the building fits in the available plot space.
     */
    private Vector3Int dimensions;
}
