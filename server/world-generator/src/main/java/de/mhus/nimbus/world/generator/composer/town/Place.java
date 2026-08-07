package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Place represents a plot within a district where something can be built.
 * Places define the layout of a district and can be buildings, free spaces, roads, rivers, or walls.
 *
 * This is an abstract base class. Use concrete subclasses:
 * - BuildingPlace: A plot for a building
 * - FreePlace: An open space (park, square, garden, plaza)
 * - RoadPlace: A street or path
 * - RiverPlace: A water feature
 * - WallPlace: A defensive or boundary structure
 */
@Data
@SuperBuilder
@NoArgsConstructor
// PRIVATE on purpose: a public all-args constructor is picked up by Jackson 3 as a
// properties-based creator, which bypasses the no-args constructor and thus every
// @Builder.Default value. Only the builder needs this constructor.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "placeType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BuildingPlace.class, name = "building"),
    @JsonSubTypes.Type(value = FreePlace.class, name = "free"),
    @JsonSubTypes.Type(value = RoadPlace.class, name = "road"),
    @JsonSubTypes.Type(value = RiverPlace.class, name = "river"),
    @JsonSubTypes.Type(value = WallPlace.class, name = "wall")
})
public abstract class Place {

    /**
     * Technical unique name of this place.
     * Used for referencing and identification.
     * Example: "main-house", "town-hall", "market-square"
     */
    private String name;

    /**
     * Whether this place is a connection point for roads, rivers, or walls.
     * Connection points generate actual Point features that can be used by
     * Road/River/Wall builders to connect structures.
     */
    @Builder.Default
    private boolean connectionPoint = false;

    /**
     * Level offset relative to the town's baseLevel.
     * The actual place level is calculated as: baseLevel + levelOffset.
     * Positive values raise the place (e.g., castle on a hill),
     * negative values lower it (e.g., cellar, canal).
     */
    @Builder.Default
    private int levelOffset = 0;
}
