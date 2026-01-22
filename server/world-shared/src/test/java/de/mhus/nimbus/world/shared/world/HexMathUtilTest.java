package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.WorldInfo;
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
            // The correct hex should be q=0, r=1
            assertThat(result.getQ()).isEqualTo(0);
            assertThat(result.getR()).isEqualTo(1);
        }
    }

    @Test
    void testGetDominantHexForChunk_multipleScenarios() {
        // Test multiple chunk positions to ensure the fix works correctly
        int chunkSize = 32;
        int hexGridSize = 400;

        System.out.println("\n=== Testing multiple chunk positions ===");

        int[][] testCases = {
                {1, 11, 0, 1},  // cx=1, cz=11 should be hex (0, 1)
                {0, 0, 0, 0},   // cx=0, cz=0 should be hex (0, 0)
                {5, 5, 0, 1},   // cx=5, cz=5 should be hex (0, 1)
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
}
