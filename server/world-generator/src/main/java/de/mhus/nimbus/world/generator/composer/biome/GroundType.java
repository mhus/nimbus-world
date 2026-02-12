package de.mhus.nimbus.world.generator.composer.biome;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Ground type configuration for biomes.
 * Defines material mappings for different ground surface types.
 * Each ground type specifies which materials to use for different elevation zones.
 */
@Getter
public enum GroundType {
    /**
     * Default ground type using standard materials.
     * Sand at ocean level, grass on land, stone at high elevations, snow at peaks.
     */
    DEFAULT(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.GRASS,        // grassMaterial
        FlatMaterialService.DIRT,         // dirtMaterial
        FlatMaterialService.STONE,        // stoneMaterial
        FlatMaterialService.SNOW          // snowMaterial
    ),

    /**
     * Snowy ground type - everything covered in snow.
     * Snow at all elevations above ocean level.
     */
    SNOWY(
        FlatMaterialService.SAND,         // sandMaterial (ocean level)
        FlatMaterialService.SNOW,         // grassMaterial (low elevation)
        FlatMaterialService.SNOW,         // dirtMaterial
        FlatMaterialService.SNOW,         // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    ),

    /**
     * Sandy ground type - desert-like terrain.
     * Desert sand at most elevations, stone at high points.
     */
    SANDY(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.DESERT_SAND,  // grassMaterial (low elevation)
        FlatMaterialService.DESERT_SAND,  // dirtMaterial
        FlatMaterialService.STONE,        // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    ),

    /**
     * Grassy ground type - lush grasslands.
     * Grass at all elevations, minimal dirt/stone.
     */
    GRASSY(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.GRASS,        // grassMaterial (low elevation)
        FlatMaterialService.GRASS,        // dirtMaterial
        FlatMaterialService.GRASS,        // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    ),

    /**
     * Stony ground type - rocky terrain.
     * Stone at most elevations.
     */
    STONY(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.STONE,        // grassMaterial (low elevation)
        FlatMaterialService.STONE,        // dirtMaterial
        FlatMaterialService.STONE,        // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    ),

    /**
     * Swampy ground type - muddy wetlands.
     * Swamp material at most elevations, wet appearance.
     */
    SWAMPY(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.SWAMP,        // grassMaterial (low elevation) - use SWAMP material
        FlatMaterialService.SWAMP,        // dirtMaterial
        FlatMaterialService.DIRT,         // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    ),

    /**
     * Volcanic ground type - dark rocky terrain.
     * Bedrock and stone for volcanic appearance.
     */
    VOLCANIC(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.BEDROCK,      // grassMaterial (low elevation)
        FlatMaterialService.BEDROCK,      // dirtMaterial
        FlatMaterialService.BEDROCK,      // stoneMaterial (high elevation)
        FlatMaterialService.STONE         // snowMaterial (peaks - stone instead of snow)
    ),

    /**
     * Icy ground type - frozen terrain.
     * Ice and snow for frozen appearance.
     */
    ICY(
        FlatMaterialService.SAND,         // sandMaterial
        FlatMaterialService.ICE,          // grassMaterial (low elevation) - use ICE material
        FlatMaterialService.ICE,          // dirtMaterial
        FlatMaterialService.ICE,          // stoneMaterial (high elevation)
        FlatMaterialService.SNOW          // snowMaterial (peaks)
    );

    private final int sandMaterial;
    private final int grassMaterial;
    private final int dirtMaterial;
    private final int stoneMaterial;
    private final int snowMaterial;

    GroundType(int sandMaterial, int grassMaterial, int dirtMaterial, int stoneMaterial, int snowMaterial) {
        this.sandMaterial = sandMaterial;
        this.grassMaterial = grassMaterial;
        this.dirtMaterial = dirtMaterial;
        this.stoneMaterial = stoneMaterial;
        this.snowMaterial = snowMaterial;
    }

    /**
     * Get material parameters as a map suitable for builder parameters.
     * @return Map with material parameter names and values
     */
    public Map<String, String> getMaterialParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("sandMaterial", String.valueOf(sandMaterial));
        params.put("grassMaterial", String.valueOf(grassMaterial));
        params.put("dirtMaterial", String.valueOf(dirtMaterial));
        params.put("stoneMaterial", String.valueOf(stoneMaterial));
        params.put("snowMaterial", String.valueOf(snowMaterial));
        return params;
    }

    /**
     * Apply material parameters to an existing parameter map.
     * @param parameters The parameter map to update
     */
    public void applyToParameters(Map<String, String> parameters) {
        parameters.putAll(getMaterialParameters());
    }

    /**
     * Get desert sand material if this ground type uses desert materials.
     * @return Desert sand material ID or regular desert sand as fallback
     */
    public int getDesertSandMaterial() {
        // For sandy types, use desert sand; otherwise use the grass material
        if (this == SANDY) {
            return FlatMaterialService.DESERT_SAND;
        }
        return grassMaterial;
    }
}
