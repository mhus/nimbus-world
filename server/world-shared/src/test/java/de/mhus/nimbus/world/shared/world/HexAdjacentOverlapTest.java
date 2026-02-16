package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests whether adjacent hex grids overlap when placed next to each other.
 * Verifies that isPointInHex claims boundary pixels for both neighbors,
 * and checks the mount-point / flat-size geometry for overlap.
 *
 * hexToCartesian uses offset coordinates (odd-r stagger), NOT axial coordinates.
 * Geometric neighbors for even-row hex (0,0):
 *   EAST→(1,0), WEST→(-1,0), NE→(0,1), NW→(-1,1), SE→(0,-1), SW→(-1,-1)
 */
class HexAdjacentOverlapTest {

    private static final int[] GRID_SIZES = {100, 200, 400};
    private static final int BORDER = 15;

    /**
     * For each grid size, verify that the hex interiors of two EAST/WEST neighbors
     * share exactly one column of pixels (the shared edge at x = halfWidth).
     */
    @Test
    void testEastWestNeighbors_sharedEdgePixels() {
        for (int gridSize : GRID_SIZES) {
            int gridWidth = HexMathUtil.getGridWidth(gridSize);

            HexVector2 hex00 = HexVector2.builder().q(0).r(0).build();
            HexVector2 hex10 = HexVector2.builder().q(1).r(0).build();

            int[] center00 = HexMathUtil.hexToCartesian(hex00, gridSize);
            int[] center10 = HexMathUtil.hexToCartesian(hex10, gridSize);

            // Centers should be gridWidth apart on x-axis
            assertThat(center10[0] - center00[0])
                    .as("EAST neighbor center distance X for gridSize=%d", gridSize)
                    .isEqualTo(gridWidth);

            // Check the shared edge: x = center00.x + halfWidth
            int halfWidth = gridWidth / 2;
            double sharedX = center00[0] + halfWidth;

            int overlapCount = 0;
            int scanRange = gridSize;
            for (int dz = -scanRange; dz <= scanRange; dz++) {
                double testZ = center00[1] + dz;
                boolean in00 = HexMathUtil.isPointInHex(sharedX, testZ, center00[0], center00[1], gridSize);
                boolean in10 = HexMathUtil.isPointInHex(sharedX, testZ, center10[0], center10[1], gridSize);
                if (in00 && in10) overlapCount++;
            }

            System.out.printf("gridSize=%d, gridWidth=%d: sharedX=%.0f, overlap=%d pixels%n",
                    gridSize, gridWidth, sharedX, overlapCount);

            // Half-open boundary: no pixel is claimed by both hexes
            assertThat(overlapCount)
                    .as("Shared edge overlap count for gridSize=%d", gridSize)
                    .isEqualTo(0);
        }
    }

    /**
     * Verify getNeighborPosition returns geometrically adjacent hexes for even and odd rows.
     * Each neighbor center must be at distance ≈ gridWidth from the source hex center.
     */
    @Test
    void testGetNeighborPosition_evenAndOddRows() {
        int gridSize = 400;
        int gridWidth = HexMathUtil.getGridWidth(gridSize);

        // Test even row: hex (0,0) and hex (2,0)
        HexVector2[] evenRowHexes = {
                HexVector2.builder().q(0).r(0).build(),
                HexVector2.builder().q(2).r(0).build(),
                HexVector2.builder().q(-1).r(2).build(),
        };
        // Test odd row: hex (0,1) and hex (1,-1)
        HexVector2[] oddRowHexes = {
                HexVector2.builder().q(0).r(1).build(),
                HexVector2.builder().q(1).r(-1).build(),
                HexVector2.builder().q(0).r(3).build(),
        };

        for (HexVector2 hex : evenRowHexes) {
            int[] center = HexMathUtil.hexToCartesian(hex, gridSize);
            assertThat(hex.getR() % 2).as("Should be even row").isEqualTo(0);

            for (WHexGrid.EDGE direction : WHexGrid.EDGE.values()) {
                HexVector2 neighbor = HexMathUtil.getNeighborPosition(hex, direction);
                int[] neighborCenter = HexMathUtil.hexToCartesian(neighbor, gridSize);
                double dist = Math.sqrt(
                        Math.pow(neighborCenter[0] - center[0], 2) +
                        Math.pow(neighborCenter[1] - center[1], 2));

                assertThat(dist)
                        .as("Even row hex(%d,%d) %s neighbor(%d,%d) distance should be ≈ gridWidth=%d",
                                hex.getQ(), hex.getR(), direction, neighbor.getQ(), neighbor.getR(), gridWidth)
                        .isBetween((double) gridWidth - 1, (double) gridWidth + 1);
            }
        }

        for (HexVector2 hex : oddRowHexes) {
            int[] center = HexMathUtil.hexToCartesian(hex, gridSize);
            assertThat(Math.abs(hex.getR()) % 2).as("Should be odd row").isEqualTo(1);

            for (WHexGrid.EDGE direction : WHexGrid.EDGE.values()) {
                HexVector2 neighbor = HexMathUtil.getNeighborPosition(hex, direction);
                int[] neighborCenter = HexMathUtil.hexToCartesian(neighbor, gridSize);
                double dist = Math.sqrt(
                        Math.pow(neighborCenter[0] - center[0], 2) +
                        Math.pow(neighborCenter[1] - center[1], 2));

                assertThat(dist)
                        .as("Odd row hex(%d,%d) %s neighbor(%d,%d) distance should be ≈ gridWidth=%d",
                                hex.getQ(), hex.getR(), direction, neighbor.getQ(), neighbor.getR(), gridWidth)
                        .isBetween((double) gridWidth - 1, (double) gridWidth + 1);
            }
        }
    }

    /**
     * Verify getNeighborPosition is the inverse of itself via opposite direction.
     * neighbor(hex, dir).neighbor(opposite(dir)) == hex
     */
    @Test
    void testGetNeighborPosition_roundtrip() {
        int gridSize = 400;
        HexVector2[] testHexes = {
                HexVector2.builder().q(0).r(0).build(),
                HexVector2.builder().q(1).r(1).build(),
                HexVector2.builder().q(-2).r(3).build(),
                HexVector2.builder().q(5).r(-4).build(),
        };

        for (HexVector2 hex : testHexes) {
            for (WHexGrid.EDGE direction : WHexGrid.EDGE.values()) {
                HexVector2 neighbor = HexMathUtil.getNeighborPosition(hex, direction);
                HexVector2 back = HexMathUtil.getNeighborPosition(neighbor, direction.getOpposite());

                assertThat(back.getQ())
                        .as("Roundtrip Q for hex(%d,%d) direction %s", hex.getQ(), hex.getR(), direction)
                        .isEqualTo(hex.getQ());
                assertThat(back.getR())
                        .as("Roundtrip R for hex(%d,%d) direction %s", hex.getQ(), hex.getR(), direction)
                        .isEqualTo(hex.getR());
            }
        }
    }

    /**
     * For all 6 neighbor directions, verify that adjacent hexes share boundary pixels
     * on their common edge. Uses getNeighborPosition (now fixed for offset coordinates).
     */
    @Test
    void testAllDirections_sharedEdgeOverlap() {
        int gridSize = 400;
        int gridWidth = HexMathUtil.getGridWidth(gridSize);
        HexVector2 centerHex = HexVector2.builder().q(0).r(0).build();
        int[] centerPos = HexMathUtil.hexToCartesian(centerHex, gridSize);

        for (WHexGrid.EDGE direction : WHexGrid.EDGE.values()) {
            HexVector2 neighborHex = HexMathUtil.getNeighborPosition(centerHex, direction);
            int[] neighborPos = HexMathUtil.hexToCartesian(neighborHex, gridSize);

            // Verify geometrically adjacent
            double dist = Math.sqrt(
                    Math.pow(neighborPos[0] - centerPos[0], 2) +
                    Math.pow(neighborPos[1] - centerPos[1], 2));
            assertThat(dist)
                    .as("Neighbor distance for %s should be ≈ gridWidth=%d", direction, gridWidth)
                    .isBetween((double) gridWidth - 1, (double) gridWidth + 1);

            // Get the shared edge midpoint
            int[][] corners = HexMathUtil.getCornersForSide(direction, gridSize);
            double edgeMidX = centerPos[0] + (corners[0][0] + corners[1][0]) / 2.0;
            double edgeMidZ = centerPos[1] + (corners[0][1] + corners[1][1]) / 2.0;

            boolean inCenter = HexMathUtil.isPointInHex(edgeMidX, edgeMidZ, centerPos[0], centerPos[1], gridSize);
            boolean inNeighbor = HexMathUtil.isPointInHex(edgeMidX, edgeMidZ, neighborPos[0], neighborPos[1], gridSize);

            System.out.printf("Direction %s: neighbor=(%d,%d) at (%d,%d), edgeMid=(%.1f,%.1f), inCenter=%b, inNeighbor=%b%n",
                    direction, neighborHex.getQ(), neighborHex.getR(), neighborPos[0], neighborPos[1],
                    edgeMidX, edgeMidZ, inCenter, inNeighbor);

            // Half-open boundary: edge midpoint belongs to exactly one hex
            assertThat(inCenter || inNeighbor)
                    .as("Edge midpoint must belong to at least one hex for direction %s", direction)
                    .isTrue();
            assertThat(inCenter && inNeighbor)
                    .as("Edge midpoint must not belong to both hexes for direction %s", direction)
                    .isFalse();
        }
    }

    /**
     * Verify flat bounding boxes of E/W adjacent hexes overlap by exactly 2*border pixels.
     */
    @Test
    void testFlatBoundingBoxOverlap_eastWest() {
        for (int gridSize : GRID_SIZES) {
            int gridWidth = HexMathUtil.getGridWidth(gridSize);
            int sizeX = gridWidth + BORDER * 2;

            HexVector2 hex00 = HexVector2.builder().q(0).r(0).build();
            HexVector2 hex10 = HexVector2.builder().q(1).r(0).build();

            int[] center00 = HexMathUtil.hexToCartesian(hex00, gridSize);
            int[] center10 = HexMathUtil.hexToCartesian(hex10, gridSize);

            int mount00X = (int) Math.floor(center00[0] - sizeX / 2.0);
            int mount10X = (int) Math.floor(center10[0] - sizeX / 2.0);

            int flatOverlap = (mount00X + sizeX) - mount10X;

            System.out.printf("gridSize=%d: flat00=[%d, %d), flat10=[%d, %d), overlap=%d (expected %d)%n",
                    gridSize, mount00X, mount00X + sizeX, mount10X, mount10X + sizeX,
                    flatOverlap, BORDER * 2);

            assertThat(flatOverlap)
                    .as("Flat overlap for gridSize=%d should be 2*border=%d", gridSize, BORDER * 2)
                    .isEqualTo(BORDER * 2);
        }
    }

    /**
     * Count how many world-coordinate columns are claimed by BOTH adjacent hex interiors.
     * Scans at the center Z row (where E/W edges are widest).
     */
    @Test
    void testHexInteriorOverlap_columnCount_eastWest() {
        for (int gridSize : GRID_SIZES) {
            int gridWidth = HexMathUtil.getGridWidth(gridSize);

            HexVector2 hex00 = HexVector2.builder().q(0).r(0).build();
            HexVector2 hex10 = HexVector2.builder().q(1).r(0).build();

            int[] center00 = HexMathUtil.hexToCartesian(hex00, gridSize);
            int[] center10 = HexMathUtil.hexToCartesian(hex10, gridSize);

            double testZ = center00[1];
            int overlapColumns = 0;

            for (int x = (int) center00[0]; x <= (int) center10[0]; x++) {
                boolean in00 = HexMathUtil.isPointInHex(x, testZ, center00[0], center00[1], gridSize);
                boolean in10 = HexMathUtil.isPointInHex(x, testZ, center10[0], center10[1], gridSize);
                if (in00 && in10) {
                    overlapColumns++;
                }
            }

            System.out.printf("gridSize=%d, gridWidth=%d: hex interior E/W overlap at center Z = %d columns%n",
                    gridSize, gridWidth, overlapColumns);

            // Half-open boundary: no column is shared at the E/W edge
            assertThat(overlapColumns)
                    .as("Hex interior overlap columns for gridSize=%d", gridSize)
                    .isEqualTo(0);
        }
    }

    /**
     * For all 6 directions, scan the shared edge and count how many pixels
     * are claimed by both hexes. This quantifies the overlap per direction.
     */
    @Test
    void testAllDirections_overlapPixelCount() {
        int gridSize = 400;
        HexVector2 centerHex = HexVector2.builder().q(0).r(0).build();
        int[] centerPos = HexMathUtil.hexToCartesian(centerHex, gridSize);

        for (WHexGrid.EDGE direction : WHexGrid.EDGE.values()) {
            HexVector2 neighborHex = HexMathUtil.getNeighborPosition(centerHex, direction);
            int[] neighborPos = HexMathUtil.hexToCartesian(neighborHex, gridSize);

            // Get edge corners in world coordinates
            int[][] corners = HexMathUtil.getCornersForSide(direction, gridSize);
            int wx1 = (int) centerPos[0] + corners[0][0];
            int wz1 = (int) centerPos[1] + corners[0][1];
            int wx2 = (int) centerPos[0] + corners[1][0];
            int wz2 = (int) centerPos[1] + corners[1][1];

            // Walk along the edge and count overlapping pixels
            int dx = wx2 - wx1;
            int dz = wz2 - wz1;
            int steps = (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz));

            int overlapCount = 0;
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double testX = wx1 + t * dx;
                double testZ = wz1 + t * dz;

                boolean inCenter = HexMathUtil.isPointInHex(testX, testZ, centerPos[0], centerPos[1], gridSize);
                boolean inNeighbor = HexMathUtil.isPointInHex(testX, testZ, neighborPos[0], neighborPos[1], gridSize);

                if (inCenter && inNeighbor) {
                    overlapCount++;
                }
            }

            System.out.printf("Direction %s: edge length=%d, overlap pixels=%d%n",
                    direction, steps, overlapCount);

            // Half-open boundary: edges have no overlap (max 1-2 for vertex pixels)
            assertThat(overlapCount)
                    .as("Edge overlap pixels for direction %s", direction)
                    .isLessThanOrEqualTo(2);
        }
    }

    /**
     * Verify that a hex occupies exactly gridWidth pixels horizontally
     * at its center row (z == centerZ), confirming the half-open boundary
     * produces the expected pixel extent.
     */
    @Test
    void testHexPixelExtent() {
        for (int gridSize : GRID_SIZES) {
            int gridWidth = HexMathUtil.getGridWidth(gridSize);

            HexVector2 hex = HexVector2.builder().q(0).r(0).build();
            int[] center = HexMathUtil.hexToCartesian(hex, gridSize);

            // Count pixels at center row
            int halfWidth = gridWidth / 2;
            int pixelCount = 0;
            for (int x = center[0] - halfWidth - 1; x <= center[0] + halfWidth + 1; x++) {
                if (HexMathUtil.isPointInHex(x, center[1], center[0], center[1], gridSize)) {
                    pixelCount++;
                }
            }

            System.out.printf("gridSize=%d, gridWidth=%d: hex pixel extent at center row = %d%n",
                    gridSize, gridWidth, pixelCount);

            // Half-open boundary: hex occupies exactly gridWidth pixels at widest row
            assertThat(pixelCount)
                    .as("Hex pixel extent at center row for gridSize=%d should be gridWidth=%d", gridSize, gridWidth)
                    .isEqualTo(gridWidth);
        }
    }
}
