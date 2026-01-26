package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructureType Rich Enum pattern
 */
class StructureTypeTest {

    private HexVector2 hex(int q, int r) {
        return HexVector2.builder().q(q).r(r).build();
    }

    @Test
    void testCreateInstance_Hamlet_ReturnsVillage() {
        Structure structure = StructureType.HAMLET.createInstance();

        assertNotNull(structure);
        assertInstanceOf(Village.class, structure);
        assertEquals(StructureType.HAMLET, structure.getType());

        Village village = (Village) structure;
        assertNotNull(village.getParameters());
        assertEquals("island", village.getParameters().get("g_builder"));
        assertEquals("1", village.getParameters().get("g_offset"));
        assertEquals("95", village.getParameters().get("default_level"));
    }

    @Test
    void testCreateInstance_Village_ReturnsVillage() {
        Structure structure = StructureType.VILLAGE.createInstance();

        assertNotNull(structure);
        assertInstanceOf(Village.class, structure);
        assertEquals(StructureType.VILLAGE, structure.getType());
    }

    @Test
    void testCreateInstance_Town_ReturnsTown() {
        Structure structure = StructureType.TOWN.createInstance();

        assertNotNull(structure);
        assertInstanceOf(Town.class, structure);
        assertEquals(StructureType.TOWN, structure.getType());

        Town town = (Town) structure;
        assertNotNull(town.getParameters());
        assertEquals("island", town.getParameters().get("g_builder"));
        assertEquals("false", town.getParameters().get("has_wall"));
    }

    @Test
    void testCreateInstance_City_ReturnsTown() {
        Structure structure = StructureType.CITY.createInstance();

        assertNotNull(structure);
        assertInstanceOf(Town.class, structure);
        assertEquals(StructureType.CITY, structure.getType());

        Town town = (Town) structure;
        assertEquals("true", town.getParameters().get("has_wall"));
        assertEquals("true", town.getParameters().get("has_districts"));
    }

    @Test
    void testDefaultParameters_Hamlet() {
        var defaults = StructureType.HAMLET.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("1", defaults.get("g_offset"));
        assertEquals("95", defaults.get("default_level"));
        assertEquals("1", defaults.get("default_material"));
    }

    @Test
    void testDefaultParameters_Town() {
        var defaults = StructureType.TOWN.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("false", defaults.get("has_wall"));
        assertNull(defaults.get("has_districts"));
    }

    @Test
    void testDefaultParameters_City() {
        var defaults = StructureType.CITY.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("true", defaults.get("has_wall"));
        assertEquals("true", defaults.get("has_districts"));
    }

    @Test
    void testGetStructureClass() {
        assertEquals(Village.class, StructureType.HAMLET.getStructureClass());
        assertEquals(Village.class, StructureType.SMALL_VILLAGE.getStructureClass());
        assertEquals(Village.class, StructureType.VILLAGE.getStructureClass());
        assertEquals(Village.class, StructureType.LARGE_VILLAGE.getStructureClass());
        assertEquals(Town.class, StructureType.TOWN.getStructureClass());
        assertEquals(Town.class, StructureType.LARGE_TOWN.getStructureClass());
        assertEquals(Town.class, StructureType.CITY.getStructureClass());
    }

    @Test
    void testFromString() {
        assertEquals(StructureType.HAMLET, StructureType.fromString("hamlet"));
        assertEquals(StructureType.HAMLET, StructureType.fromString("HAMLET"));
        assertEquals(StructureType.VILLAGE, StructureType.fromString("village"));
        assertEquals(StructureType.TOWN, StructureType.fromString("town"));
        assertNull(StructureType.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> StructureType.fromString("invalid"));
    }

    @Test
    void testVillageConfiguresOwnGrids() {
        Village village = (Village) StructureType.HAMLET.createInstance();
        village.setName("test-hamlet");

        List<HexVector2> coordinates = Arrays.asList(hex(0, 0));
        village.configureHexGrids(coordinates);

        assertEquals(1, village.getHexGrids().size());

        FeatureHexGrid grid = village.getHexGrids().get(0);
        assertEquals(0, grid.getCoordinate().getQ());
        assertEquals(0, grid.getCoordinate().getR());
        assertEquals("hamlet", grid.getParameters().get("structure"));
        assertEquals("test-hamlet", grid.getParameters().get("structureName"));
        assertEquals("island", grid.getParameters().get("g_builder"));
    }

    @Test
    void testTownConfiguresOwnGrids() {
        Town town = (Town) StructureType.TOWN.createInstance();
        town.setName("test-town");

        List<HexVector2> coordinates = Arrays.asList(
            hex(0, 0),
            hex(1, 0),
            hex(0, 1),
            hex(1, 1),
            hex(-1, 0)  // 5-cross pattern
        );
        town.configureHexGrids(coordinates);

        assertEquals(5, town.getHexGrids().size());

        FeatureHexGrid grid = town.getHexGrids().get(0);
        assertEquals("town", grid.getParameters().get("structure"));
        assertEquals("test-town", grid.getParameters().get("structureName"));
        assertEquals("false", grid.getParameters().get("has_wall"));
    }

    @Test
    void testCityWithWall() {
        Town city = (Town) StructureType.CITY.createInstance();
        city.setName("metropolis");

        List<HexVector2> coordinates = Arrays.asList(hex(0, 0));
        city.configureHexGrids(coordinates);

        assertEquals(1, city.getHexGrids().size());

        FeatureHexGrid grid = city.getHexGrids().get(0);
        assertEquals("city", grid.getParameters().get("structure"));
        assertEquals("true", grid.getParameters().get("has_wall"));
    }

    @Test
    void testVillageCanOverrideDefaults() {
        Village village = (Village) StructureType.VILLAGE.createInstance();
        village.setName("custom-village");

        // Override default level
        village.getParameters().put("default_level", "100");

        List<HexVector2> coordinates = Arrays.asList(hex(0, 0));
        village.configureHexGrids(coordinates);

        FeatureHexGrid grid = village.getHexGrids().get(0);
        assertEquals("100", grid.getParameters().get("default_level"));
    }

    @Test
    void testStructureWithoutType() {
        Village village = new Village();
        village.setName("legacy-village");

        // No type set - should handle gracefully
        village.applyDefaults();  // Should not crash

        List<HexVector2> coordinates = Arrays.asList(hex(0, 0));
        village.configureHexGrids(coordinates);

        assertEquals(1, village.getHexGrids().size());
        FeatureHexGrid grid = village.getHexGrids().get(0);
        assertEquals("g_village", grid.getParameters().get("structure"));  // Falls back to class name
    }

    @Test
    void testDifferentStructureTypesSameClass() {
        // Small village
        Village smallVillage = (Village) StructureType.SMALL_VILLAGE.createInstance();
        smallVillage.setName("small");
        smallVillage.configureHexGrids(Arrays.asList(hex(0, 0)));

        // Large village - same class, different defaults
        Village largeVillage = (Village) StructureType.LARGE_VILLAGE.createInstance();
        largeVillage.setName("large");
        largeVillage.configureHexGrids(Arrays.asList(hex(0, 0)));

        // Both are Villages
        assertEquals(Village.class, smallVillage.getClass());
        assertEquals(Village.class, largeVillage.getClass());

        // But have different types
        assertEquals(StructureType.SMALL_VILLAGE, smallVillage.getType());
        assertEquals(StructureType.LARGE_VILLAGE, largeVillage.getType());

        // And different structure parameters
        assertEquals("small_village", smallVillage.getHexGrids().get(0).getParameters().get("structure"));
        assertEquals("large_village", largeVillage.getHexGrids().get(0).getParameters().get("structure"));
    }
}
