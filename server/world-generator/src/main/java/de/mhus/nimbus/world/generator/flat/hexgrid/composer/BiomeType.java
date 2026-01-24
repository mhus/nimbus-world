package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rich Enum defining all biome types with their default builder configurations.
 * Each biome type specifies:
 * - Default builder to use (e.g., "mountain", "island")
 * - Default parameters for terrain generation
 * - Associated biome class for type-specific behavior
 */
public enum BiomeType implements TsEnum {
    MOUNTAINS(MountainBiome.class, "mountain", Map.of(
        "g_offset", "30",
        "g_roughness", "0.8"
    )),

    FOREST(ForestBiome.class, "mountain", Map.of(
        "g_offset", "2",
        "g_flora", "forest",
        "flora_density", "0.8"
    )),

    PLAINS(PlainsBiome.class, "island", Map.of(
        "g_offset", "1"
    )),

    DESERT(DesertBiome.class, "mountain", Map.of(
        "g_offset", "5",
        "g_flora", "desert",
        "cactus_density", "0.3"
    )),

    SWAMP(SwampBiome.class, "coast", Map.of(
        "g_offset", "1",
        "g_water", "true"
    )),

    VILLAGE(Biome.class, "island", Map.of(
        "g_offset", "1"
    )),

    TOWN(Biome.class, "island", Map.of(
        "g_offset", "1"
    )),

    COAST(CoastBiome.class, "coast", Map.of()),

    ISLAND(IslandBiome.class, "island", Map.of()),

    OCEAN(OceanBiome.class, "ocean", Map.of());

    private final Class<? extends Biome> biomeClass;
    private final String defaultBuilder;
    private final Map<String, String> defaultParameters;

    BiomeType(Class<? extends Biome> biomeClass, String defaultBuilder, Map<String, String> defaultParameters) {
        this.biomeClass = biomeClass;
        this.defaultBuilder = defaultBuilder;
        this.defaultParameters = Collections.unmodifiableMap(new HashMap<>(defaultParameters));
    }

    /**
     * @deprecated Use getDefaultBuilder() instead
     */
    @Deprecated
    public String getBuilderName() {
        return defaultBuilder;
    }

    public String getDefaultBuilder() {
        return defaultBuilder;
    }

    public Map<String, String> getDefaultParameters() {
        return defaultParameters;
    }

    public Class<? extends Biome> getBiomeClass() {
        return biomeClass;
    }

    /**
     * Creates a new instance of the biome with default configuration applied.
     */
    public Biome createInstance() {
        try {
            Biome biome = biomeClass.getDeclaredConstructor().newInstance();
            biome.setType(this);
            biome.applyDefaults();
            return biome;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create biome instance for type: " + this, e);
        }
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static BiomeType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BiomeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BiomeType value: " + value);
        }
    }
}
