package de.mhus.nimbus.world.generator.composer.biome;

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
        "g_roughness", "0.8",
        "gf_flora", "mountain",
        "gf_density", "0.1",
        "gf_fauna", "mountain"
    )),

    FOREST(ForestBiome.class, "forest", Map.of(
        "g_offset", "5",
        "g_asl", "20",
        "dirtRatio", "0.3",
        "gf_flora", "forest_mixed",
        "gf_density", "0.8",
        "gf_fauna", "forest"
    )),

    PLAINS(PlainsBiome.class, "plains", Map.ofEntries(
        Map.entry("g_offset", "5"),
        Map.entry("g_asl", "15"),
        Map.entry("dirtRatio", "0.1"),
        Map.entry("enableLakes", "true"),
        Map.entry("lakeDepth", "4"),
        Map.entry("gf_flora", "plains_flower"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "plains")
    )),

    DESERT(DesertBiome.class, "desert", Map.ofEntries(
        Map.entry("g_offset", "15"),
        Map.entry("g_asl", "30"),
        Map.entry("dirtRatio", "0.05"),
        Map.entry("stoneRatio", "0.3"),
        Map.entry("gf_flora", "desert"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "desert")
    )),

    SWAMP(SwampBiome.class, "swamp", Map.ofEntries(
        Map.entry("g_offset", "10"),
        Map.entry("g_asl", "5"),
        Map.entry("swampDepth", "3"),
        Map.entry("gf_flora", "swamp"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "swamp"),
        Map.entry("gf_water_flora", "swamp_water"),
        Map.entry("gf_water_density", "0.5")
    )),

    MARSH(MarshBiome.class, "swamp", Map.ofEntries(
        Map.entry("g_offset", "5"),
        Map.entry("g_asl", "2"),
        Map.entry("swampDepth", "3"),
        Map.entry("grassMaterial", "DIRT"),
        Map.entry("gf_flora", "marsh"),
        Map.entry("gf_density", "0.2"),
        Map.entry("gf_fauna", "marsh"),
        Map.entry("gf_water_flora", "marsh_water"),
        Map.entry("gf_water_density", "0.4")
    )),

    TOWN(Biome.class, "island", Map.of(
        "g_offset", "1",
        "gf_flora", "town",
        "gf_density", "0.05"
    )),

    COAST(CoastBiome.class, "coast", Map.ofEntries(
        Map.entry("gf_flora", "coast"),
        Map.entry("gf_density", "0.1"),
        Map.entry("gf_fauna", "coast"),
        Map.entry("gf_sea_flora", "coast_sea"),
        Map.entry("gf_sea_density", "0.2")
    )),

    ISLAND(IslandBiome.class, "island", Map.of(
        "gf_flora", "island_tropical",
        "gf_density", "0.3",
        "gf_fauna", "island"
    )),

    OCEAN(OceanBiome.class, "ocean", Map.of(
        "gf_fauna", "ocean",
        "gf_sea_flora", "ocean",
        "gf_sea_density", "0.4"
    ));

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
