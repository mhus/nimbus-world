package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BiomeType Rich Enum pattern
 */
class BiomeTypeTest {

    @Test
    void testCreateInstance_Mountains_ReturnsMountainBiome() {
        Biome biome = BiomeType.MOUNTAINS.createInstance();

        assertNotNull(biome);
        assertInstanceOf(MountainBiome.class, biome);
        assertEquals(BiomeType.MOUNTAINS, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("mountain", biome.getParameters().get("g_builder"));
        assertEquals("30", biome.getParameters().get("g_offset"));
        assertEquals("0.8", biome.getParameters().get("g_roughness"));
    }

    @Test
    void testCreateInstance_Forest_ReturnsForestBiome() {
        Biome biome = BiomeType.FOREST.createInstance();

        assertNotNull(biome);
        assertInstanceOf(ForestBiome.class, biome);
        assertEquals(BiomeType.FOREST, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("mountain", biome.getParameters().get("g_builder"));
        assertEquals("2", biome.getParameters().get("g_offset"));
        assertEquals("forest", biome.getParameters().get("g_flora"));
        assertEquals("0.8", biome.getParameters().get("flora_density"));
    }

    @Test
    void testCreateInstance_Plains_ReturnsPlainsBiome() {
        Biome biome = BiomeType.PLAINS.createInstance();

        assertNotNull(biome);
        assertInstanceOf(PlainsBiome.class, biome);
        assertEquals(BiomeType.PLAINS, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("mountain", biome.getParameters().get("g_builder"));
        assertEquals("1", biome.getParameters().get("g_offset"));
    }

    @Test
    void testCreateInstance_Desert_ReturnsDesertBiome() {
        Biome biome = BiomeType.DESERT.createInstance();

        assertNotNull(biome);
        assertInstanceOf(DesertBiome.class, biome);
        assertEquals(BiomeType.DESERT, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("mountain", biome.getParameters().get("g_builder"));
        assertEquals("5", biome.getParameters().get("g_offset"));
        assertEquals("desert", biome.getParameters().get("g_flora"));
        assertEquals("0.3", biome.getParameters().get("cactus_density"));
    }

    @Test
    void testCreateInstance_Swamp_ReturnsSwampBiome() {
        Biome biome = BiomeType.SWAMP.createInstance();

        assertNotNull(biome);
        assertInstanceOf(SwampBiome.class, biome);
        assertEquals(BiomeType.SWAMP, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("coast", biome.getParameters().get("g_builder"));
        assertEquals("1", biome.getParameters().get("g_offset"));
        assertEquals("true", biome.getParameters().get("g_water"));
    }

    @Test
    void testCreateInstance_Coast_ReturnsCoastBiome() {
        Biome biome = BiomeType.COAST.createInstance();

        assertNotNull(biome);
        assertInstanceOf(CoastBiome.class, biome);
        assertEquals(BiomeType.COAST, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("coast", biome.getParameters().get("g_builder"));
    }

    @Test
    void testCreateInstance_Island_ReturnsIslandBiome() {
        Biome biome = BiomeType.ISLAND.createInstance();

        assertNotNull(biome);
        assertInstanceOf(IslandBiome.class, biome);
        assertEquals(BiomeType.ISLAND, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("island", biome.getParameters().get("g_builder"));
    }

    @Test
    void testCreateInstance_Ocean_ReturnsOceanBiome() {
        Biome biome = BiomeType.OCEAN.createInstance();

        assertNotNull(biome);
        assertInstanceOf(OceanBiome.class, biome);
        assertEquals(BiomeType.OCEAN, biome.getType());
        assertNotNull(biome.getParameters());
        assertEquals("ocean", biome.getParameters().get("g_builder"));
    }

    @Test
    void testDefaultParameters_MultipleBuildersSameClass() {
        // Test that different biome types can use same builder with different parameters
        Biome forest = BiomeType.FOREST.createInstance();
        Biome mountains = BiomeType.MOUNTAINS.createInstance();

        // Both use MountainBuilder
        assertEquals("mountain", forest.getParameters().get("g_builder"));
        assertEquals("mountain", mountains.getParameters().get("g_builder"));

        // But with different offsets
        assertEquals("2", forest.getParameters().get("g_offset"));  // Flat hills
        assertEquals("30", mountains.getParameters().get("g_offset"));  // High peaks

        // Different classes
        assertInstanceOf(ForestBiome.class, forest);
        assertInstanceOf(MountainBiome.class, mountains);
    }

    @Test
    void testDefaultParameters_CanBeOverridden() {
        Biome biome = BiomeType.MOUNTAINS.createInstance();

        // Default offset is 30
        assertEquals("30", biome.getParameters().get("g_offset"));

        // Can be overridden
        biome.getParameters().put("g_offset", "50");
        assertEquals("50", biome.getParameters().get("g_offset"));
    }

    @Test
    void testGetDefaultBuilder() {
        assertEquals("mountain", BiomeType.MOUNTAINS.getDefaultBuilder());
        assertEquals("mountain", BiomeType.FOREST.getDefaultBuilder());
        assertEquals("mountain", BiomeType.PLAINS.getDefaultBuilder());
        assertEquals("mountain", BiomeType.DESERT.getDefaultBuilder());
        assertEquals("coast", BiomeType.SWAMP.getDefaultBuilder());
        assertEquals("coast", BiomeType.COAST.getDefaultBuilder());
        assertEquals("island", BiomeType.ISLAND.getDefaultBuilder());
        assertEquals("ocean", BiomeType.OCEAN.getDefaultBuilder());
    }

    @Test
    void testFromString() {
        assertEquals(BiomeType.MOUNTAINS, BiomeType.fromString("mountains"));
        assertEquals(BiomeType.MOUNTAINS, BiomeType.fromString("MOUNTAINS"));
        assertEquals(BiomeType.FOREST, BiomeType.fromString("forest"));
        assertNull(BiomeType.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> BiomeType.fromString("invalid"));
    }

    @Test
    void testTsString() {
        assertEquals("mountains", BiomeType.MOUNTAINS.tsString());
        assertEquals("forest", BiomeType.FOREST.tsString());
        assertEquals("plains", BiomeType.PLAINS.tsString());
        assertEquals("desert", BiomeType.DESERT.tsString());
        assertEquals("swamp", BiomeType.SWAMP.tsString());
        assertEquals("coast", BiomeType.COAST.tsString());
        assertEquals("island", BiomeType.ISLAND.tsString());
        assertEquals("ocean", BiomeType.OCEAN.tsString());
    }

    @Test
    void testGetBiomeClass() {
        assertEquals(MountainBiome.class, BiomeType.MOUNTAINS.getBiomeClass());
        assertEquals(ForestBiome.class, BiomeType.FOREST.getBiomeClass());
        assertEquals(PlainsBiome.class, BiomeType.PLAINS.getBiomeClass());
        assertEquals(DesertBiome.class, BiomeType.DESERT.getBiomeClass());
        assertEquals(SwampBiome.class, BiomeType.SWAMP.getBiomeClass());
        assertEquals(CoastBiome.class, BiomeType.COAST.getBiomeClass());
        assertEquals(IslandBiome.class, BiomeType.ISLAND.getBiomeClass());
        assertEquals(OceanBiome.class, BiomeType.OCEAN.getBiomeClass());
    }
}
