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
    MOUNTAINS(MountainBiome.class, "mountain", Map.ofEntries(
        Map.entry("g_offset", "30"),
        Map.entry("g_roughness", "0.8"),
        Map.entry("gf_flora", "mountain"),
        Map.entry("gf_density", "0.1"),
        Map.entry("gf_fauna", "mountain"),
        Map.entry("ge_weather", "{\"base\":\"cloudy\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":1.0,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"wind\",\"fog\"]},\"overcast\":{\"weight\":0.5,\"duration\":[60,300],\"next\":[\"rain\",\"snow\",\"cloudy\"]},\"rain\":{\"weight\":0.3,\"duration\":[60,300],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[30,70]}},\"snow\":{\"weight\":0.2,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"blizzard\"],\"params\":{\"intensity\":[20,60]}},\"thunderstorm\":{\"weight\":0.08,\"duration\":[60,180],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[60,100]}},\"blizzard\":{\"weight\":0.03,\"duration\":[60,180],\"next\":[\"snow\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"fog\":{\"weight\":0.2,\"duration\":[60,300],\"next\":[\"cloudy\",\"clear\"]},\"wind\":{\"weight\":0.25,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"snow\":0.6,\"blizzard\":0.1,\"rain\":-0.3,\"clear\":-0.3},\"spring\":{\"rain\":0.2,\"fog\":0.1},\"summer\":{\"clear\":0.4,\"thunderstorm\":0.1,\"snow\":-0.2},\"autumn\":{\"fog\":0.2,\"wind\":0.2,\"snow\":0.1}}}")
    )),

    FOREST(ForestBiome.class, "forest", Map.ofEntries(
        Map.entry("g_offset", "5"),
        Map.entry("g_asl", "20"),
        Map.entry("dirtRatio", "0.3"),
        Map.entry("gf_flora", "forest_mixed"),
        Map.entry("gf_density", "0.8"),
        Map.entry("gf_fauna", "forest"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[300,900],\"next\":[\"cloudy\",\"wind\",\"fog\"]},\"cloudy\":{\"weight\":0.7,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"rain\"]},\"overcast\":{\"weight\":0.4,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.35,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[20,70]}},\"thunderstorm\":{\"weight\":0.05,\"duration\":[60,240],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"fog\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]},\"wind\":{\"weight\":0.1,\"duration\":[60,180],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"snow\":0.5,\"rain\":-0.2,\"fog\":0.15},\"spring\":{\"rain\":0.2,\"cloudy\":0.1},\"summer\":{\"clear\":0.4,\"thunderstorm\":0.1},\"autumn\":{\"fog\":0.2,\"overcast\":0.2,\"wind\":0.15}}}")
    )),

    PLAINS(PlainsBiome.class, "plains", Map.ofEntries(
        Map.entry("g_offset", "5"),
        Map.entry("g_asl", "15"),
        Map.entry("dirtRatio", "0.1"),
        Map.entry("enableLakes", "true"),
        Map.entry("lakeDepth", "4"),
        Map.entry("gf_flora", "plains_flower"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "plains"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.4,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[300,1200],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":0.5,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"rain\"]},\"overcast\":{\"weight\":0.3,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.25,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[20,60]}},\"thunderstorm\":{\"weight\":0.05,\"duration\":[60,180],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,80]}},\"wind\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"snow\":0.4,\"rain\":-0.2},\"spring\":{\"rain\":0.15,\"cloudy\":0.1},\"summer\":{\"clear\":0.5,\"heatwave\":0.05},\"autumn\":{\"fog\":0.1,\"overcast\":0.15,\"wind\":0.2}}}")
    )),

    DESERT(DesertBiome.class, "desert", Map.ofEntries(
        Map.entry("g_offset", "15"),
        Map.entry("g_asl", "30"),
        Map.entry("dirtRatio", "0.05"),
        Map.entry("stoneRatio", "0.3"),
        Map.entry("gf_flora", "desert"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "desert"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.6,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[600,1800],\"next\":[\"heatwave\",\"wind\"]},\"heatwave\":{\"weight\":0.3,\"duration\":[300,900],\"next\":[\"clear\"]},\"wind\":{\"weight\":0.2,\"duration\":[120,600],\"next\":[\"clear\",\"sandstorm\"]},\"sandstorm\":{\"weight\":0.05,\"duration\":[60,300],\"next\":[\"wind\",\"clear\"],\"params\":{\"intensity\":[40,90]}}},\"seasonModifier\":{\"winter\":{\"clear\":0.3,\"heatwave\":-0.2},\"summer\":{\"heatwave\":0.3,\"sandstorm\":0.05}}}")
    )),

    SWAMP(SwampBiome.class, "swamp", Map.ofEntries(
        Map.entry("g_offset", "10"),
        Map.entry("g_asl", "5"),
        Map.entry("swampDepth", "3"),
        Map.entry("gf_flora", "swamp"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "swamp"),
        Map.entry("gf_water_flora", "swamp_water"),
        Map.entry("gf_water_density", "0.5"),
        Map.entry("ge_weather", "{\"base\":\"overcast\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":0.3,\"duration\":[120,300],\"next\":[\"cloudy\",\"fog\"]},\"cloudy\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"overcast\",\"rain\",\"fog\"]},\"overcast\":{\"weight\":1.0,\"duration\":[120,600],\"next\":[\"rain\",\"fog\",\"cloudy\"]},\"rain\":{\"weight\":0.5,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[30,80]}},\"thunderstorm\":{\"weight\":0.08,\"duration\":[60,240],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"fog\":{\"weight\":0.4,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"fog\":0.2,\"rain\":-0.1},\"spring\":{\"rain\":0.2},\"summer\":{\"thunderstorm\":0.1},\"autumn\":{\"fog\":0.3,\"overcast\":0.2}}}")
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
        Map.entry("gf_water_density", "0.4"),
        Map.entry("ge_weather", "{\"base\":\"overcast\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":0.3,\"duration\":[120,300],\"next\":[\"cloudy\",\"fog\"]},\"cloudy\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"overcast\",\"rain\",\"fog\"]},\"overcast\":{\"weight\":1.0,\"duration\":[120,600],\"next\":[\"rain\",\"fog\",\"cloudy\"]},\"rain\":{\"weight\":0.5,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[30,80]}},\"thunderstorm\":{\"weight\":0.08,\"duration\":[60,240],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"fog\":{\"weight\":0.4,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"fog\":0.2,\"rain\":-0.1},\"spring\":{\"rain\":0.2},\"summer\":{\"thunderstorm\":0.1},\"autumn\":{\"fog\":0.3,\"overcast\":0.2}}}")
    )),

    TOWN(Biome.class, "island", Map.ofEntries(
        Map.entry("g_offset", "1"),
        Map.entry("gf_flora", "town"),
        Map.entry("gf_density", "0.05"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.4,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[300,1200],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":0.5,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"rain\"]},\"overcast\":{\"weight\":0.3,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.25,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[20,60]}},\"thunderstorm\":{\"weight\":0.05,\"duration\":[60,180],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,80]}},\"wind\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"snow\":0.4,\"rain\":-0.2},\"spring\":{\"rain\":0.15,\"cloudy\":0.1},\"summer\":{\"clear\":0.5,\"heatwave\":0.05},\"autumn\":{\"fog\":0.1,\"overcast\":0.15,\"wind\":0.2}}}")
    )),

    COAST(CoastBiome.class, "coast", Map.ofEntries(
        Map.entry("gf_flora", "coast"),
        Map.entry("gf_density", "0.1"),
        Map.entry("gf_fauna", "coast"),
        Map.entry("gf_sea_flora", "coast_sea"),
        Map.entry("gf_sea_density", "0.2"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[300,900],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"rain\",\"wind\"]},\"overcast\":{\"weight\":0.3,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.3,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[20,70]}},\"thunderstorm\":{\"weight\":0.08,\"duration\":[60,240],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"wind\":{\"weight\":0.3,\"duration\":[120,600],\"next\":[\"clear\",\"cloudy\",\"rain\"]},\"fog\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"wind\":0.3,\"rain\":0.1,\"snow\":0.1},\"spring\":{\"fog\":0.1},\"summer\":{\"clear\":0.3},\"autumn\":{\"wind\":0.2,\"fog\":0.15,\"rain\":0.1}}}")
    )),

    ISLAND(IslandBiome.class, "island", Map.ofEntries(
        Map.entry("gf_flora", "island_tropical"),
        Map.entry("gf_density", "0.3"),
        Map.entry("gf_fauna", "island"),
        Map.entry("ge_weather", "{\"base\":\"clear\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":1.0,\"duration\":[300,900],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"rain\",\"wind\"]},\"overcast\":{\"weight\":0.3,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.3,\"duration\":[120,600],\"next\":[\"overcast\",\"cloudy\",\"thunderstorm\"],\"params\":{\"intensity\":[20,70]}},\"thunderstorm\":{\"weight\":0.08,\"duration\":[60,240],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[50,90]}},\"wind\":{\"weight\":0.3,\"duration\":[120,600],\"next\":[\"clear\",\"cloudy\",\"rain\"]},\"fog\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"clear\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"wind\":0.3,\"rain\":0.1,\"snow\":0.1},\"spring\":{\"fog\":0.1},\"summer\":{\"clear\":0.3},\"autumn\":{\"wind\":0.2,\"fog\":0.15,\"rain\":0.1}}}")
    )),

    OCEAN(OceanBiome.class, "ocean", Map.ofEntries(
        Map.entry("gf_fauna", "ocean"),
        Map.entry("gf_sea_flora", "ocean"),
        Map.entry("gf_sea_density", "0.4"),
        Map.entry("ge_weather", "{\"base\":\"wind\",\"baseWeight\":0.3,\"scenarios\":{\"clear\":{\"weight\":0.5,\"duration\":[120,600],\"next\":[\"cloudy\",\"wind\"]},\"cloudy\":{\"weight\":0.6,\"duration\":[120,600],\"next\":[\"clear\",\"overcast\",\"wind\"]},\"overcast\":{\"weight\":0.4,\"duration\":[60,300],\"next\":[\"rain\",\"cloudy\"]},\"rain\":{\"weight\":0.35,\"duration\":[120,600],\"next\":[\"overcast\",\"thunderstorm\",\"wind\"],\"params\":{\"intensity\":[30,80]}},\"thunderstorm\":{\"weight\":0.1,\"duration\":[60,300],\"next\":[\"rain\",\"overcast\"],\"params\":{\"intensity\":[60,100]}},\"wind\":{\"weight\":1.0,\"duration\":[120,600],\"next\":[\"clear\",\"cloudy\",\"rain\"]},\"fog\":{\"weight\":0.15,\"duration\":[60,300],\"next\":[\"wind\",\"cloudy\"]}},\"seasonModifier\":{\"winter\":{\"thunderstorm\":0.15,\"wind\":0.2,\"snow\":0.1},\"summer\":{\"clear\":0.3},\"autumn\":{\"fog\":0.2,\"wind\":0.15}}}")
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
