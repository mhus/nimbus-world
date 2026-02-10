package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
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
    private boolean connectionPoint;
}
