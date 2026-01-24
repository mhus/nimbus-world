package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configureHexGrids() polymorphic grid configuration
 */
class ConfigureHexGridsTest {

    private HexVector2 hex(int q, int r) {
        return HexVector2.builder().q(q).r(r).build();
    }

    @Test
    void testBiomeConfiguresOwnGrids() {
        // Create mountain biome
        Biome mountain = Biome.builder()
            .type(BiomeType.MOUNTAINS)
            .parameters(new java.util.HashMap<>())
            .build();
        mountain.setName("test-mountains");
        mountain.applyDefaults();

        // Define coordinates
        List<HexVector2> coordinates = Arrays.asList(
            hex(0, 0),
            hex(1, 0),
            hex(0, 1)
        );

        // Let biome configure its own grids
        mountain.configureHexGrids(coordinates);

        // Verify FeatureHexGrids were created
        assertEquals(3, mountain.getHexGrids().size());

        // Verify each grid has correct configuration
        for (int i = 0; i < 3; i++) {
            FeatureHexGrid grid = mountain.getHexGrids().get(i);
            assertNotNull(grid.getCoordinate());
            assertEquals(coordinates.get(i).getQ(), grid.getCoordinate().getQ());
            assertEquals(coordinates.get(i).getR(), grid.getCoordinate().getR());

            // Check biome-specific parameters were applied
            assertEquals("mountain", grid.getParameters().get("g_builder"));
            assertEquals("30", grid.getParameters().get("g_offset"));
            assertEquals("0.8", grid.getParameters().get("g_roughness"));
            assertEquals("mountains", grid.getParameters().get("biome"));
            assertEquals("test-mountains", grid.getParameters().get("biomeName"));
        }
    }

    @Test
    void testForestBiomeHasCorrectParameters() {
        Biome forest = Biome.builder()
            .type(BiomeType.FOREST)
            .parameters(new java.util.HashMap<>())
            .build();
        forest.setName("dark-forest");
        forest.applyDefaults();

        List<HexVector2> coordinates = Arrays.asList(hex(5, 5));

        forest.configureHexGrids(coordinates);

        assertEquals(1, forest.getHexGrids().size());
        FeatureHexGrid grid = forest.getHexGrids().get(0);

        // Forest uses MountainBuilder with low offset
        assertEquals("mountain", grid.getParameters().get("g_builder"));
        assertEquals("2", grid.getParameters().get("g_offset"));  // Gentle hills

        // Forest has flora parameters
        assertEquals("forest", grid.getParameters().get("g_flora"));
        assertEquals("0.8", grid.getParameters().get("flora_density"));
    }

    @Test
    void testPreparedBiomeConfiguresOwnGrids() {
        PreparedBiome preparedBiome = new PreparedBiome();
        preparedBiome.setName("test-biome");
        preparedBiome.setType(BiomeType.DESERT);
        preparedBiome.setParameters(new java.util.HashMap<>());
        preparedBiome.getParameters().put("g_builder", "mountain");  // Explicitly set builder
        preparedBiome.getParameters().put("g_offset", "5");
        preparedBiome.getParameters().put("g_flora", "desert");

        List<HexVector2> coordinates = Arrays.asList(
            hex(10, 10),
            hex(11, 10)
        );

        preparedBiome.configureHexGrids(coordinates);

        assertEquals(2, preparedBiome.getHexGrids().size());

        FeatureHexGrid grid = preparedBiome.getHexGrids().get(0);
        assertEquals("mountain", grid.getParameters().get("g_builder"));  // Builder from parameters
        assertEquals("5", grid.getParameters().get("g_offset"));
        assertEquals("desert", grid.getParameters().get("g_flora"));
    }

    @Test
    void testBiomeCanOverrideDefaults() {
        // Create biome with custom parameters
        Biome mountain = Biome.builder()
            .type(BiomeType.MOUNTAINS)
            .parameters(new java.util.HashMap<>())
            .build();
        mountain.setName("extra-tall-mountains");
        mountain.applyDefaults();

        // Override default offset
        mountain.getParameters().put("g_offset", "50");  // Taller than default 30

        List<HexVector2> coordinates = Arrays.asList(hex(0, 0));
        mountain.configureHexGrids(coordinates);

        FeatureHexGrid grid = mountain.getHexGrids().get(0);
        assertEquals("50", grid.getParameters().get("g_offset"));  // Custom value applied
    }

    @Test
    void testConfigureHexGrids_EmptyCoordinates() {
        Biome biome = Biome.builder()
            .type(BiomeType.PLAINS)
            .parameters(new java.util.HashMap<>())
            .build();
        biome.setName("test-plains");

        // Empty coordinates
        biome.configureHexGrids(Arrays.asList());

        assertTrue(biome.getHexGrids().isEmpty());
    }

    @Test
    void testConfigureHexGrids_NullCoordinates() {
        Biome biome = Biome.builder()
            .type(BiomeType.PLAINS)
            .parameters(new java.util.HashMap<>())
            .build();
        biome.setName("test-plains");

        // Null coordinates
        biome.configureHexGrids(null);

        assertTrue(biome.getHexGrids().isEmpty());
    }

    @Test
    void testConfigureHexGrids_ClearsExistingConfigurations() {
        Biome biome = Biome.builder()
            .type(BiomeType.MOUNTAINS)
            .parameters(new java.util.HashMap<>())
            .build();
        biome.setName("test-mountains");
        biome.applyDefaults();

        // First configuration
        biome.configureHexGrids(Arrays.asList(hex(0, 0)));
        assertEquals(1, biome.getHexGrids().size());

        // Second configuration should replace first
        biome.configureHexGrids(Arrays.asList(hex(1, 1), hex(2, 2)));
        assertEquals(2, biome.getHexGrids().size());

        // Verify new coordinates
        assertEquals(1, biome.getHexGrids().get(0).getCoordinate().getQ());
        assertEquals(2, biome.getHexGrids().get(1).getCoordinate().getQ());
    }

    @Test
    void testFeatureGeneratedFlag() {
        Biome oceanBiome = Biome.builder()
            .type(BiomeType.OCEAN)
            .build();
        oceanBiome.setName("filler-ocean");

        // Not generated by default
        assertFalse(oceanBiome.isGenerated());

        // Mark as generated (by filler)
        oceanBiome.markAsGenerated();

        assertTrue(oceanBiome.isGenerated());
        assertEquals("true", oceanBiome.getMetadata().get("generated"));
    }

    @Test
    void testDifferentBiomeTypesHaveDifferentDefaults() {
        List<HexVector2> coords = Arrays.asList(hex(0, 0));

        // Mountains
        Biome mountains = BiomeType.MOUNTAINS.createInstance();
        mountains.setName("mountains");
        mountains.configureHexGrids(coords);
        assertEquals("30", mountains.getHexGrids().get(0).getParameters().get("g_offset"));

        // Forest (uses same builder but different offset)
        Biome forest = BiomeType.FOREST.createInstance();
        forest.setName("forest");
        forest.configureHexGrids(coords);
        assertEquals("2", forest.getHexGrids().get(0).getParameters().get("g_offset"));

        // Both use mountain builder
        assertEquals("mountain", mountains.getHexGrids().get(0).getParameters().get("g_builder"));
        assertEquals("mountain", forest.getHexGrids().get(0).getParameters().get("g_builder"));
    }
}
