package de.mhus.nimbus.world.generator.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.structure.StructureType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructureType Rich Enum pattern
 */
class StructureTypeTest {

    private HexVector2 hex(int q, int r) {
        return HexVector2.builder().q(q).r(r).build();
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

}
