package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for hex grid mount coordinate calculation.
 * Validates that mount coordinates (top-left corner of flat bounding box) are correctly
 * calculated for different hex grid positions.
 *
 * Geometry:
 * - Hex radius: 200
 * - Grid size (diameter): 400
 * - Hex width: 346 (radius * sqrt(3))
 * - Hex height: 400 (radius * 2)
 * - Flat size with 15px border: SizeX=377, SizeZ=430
 */
class HexGridMountCoordinatesTest {

    private static final int GRID_SIZE = 400;
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final int SIZE_X = (int) Math.round(GRID_SIZE * SQRT_3 / 2.0) + 30;  // 346 + 30 = 376
    private static final int SIZE_Z = GRID_SIZE + 30;  // 430

    /**
     * Calculate mount position using the corrected formula from FlatCreateService.
     */
    private int[] calculateMountPosition(int q, int r) {
        HexVector2 hexVec = HexVector2.builder().q(q).r(r).build();
        int[] center = HexMathUtil.hexToCartesian(hexVec, GRID_SIZE);
        int centerX = center[0];
        int centerZ = center[1];

        // Corrected formula: no -10 offset
        int mountX = centerX - SIZE_X / 2;
        int mountZ = centerZ - SIZE_Z / 2;

        return new int[]{mountX, mountZ};
    }

    @Test
    void testGrid_0_0_MountCoordinates() {
        // Grid 0;0 should be centered at world origin (0, 0)
        int[] mount = calculateMountPosition(0, 0);

        System.out.printf("Grid 0;0: centerX=0, centerZ=0, mount=(%d, %d), sizeX=%d%n",
                mount[0], mount[1], SIZE_X);

        // Expected: mount = (0 - 376/2, 0 - 430/2) = (-188, -215)
        assertEquals(-188, mount[0], "Grid 0;0 mountX should be -188");
        assertEquals(-215, mount[1], "Grid 0;0 mountZ should be -215");
    }

    @Test
    void testGrid_1_0_MountCoordinates() {
        // Grid 1;0 should be one hex to the right
        int[] mount = calculateMountPosition(1, 0);

        // Offset coordinates: x = 1*346 = 346, z = 0
        // Expected mount: (346 - 188, 0 - 215) = (158, -215)
        System.out.printf("Grid 1;0: centerX=346, centerZ=0, mount=(%d, %d)%n",
                mount[0], mount[1]);

        assertEquals(158, mount[0], "Grid 1;0 mountX should be 158");
        assertEquals(-215, mount[1], "Grid 1;0 mountZ should be -215");
    }

    @Test
    void testGrid_0_1_MountCoordinates() {
        // Grid 0;1 should be one hex down and to the right (pointy-top hex, odd-r stagger)
        int[] mount = calculateMountPosition(0, 1);

        // Offset coordinates: x = 0*346 + 173 (odd row offset) = 173, z = 1*300 = 300
        // Expected mount: (173 - 188, 300 - 215) = (-15, 85)
        System.out.printf("Grid 0;1: centerX=173, centerZ=300, mount=(%d, %d)%n",
                mount[0], mount[1]);

        assertEquals(-15, mount[0], "Grid 0;1 mountX should be -15");
        assertEquals(85, mount[1], "Grid 0;1 mountZ should be 85");
    }

    @Test
    void testGrid_1_1_MountCoordinates() {
        // Grid 1;1
        int[] mount = calculateMountPosition(1, 1);

        // Offset coordinates: x = 1*346 + 173 (odd row offset) = 519, z = 1*300 = 300
        // Expected mount: (519 - 188, 300 - 215) = (331, 85)
        System.out.printf("Grid 1;1: centerX=519, centerZ=300, mount=(%d, %d)%n",
                mount[0], mount[1]);

        assertEquals(331, mount[0], "Grid 1;1 mountX should be 331");
        assertEquals(85, mount[1], "Grid 1;1 mountZ should be 85");
    }

    @Test
    void testGrid_Minus1_0_MountCoordinates() {
        // Grid -1;0 should be one hex to the left
        int[] mount = calculateMountPosition(-1, 0);

        // Offset coordinates: x = -1*346 = -346, z = 0
        // Expected mount: (-346 - 188, 0 - 215) = (-534, -215)
        System.out.printf("Grid -1;0: centerX=-346, centerZ=0, mount=(%d, %d)%n",
                mount[0], mount[1]);

        assertEquals(-534, mount[0], "Grid -1;0 mountX should be -534");
        assertEquals(-215, mount[1], "Grid -1;0 mountZ should be -215");
    }

    @Test
    void testGrid_0_Minus1_MountCoordinates() {
        // Grid 0;-1 should be one hex up (odd-r stagger, odd row gets +halfWidth offset)
        int[] mount = calculateMountPosition(0, -1);

        // Offset coordinates: x = 0*346 + 173 (odd row offset) = 173, z = -1*300 = -300
        // Expected mount: (173 - 188, -300 - 215) = (-15, -515)
        System.out.printf("Grid 0;-1: centerX=173, centerZ=-300, mount=(%d, %d)%n",
                mount[0], mount[1]);

        assertEquals(-15, mount[0], "Grid 0;-1 mountX should be -15");
        assertEquals(-515, mount[1], "Grid 0;-1 mountZ should be -515");
    }

    @Test
    void testAdjacentGrids_NoGap() {
        // Verify that adjacent grids have exactly 15 pixels overlap on each side
        // (30 total: 15px border on each side of the hexagon)

        // Test horizontal neighbors: Grid 0;0 and Grid 1;0
        int[] mount_0_0 = calculateMountPosition(0, 0);
        int[] mount_1_0 = calculateMountPosition(1, 0);

        // Grid 0;0 right edge: mountX + sizeX = -188 + 376 = 188
        // Grid 1;0 left edge: mountX = 158
        // Distance between edges: 158 - 188 = -30 (negative means overlap)
        int horizontalOverlap = mount_1_0[0] - (mount_0_0[0] + SIZE_X);

        System.out.printf("Horizontal overlap between 0;0 and 1;0: %d pixels (expected: -30)%n",
                horizontalOverlap);

        assertEquals(-30, horizontalOverlap,
                "Horizontal grids should have 30 pixels overlap (15px border on each side)");
    }

    @Test
    void testFlatBoundingBox() {
        // Verify that flat bounding box dimensions are correct
        System.out.printf("Flat bounding box: SizeX=%d, SizeZ=%d%n", SIZE_X, SIZE_Z);

        // Expected: 376 x 430 (hex width 346 + 30 border, hex height 400 + 30 border)
        assertEquals(376, SIZE_X, "SizeX should be 376");
        assertEquals(430, SIZE_Z, "SizeZ should be 430");
    }

    @Test
    void testHexToCartesianFormula() {
        // Verify the hex-to-cartesian conversion formula
        HexVector2 hex = HexVector2.builder().q(1).r(1).build();
        int[] center = HexMathUtil.hexToCartesian(hex, GRID_SIZE);

        // Expected: x = q * gridWidth + halfWidth (odd row offset) = 1*346 + 173 = 519
        //           z = r * 3 * gridSize / 4 = 1 * 3 * 400 / 4 = 300
        int expectedX = HexMathUtil.getGridWidth(GRID_SIZE) + HexMathUtil.getGridWidth(GRID_SIZE) / 2;
        int expectedZ = 3 * GRID_SIZE / 4;

        System.out.printf("Hex 1;1 cartesian: (%d, %d), expected: (%d, %d)%n",
                center[0], center[1], expectedX, expectedZ);

        assertEquals(expectedX, center[0], "Hex-to-cartesian X should match formula");
        assertEquals(expectedZ, center[1], "Hex-to-cartesian Z should match formula");
    }
}
