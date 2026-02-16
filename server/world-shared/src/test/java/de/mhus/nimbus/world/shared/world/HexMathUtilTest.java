package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HexMathUtilTest {

    @Test
    void testGetDominantHexForChunk_withChunkSize32HexSize400() {
        // Given: chunkSize=32, hexGridSize=400, chunk at cx=1, cz=11
        int chunkSize = 32;
        int hexGridSize = 400;
        int cx = 1;
        int cz = 11;

        WWorld world = mock(WWorld.class);
        WorldInfo publicData = mock(WorldInfo.class);
        when(publicData.getChunkSize()).thenReturn(chunkSize);
        when(publicData.getHexGridSize()).thenReturn(hexGridSize);
        when(world.getPublicData()).thenReturn(publicData);

        // Chunk world bounds: X=[32, 64), Z=[352, 384)
        // Hex (0,1) center at (173, 300) - closest hex for this chunk area
        HexVector2 result = HexMathUtil.getDominantHexForChunk(world, cx, cz);

        assertThat(result).isNotNull();
        assertThat(result.getQ()).isEqualTo(0);
        assertThat(result.getR()).isEqualTo(1);
    }

    @Test
    void testGetDominantHexForChunk_multipleScenarios() {
        int chunkSize = 32;
        int hexGridSize = 400;

        // hexToCartesian centers for reference (gridWidth=346):
        // (0,0) -> (0, 0)
        // (0,1) -> (173, 300)
        // (1,0) -> (346, 0)
        int[][] testCases = {
                {1, 11, 0, 1},  // cx=1, cz=11 -> world center (48, 368), nearest hex (0,1) at (173, 300)
                {0, 0, 0, 0},   // cx=0, cz=0 -> world center (16, 16), nearest hex (0,0) at (0, 0)
                {5, 5, 0, 1},   // cx=5, cz=5 -> world center (176, 176), inside hex (0,1) at (173, 300)
                {10, 0, 1, 0},  // cx=10, cz=0 -> world center (336, 16), nearest hex (1,0) at (346, 0)
        };

        for (int[] testCase : testCases) {
            int cx = testCase[0];
            int cz = testCase[1];
            int expectedQ = testCase[2];
            int expectedR = testCase[3];

            WWorld world = mock(WWorld.class);
            WorldInfo publicData = mock(WorldInfo.class);
            when(publicData.getChunkSize()).thenReturn(chunkSize);
            when(publicData.getHexGridSize()).thenReturn(hexGridSize);
            when(world.getPublicData()).thenReturn(publicData);

            HexVector2 result = HexMathUtil.getDominantHexForChunk(world, cx, cz);

            assertThat(result.getQ()).as("Q coordinate for chunk (" + cx + "," + cz + ")").isEqualTo(expectedQ);
            assertThat(result.getR()).as("R coordinate for chunk (" + cx + "," + cz + ")").isEqualTo(expectedR);
        }
    }

    @Test
    void testGetHexesForChunk_analysisOfProblem() {
        int chunkSize = 32;
        int hexGridSize = 400;
        int cx = 1;
        int cz = 11;

        // Chunk corners in world coordinates
        int[][] corners = {
                {cx * chunkSize, cz * chunkSize},
                {(cx + 1) * chunkSize - 1, cz * chunkSize},
                {cx * chunkSize, (cz + 1) * chunkSize - 1},
                {(cx + 1) * chunkSize - 1, (cz + 1) * chunkSize - 1}
        };

        // All corners should map to the same hex using flatToHex
        HexVector2 firstHex = null;
        for (int[] corner : corners) {
            HexVector2 hex = HexMathUtil.flatToHex(
                    Vector2Int.builder().x(corner[0]).z(corner[1]).build(),
                    hexGridSize
            );
            if (firstHex == null) {
                firstHex = hex;
            }
            // All corners of this small chunk should be in the same hex
            assertThat(hex.getQ()).as("Corner (%d,%d) q", corner[0], corner[1]).isEqualTo(firstHex.getQ());
            assertThat(hex.getR()).as("Corner (%d,%d) r", corner[0], corner[1]).isEqualTo(firstHex.getR());
        }
    }

    @Test
    void testIsPointInHex_coverageOfThreeHexagons() {
        // Test that three adjacent hexagons sharing a vertex completely cover
        // the area around their shared vertex without gaps.
        // Hexes (0,0), (1,0), (0,1) share a vertex at (halfWidth, quarterHeight) = (173, 100)
        int gridSize = 400;

        HexVector2[] hexagons = {
                HexVector2.builder().q(0).r(0).build(),
                HexVector2.builder().q(1).r(0).build(),
                HexVector2.builder().q(0).r(1).build()
        };

        double[][] centers = new double[3][];
        for (int i = 0; i < hexagons.length; i++) {
            centers[i] = HexMathUtil.hexToCartesian(hexagons[i], gridSize);
        }

        // Focus on the shared vertex area (173, 100)
        double focusX = (centers[0][0] + centers[1][0] + centers[2][0]) / 3.0;
        double focusZ = (centers[0][1] + centers[1][1] + centers[2][1]) / 3.0;

        // Use a tight test margin around the shared vertex
        double testMargin = 80;
        double minX = focusX - testMargin;
        double maxX = focusX + testMargin;
        double minZ = focusZ - testMargin;
        double maxZ = focusZ + testMargin;

        int gridWidth = (int) Math.ceil((maxX - minX));
        int gridHeight = (int) Math.ceil((maxZ - minZ));

        int uncoveredPoints = 0;

        for (int ix = 0; ix < gridWidth; ix++) {
            for (int iz = 0; iz < gridHeight; iz++) {
                double x = minX + ix;
                double z = minZ + iz;

                boolean covered = false;
                for (int h = 0; h < hexagons.length; h++) {
                    if (HexMathUtil.isPointInHex(x, z, centers[h][0], centers[h][1], gridSize)) {
                        covered = true;
                        break;
                    }
                }

                if (!covered) {
                    uncoveredPoints++;
                }
            }
        }

        assertThat(uncoveredPoints)
                .as("All points around shared vertex should be covered by at least one hexagon")
                .isEqualTo(0);
    }

    @Test
    void testIsPointInHex_specificPoint() {
        // Test a specific point and verify flatToHex + isPointInHex consistency
        int gridSize = 400;
        double x = -503.0;
        double z = 680.0;

        HexVector2 expectedHex = HexMathUtil.flatToHex(
                Vector2Int.builder().x((int) x).z((int) z).build(),
                gridSize
        );

        double[] hexCenter = HexMathUtil.hexToCartesian(expectedHex, gridSize);
        boolean isInHex = HexMathUtil.isPointInHex(x, z, hexCenter[0], hexCenter[1], gridSize);

        // flatToHex must return a hex that contains the point
        assertThat(isInHex)
                .as("Point (" + x + ", " + z + ") should be in hex (" + expectedHex.getQ() + ", " + expectedHex.getR() + ")")
                .isTrue();
    }

    @Test
    void testHexToCartesianAndBack_largeAndNegativeCoordinates() {
        int[] testGridSizes = {2, 10, 400, 1024};
        // Keep values within int range to avoid overflow in hexToCartesian (int arithmetic)
        int[] testQs = {0, 1, -1, 100, -100, 1000, -1000, 100000, -100000};
        int[] testRs = {0, 1, -1, 100, -100, 1000, -1000, 100000, -100000};

        for (int gridSize : testGridSizes) {
            for (int q : testQs) {
                for (int r : testRs) {
                    // Skip values that would overflow int in hexToCartesian
                    int gridWidth = HexMathUtil.getGridWidth(gridSize);
                    long xLong = (long) q * gridWidth + (r % 2 != 0 ? gridWidth / 2 : 0);
                    long zLong = ((long) r * 3 * gridSize) / 4;
                    if (xLong > Integer.MAX_VALUE || xLong < Integer.MIN_VALUE ||
                        zLong > Integer.MAX_VALUE || zLong < Integer.MIN_VALUE) {
                        continue; // Skip overflow cases
                    }

                    HexVector2 hex = HexVector2.builder().q(q).r(r).build();
                    double[] cart = HexMathUtil.hexToCartesian(hex, gridSize);
                    Vector2Int pos = Vector2Int.builder()
                            .x((int) Math.round(cart[0]))
                            .z((int) Math.round(cart[1]))
                            .build();
                    HexVector2 back = HexMathUtil.flatToHex(pos, gridSize);

                    assertThat(back.getQ())
                        .as("q roundtrip for q=" + q + ", r=" + r + ", gridSize=" + gridSize)
                        .isEqualTo(q);
                    assertThat(back.getR())
                        .as("r roundtrip for q=" + q + ", r=" + r + ", gridSize=" + gridSize)
                        .isEqualTo(r);
                }
            }
        }
    }
}
