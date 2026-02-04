package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for HexLocalUtil hex-to-cartesian coordinate conversion.
 * Verifies that hexagonal coordinates are correctly converted to cartesian coordinates.
 */
class HexLocalUtilTest {

    private static final int HEX_GRID_SIZE = 512;

    @Test
    void testCenterPosition_Divider3() {
        // Center position <0;0> with divider 3
        HexVector2 hexPos = TypeUtil.hexVector2(0, 0);
        int divider = 3;
        int slotSize = HEX_GRID_SIZE / divider; // 170

        HexLocalPosition localPos = new HexLocalPosition(hexPos, divider, slotSize);
        Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);

        // Center should be at (0, 0) relative to grid center
        System.out.printf("Center <0;0> divider %d: relative (%d, %d), absolute (%d, %d)%n",
            divider, cartesian.getX(), cartesian.getZ(),
            HEX_GRID_SIZE / 2 + cartesian.getX(),
            HEX_GRID_SIZE / 2 + cartesian.getZ());

        assertEquals(0, cartesian.getX(), 5, "Center X should be near 0");
        assertEquals(0, cartesian.getZ(), 5, "Center Z should be near 0");
    }

    @Test
    void testRing1Positions_Divider3() {
        // Test all 6 neighbors in ring 1 with divider 3
        int divider = 3;
        int slotSize = HEX_GRID_SIZE / divider;

        // Ring 1 positions: <0;1>, <1;0>, <1;-1>, <0;-1>, <-1;0>, <-1;1>
        HexVector2[] ring1 = {
            TypeUtil.hexVector2(0, 1),   // SE
            TypeUtil.hexVector2(1, 0),   // E
            TypeUtil.hexVector2(1, -1),  // NE
            TypeUtil.hexVector2(0, -1),  // NW
            TypeUtil.hexVector2(-1, 0),  // W
            TypeUtil.hexVector2(-1, 1)   // SW
        };

        String[] directions = {"SE", "E", "NE", "NW", "W", "SW"};

        for (int i = 0; i < ring1.length; i++) {
            HexLocalPosition localPos = new HexLocalPosition(ring1[i], divider, slotSize);
            Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);

            System.out.printf("Ring1 %s <%d;%d> divider %d: relative (%d, %d), absolute (%d, %d)%n",
                directions[i], ring1[i].getQ(), ring1[i].getR(), divider,
                cartesian.getX(), cartesian.getZ(),
                HEX_GRID_SIZE / 2 + cartesian.getX(),
                HEX_GRID_SIZE / 2 + cartesian.getZ());

            // Ring 1 neighbors should be at approximately slotSize distance from center
            double distance = Math.sqrt(cartesian.getX() * cartesian.getX() +
                                       cartesian.getZ() * cartesian.getZ());

            // Expected distance is roughly slotSize (with some tolerance for hex geometry)
            assertTrue(distance > slotSize * 0.5 && distance < slotSize * 1.5,
                String.format("Distance for %s should be roughly %d, but was %.1f",
                    directions[i], slotSize, distance));
        }
    }

    @Test
    void testCenterPosition_Divider5() {
        // Center position <0;0> with divider 5
        HexVector2 hexPos = TypeUtil.hexVector2(0, 0);
        int divider = 5;
        int slotSize = HEX_GRID_SIZE / divider; // 102

        HexLocalPosition localPos = new HexLocalPosition(hexPos, divider, slotSize);
        Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);

        System.out.printf("Center <0;0> divider %d: relative (%d, %d), absolute (%d, %d)%n",
            divider, cartesian.getX(), cartesian.getZ(),
            HEX_GRID_SIZE / 2 + cartesian.getX(),
            HEX_GRID_SIZE / 2 + cartesian.getZ());

        assertEquals(0, cartesian.getX(), 5, "Center X should be near 0");
        assertEquals(0, cartesian.getZ(), 5, "Center Z should be near 0");
    }

    @Test
    void testRing2Position_Divider5() {
        // Test a ring 2 position with divider 5
        int divider = 5;
        int slotSize = HEX_GRID_SIZE / divider;

        // Ring 2 position: <0;2>
        HexVector2 hexPos = TypeUtil.hexVector2(0, 2);
        HexLocalPosition localPos = new HexLocalPosition(hexPos, divider, slotSize);
        Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);

        System.out.printf("Ring2 <0;2> divider %d: relative (%d, %d), absolute (%d, %d)%n",
            divider, cartesian.getX(), cartesian.getZ(),
            HEX_GRID_SIZE / 2 + cartesian.getX(),
            HEX_GRID_SIZE / 2 + cartesian.getZ());

        // Ring 2 should be roughly 2 * slotSize distance from center
        double distance = Math.sqrt(cartesian.getX() * cartesian.getX() +
                                   cartesian.getZ() * cartesian.getZ());

        assertTrue(distance > slotSize * 1.5 && distance < slotSize * 2.5,
            String.format("Distance for ring 2 should be roughly %d, but was %.1f",
                slotSize * 2, distance));
    }

    @Test
    void testAllDividers_CenterPosition() {
        // Test that center is always at (0,0) regardless of divider
        int[] dividers = {1, 3, 5, 7};

        System.out.println("\n=== Testing center position for all dividers ===");
        for (int divider : dividers) {
            int slotSize = HEX_GRID_SIZE / divider;
            HexVector2 hexPos = TypeUtil.hexVector2(0, 0);
            HexLocalPosition localPos = new HexLocalPosition(hexPos, divider, slotSize);
            Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);

            System.out.printf("Divider %d (slotSize %d): center at relative (%d, %d)%n",
                divider, slotSize, cartesian.getX(), cartesian.getZ());

            assertEquals(0, cartesian.getX(), 5,
                String.format("Center X for divider %d should be near 0", divider));
            assertEquals(0, cartesian.getZ(), 5,
                String.format("Center Z for divider %d should be near 0", divider));
        }
    }

    @Test
    void testHexNeighborDistances_Divider3() {
        // Verify that hex neighbors are equidistant from center
        int divider = 3;
        int slotSize = HEX_GRID_SIZE / divider;

        HexVector2[] ring1 = {
            TypeUtil.hexVector2(0, 1),
            TypeUtil.hexVector2(1, 0),
            TypeUtil.hexVector2(1, -1),
            TypeUtil.hexVector2(0, -1),
            TypeUtil.hexVector2(-1, 0),
            TypeUtil.hexVector2(-1, 1)
        };

        System.out.println("\n=== Testing hex neighbor distances (divider 3) ===");
        double[] distances = new double[6];
        for (int i = 0; i < ring1.length; i++) {
            HexLocalPosition localPos = new HexLocalPosition(ring1[i], divider, slotSize);
            Vector2Int cartesian = HexLocalUtil.toHexGridLocalCenter(localPos);
            distances[i] = Math.sqrt(cartesian.getX() * cartesian.getX() +
                                    cartesian.getZ() * cartesian.getZ());
            System.out.printf("Neighbor <%d;%d>: distance %.1f%n",
                ring1[i].getQ(), ring1[i].getR(), distances[i]);
        }

        // All neighbors should have roughly the same distance from center
        double avgDistance = 0;
        for (double d : distances) avgDistance += d;
        avgDistance /= distances.length;

        for (int i = 0; i < distances.length; i++) {
            assertEquals(avgDistance, distances[i], avgDistance * 0.1,
                String.format("Neighbor %d distance should be close to average %.1f",
                    i, avgDistance));
        }
    }
}
