package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Defines a continent - a landmass that fills gaps between biomes with the same continentId.
 * Continents provide a way to create cohesive landmasses where biomes are connected
 * by the same terrain type instead of ocean.
 *
 * Example:
 * - Continent "middle-earth-continent" with biomeType PLAINS
 * - All biomes with continentId "middle-earth-continent" will have gaps filled with PLAINS
 * - Creates a unified landmass instead of isolated islands
 */
@Data
@Builder
@NoArgsConstructor
// PRIVATE on purpose: a public all-args constructor is picked up by Jackson 3 as a
// properties-based creator, which bypasses the no-args constructor and thus every
// @Builder.Default value. Only the builder needs this constructor.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Continent {
    /**
     * Unique identifier for this continent
     */
    private String continentId;

    /**
     * Biome type used to fill gaps between biomes on this continent
     */
    private BiomeType biomeType;

    /**
     * Optional parameters for the filler biome (e.g., landLevel, g_builder)
     */
    private Map<String, String> parameters;

    /**
     * Minimum number of neighbors with same continentId required to fill a gap
     * Default: 2 (at least 2 neighbors from same continent)
     */
    @Builder.Default
    private int minNeighbors = 2;

    /**
     * Human-readable name for this continent
     */
    private String name;

    /**
     * Description of this continent
     */
    private String description;
}
