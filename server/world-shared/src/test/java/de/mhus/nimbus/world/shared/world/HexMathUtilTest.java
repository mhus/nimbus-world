package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
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

        // Create mock WWorld
        WWorld world = mock(WWorld.class);
        WorldInfo publicData = mock(WorldInfo.class);
        when(publicData.getChunkSize()).thenReturn(chunkSize);
        when(publicData.getHexGridSize()).thenReturn(hexGridSize);
        when(world.getPublicData()).thenReturn(publicData);

        // Calculate chunk world bounds
        int chunkMinX = cx * chunkSize; // 32
        int chunkMinZ = cz * chunkSize; // 352
        int chunkMaxX = (cx + 1) * chunkSize; // 64
        int chunkMaxZ = (cz + 1) * chunkSize; // 384

        System.out.println("Chunk bounds: X=[" + chunkMinX + ", " + chunkMaxX + "), Z=[" + chunkMinZ + ", " + chunkMaxZ + ")");

        // Get all hexes overlapping the chunk
        HexVector2[] hexes = HexMathUtil.getHexesForChunk(hexGridSize, chunkSize, cx, cz);
        System.out.println("Overlapping hexes: " + hexes.length);
        for (HexVector2 hex : hexes) {
            System.out.println("  Hex: q=" + hex.getQ() + ", r=" + hex.getR());
            double[] hexCenter = HexMathUtil.hexToCartesian(hex, hexGridSize);
            System.out.println("    Center: x=" + hexCenter[0] + ", z=" + hexCenter[1]);
        }

        // When: Get dominant hex for this chunk
        HexVector2 result = HexMathUtil.getDominantHexForChunk(world, cx, cz);

        // Then: Print result
        System.out.println("Dominant hex: q=" + result.getQ() + ", r=" + result.getR());
        double[] dominantCenter = HexMathUtil.hexToCartesian(result, hexGridSize);
        System.out.println("Dominant hex center: x=" + dominantCenter[0] + ", z=" + dominantCenter[1]);

        // Calculate chunk center
        double chunkCenterX = (chunkMinX + chunkMaxX) / 2.0;
        double chunkCenterZ = (chunkMinZ + chunkMaxZ) / 2.0;
        System.out.println("Chunk center: x=" + chunkCenterX + ", z=" + chunkCenterZ);

        // Calculate distances from chunk center to each hex center
        System.out.println("\nDistances from chunk center to hex centers:");
        for (HexVector2 hex : hexes) {
            double[] hexCenter = HexMathUtil.hexToCartesian(hex, hexGridSize);
            double distance = Math.sqrt(
                    Math.pow(chunkCenterX - hexCenter[0], 2) +
                    Math.pow(chunkCenterZ - hexCenter[1], 2)
            );
            System.out.println("  Hex q=" + hex.getQ() + ", r=" + hex.getR() + ": distance=" + distance);
        }

        // The result should NOT be q=0, r=0 according to the user
        // Let's verify what the actual dominant hex should be
        assertThat(result).isNotNull();

        // Verify the result
        System.out.println("\n=== VERIFICATION ===");
        if (result.getQ() == 0 && result.getR() == 0) {
            System.out.println("FAILURE: Result is q=0, r=0 which is INCORRECT");
            assertThat(result).as("Hex coordinate should not be (0,0) for chunk (1,11)").isNotEqualTo(
                    HexVector2.builder().q(0).r(0).build()
            );
        } else {
            System.out.println("SUCCESS: Result is q=" + result.getQ() + ", r=" + result.getR() + " which is CORRECT");
            // After Z-axis fix (hexToCartesian negates Z): hex (1,-1) has center at z=+300, near chunk z=352-384
            assertThat(result.getQ()).isEqualTo(1);
            assertThat(result.getR()).isEqualTo(-1);
        }
    }

    @Test
    void testGetDominantHexForChunk_multipleScenarios() {
        // Test multiple chunk positions to ensure the fix works correctly
        int chunkSize = 32;
        int hexGridSize = 400;

        System.out.println("\n=== Testing multiple chunk positions ===");

        int[][] testCases = {
                // After Z-axis fix (hexToCartesian negates Z), hex centers have moved
                {1, 11, 1, -1}, // cx=1, cz=11 → worldZ~368, hex (1,-1) center at z=+300
                {0, 0, 0, 0},   // cx=0, cz=0 → worldZ~0, hex (0,0) center at z=0
                {5, 5, 1, -1},  // cx=5, cz=5 → worldZ~176, hex (1,-1) center at z=+300
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

            System.out.println(String.format("Chunk (%d,%d) -> Hex (%d,%d) [expected: (%d,%d)]",
                    cx, cz, result.getQ(), result.getR(), expectedQ, expectedR));

            assertThat(result.getQ()).as("Q coordinate for chunk (" + cx + "," + cz + ")").isEqualTo(expectedQ);
            assertThat(result.getR()).as("R coordinate for chunk (" + cx + "," + cz + ")").isEqualTo(expectedR);
        }
    }

    @Test
    void testGetHexesForChunk_analysisOfProblem() {
        // This test analyzes the problem with getHexesForChunk
        int chunkSize = 32;
        int hexGridSize = 400;
        int cx = 1;
        int cz = 11;

        System.out.println("=== Analysis of getHexesForChunk ===");
        System.out.println("chunkSize: " + chunkSize);
        System.out.println("hexGridSize: " + hexGridSize);
        System.out.println("chunk: cx=" + cx + ", cz=" + cz);

        // Chunk corners in world coordinates
        int[][] corners = {
                {cx * chunkSize, cz * chunkSize},                           // top-left
                {(cx + 1) * chunkSize - 1, cz * chunkSize},                 // top-right
                {cx * chunkSize, (cz + 1) * chunkSize - 1},                 // bottom-left
                {(cx + 1) * chunkSize - 1, (cz + 1) * chunkSize - 1}        // bottom-right
        };

        System.out.println("\nChunk corners and their hex assignments (using simple division):");
        for (int i = 0; i < corners.length; i++) {
            int worldX = corners[i][0];
            int worldZ = corners[i][1];
            int q = worldX / hexGridSize;
            int r = worldZ / hexGridSize;
            System.out.println("  Corner " + i + ": (" + worldX + ", " + worldZ + ") -> hex q=" + q + ", r=" + r);
        }

        System.out.println("\nPROBLEM: All corners map to the same hex using simple division!");
        System.out.println("This is because hexGridSize (" + hexGridSize + ") is much larger than chunk size (" + chunkSize + ")");

        // Now check using flatToHex which uses proper hex coordinate conversion
        System.out.println("\nUsing flatToHex (proper axial coordinate conversion):");
        for (int i = 0; i < corners.length; i++) {
            int worldX = corners[i][0];
            int worldZ = corners[i][1];
            HexVector2 hex = HexMathUtil.flatToHex(
                    de.mhus.nimbus.generated.types.Vector2Int.builder().x(worldX).z(worldZ).build(),
                    hexGridSize
            );
            System.out.println("  Corner " + i + ": (" + worldX + ", " + worldZ + ") -> hex q=" + hex.getQ() + ", r=" + hex.getR());
            double[] hexCenter = HexMathUtil.hexToCartesian(hex, hexGridSize);
            System.out.println("    Hex center: (" + hexCenter[0] + ", " + hexCenter[1] + ")");
        }
    }

    @Test
    void testIsPointInHex_coverageOfThreeHexagons() {
        // Test that three adjacent hexagons (0;1, 1;1, 0;2) completely cover
        // their intersection area without gaps
        int gridSize = 400;

        // Define the three hexagons
        HexVector2[] hexagons = {
                HexVector2.builder().q(0).r(1).build(),
                HexVector2.builder().q(1).r(1).build(),
                HexVector2.builder().q(0).r(2).build()
        };

        System.out.println("=== Testing coverage of three hexagons ===");
        System.out.println("Hexagons: (0,1), (1,1), (0,2)");
        System.out.println("GridSize: " + gridSize);

        // Calculate centers
        double[][] centers = new double[3][];
        for (int i = 0; i < hexagons.length; i++) {
            centers[i] = HexMathUtil.hexToCartesian(hexagons[i], gridSize);
            System.out.println("Hex (" + hexagons[i].getQ() + "," + hexagons[i].getR() + ") center: (" + centers[i][0] + ", " + centers[i][1] + ")");
        }

        // Define test area as the intersection region between the three hexagons
        // We focus on the area where hexagons meet/overlap
        double radius = gridSize / 2.0;

        // Test area: centered around the meeting point of the three hexagons
        // Calculate the average position of the three centers as the focus point
        double focusX = (centers[0][0] + centers[1][0] + centers[2][0]) / 3.0;
        double focusZ = (centers[0][1] + centers[1][1] + centers[2][1]) / 3.0;

        // Define test area with some margin around the focus point
        // This area should be fully covered by the three hexagons without gaps
        double testMargin = radius * 0.8; // Test within 80% of hex radius around focus
        double minX = focusX - testMargin;
        double maxX = focusX + testMargin;
        double minZ = focusZ - testMargin;
        double maxZ = focusZ + testMargin;

        System.out.println("\nFocus point: (" + focusX + ", " + focusZ + ")");
        System.out.println("Test area: X=[" + minX + ", " + maxX + "], Z=[" + minZ + ", " + maxZ + "]");

        // Create a grid to track coverage
        // Use step size 1 for precise coverage testing
        int gridWidth = (int) Math.ceil((maxX - minX));
        int gridHeight = (int) Math.ceil((maxZ - minZ));

        System.out.println("Grid dimensions: " + gridWidth + " x " + gridHeight + " = " + (gridWidth * gridHeight) + " points");

        int[][] coverageGrid = new int[gridWidth][gridHeight];
        int totalPoints = 0;
        int coveredPoints = 0;
        int uncoveredPoints = 0;

        // Test each point in the grid
        for (int ix = 0; ix < gridWidth; ix++) {
            for (int iz = 0; iz < gridHeight; iz++) {
                double x = minX + ix;
                double z = minZ + iz;

                boolean covered = false;
                // Check if point is in any of the three hexagons
                for (int h = 0; h < hexagons.length; h++) {
                    if (HexMathUtil.isPointInHex(x, z, centers[h][0], centers[h][1], gridSize)) {
                        coverageGrid[ix][iz] = h + 1; // Mark which hex covers this point (1, 2, or 3)
                        covered = true;
                        break;
                    }
                }

                totalPoints++;
                if (covered) {
                    coveredPoints++;
                } else {
                    uncoveredPoints++;
                }
            }
        }

        System.out.println("\nCoverage results:");
        System.out.println("  Total points: " + totalPoints);
        System.out.println("  Covered points: " + coveredPoints);
        System.out.println("  Uncovered points: " + uncoveredPoints);
        System.out.println("  Coverage: " + (100.0 * coveredPoints / totalPoints) + "%");

        // Find and print some uncovered points for debugging
        if (uncoveredPoints > 0) {
            System.out.println("\nSample of uncovered points (first 10):");
            int printedCount = 0;
            for (int ix = 0; ix < gridWidth && printedCount < 10; ix++) {
                for (int iz = 0; iz < gridHeight && printedCount < 10; iz++) {
                    if (coverageGrid[ix][iz] == 0) {
                        double x = minX + ix;
                        double z = minZ + iz;
                        System.out.println("  Uncovered point: (" + x + ", " + z + ")");

                        // Debug: show distance to each hex center
                        for (int h = 0; h < hexagons.length; h++) {
                            double dist = Math.sqrt(Math.pow(x - centers[h][0], 2) + Math.pow(z - centers[h][1], 2));
                            System.out.println("    Distance to hex " + h + ": " + dist + " (radius: " + radius + ")");
                        }
                        printedCount++;
                    }
                }
            }
        }

        // Assert that all points in the intersection area are covered
        assertThat(uncoveredPoints)
                .as("All points in the intersection area should be covered by at least one hexagon")
                .isEqualTo(0);
    }

    @Test
    void testIsPointInHex_specificPoint() {
        // Test a specific point that was not drawn in the real grid
        int gridSize = 400;
        double x = -503.0;
        double z = 680.0;

        System.out.println("=== Testing specific point ===");
        System.out.println("Point: (" + x + ", " + z + ")");
        System.out.println("GridSize: " + gridSize);

        // Find which hex this point should belong to
        HexVector2 expectedHex = HexMathUtil.flatToHex(
                de.mhus.nimbus.generated.types.Vector2Int.builder()
                        .x((int) x)
                        .z((int) z)
                        .build(),
                gridSize
        );

        System.out.println("\nExpected hex coordinate: q=" + expectedHex.getQ() + ", r=" + expectedHex.getR());

        // Get the center of this hex
        double[] hexCenter = HexMathUtil.hexToCartesian(expectedHex, gridSize);
        System.out.println("Hex center: (" + hexCenter[0] + ", " + hexCenter[1] + ")");

        // Calculate distance from point to hex center
        double distance = Math.sqrt(Math.pow(x - hexCenter[0], 2) + Math.pow(z - hexCenter[1], 2));
        double radius = gridSize / 2.0;
        System.out.println("\nDistance from point to hex center: " + distance);
        System.out.println("Hex radius (circumradius): " + radius);
        System.out.println("Hex inradius (apothem): " + (radius * Math.sqrt(3.0) / 2.0));

        // Test if the point is in the hex
        boolean isInHex = HexMathUtil.isPointInHex(x, z, hexCenter[0], hexCenter[1], gridSize);
        System.out.println("\nisPointInHex result: " + isInHex);

        // Test neighboring hexes as well
        System.out.println("\nTesting neighboring hexes:");
        de.mhus.nimbus.world.shared.world.WHexGrid.EDGE[] edges = de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values();
        for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge : edges) {
            HexVector2 neighbor = HexMathUtil.getNeighborPosition(expectedHex, edge);
            double[] neighborCenter = HexMathUtil.hexToCartesian(neighbor, gridSize);
            boolean isInNeighbor = HexMathUtil.isPointInHex(x, z, neighborCenter[0], neighborCenter[1], gridSize);
            double neighborDistance = Math.sqrt(Math.pow(x - neighborCenter[0], 2) + Math.pow(z - neighborCenter[1], 2));

            System.out.println("  " + edge + " (q=" + neighbor.getQ() + ", r=" + neighbor.getR() + "): " +
                    "isInHex=" + isInNeighbor + ", distance=" + neighborDistance);
        }

        // Debug: Calculate hex-relative coordinates and check boundaries
        System.out.println("\n=== Debug: Hex-relative coordinates ===");
        double dx = x - hexCenter[0];
        double dz = z - hexCenter[1];
        System.out.println("dx (x - hexCenterX): " + dx);
        System.out.println("dz (z - hexCenterZ): " + dz);

        double halfWidth = radius * Math.sqrt(3.0) / 2.0;
        System.out.println("\nHalf width (inradius): " + halfWidth);
        System.out.println("Is within vertical bounds (|dx| <= halfWidth): " + (Math.abs(dx) <= halfWidth));
        System.out.println("|dx| = " + Math.abs(dx));

        double absDz = Math.abs(dz);
        double absDx = Math.abs(dx);
        double diagonalLimit = radius - absDx / Math.sqrt(3.0);
        System.out.println("\nDiagonal constraint:");
        System.out.println("|dz| = " + absDz);
        System.out.println("limit (radius - |dx|/sqrt(3)): " + diagonalLimit);
        System.out.println("Is within diagonal bounds (|dz| <= limit): " + (absDz <= diagonalLimit));

        // The point should be in its expected hex or one of the neighbors
        assertThat(isInHex)
                .as("Point (" + x + ", " + z + ") should be in hex (" + expectedHex.getQ() + ", " + expectedHex.getR() + ")")
                .isTrue();
    }
}
