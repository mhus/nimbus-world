package de.mhus.nimbus.world.shared.util;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector2Pair;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.experimental.UtilityClass;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Utility class for hexagonal grid mathematics.
 * Uses pointy-top hexagon orientation (hexagon point facing up).
 * Coordinates are axial (q, r) with cube coordinates s = -q - r.
 *
 * Hex Position Key Format: "q;r"
 *
 */
@UtilityClass
public class HexMathUtil {

    public static final double SQRT_3 = Math.sqrt(3.0);

    /**
     * Converts hex coordinates to cartesian world coordinates (center position).
     * Uses pointy-top hexagon orientation.
     *
     * Z-axis is negated so that hex NORTH (r-) maps to world North (z+)
     * and hex SOUTH (r+) maps to world South (z-). This ensures consistent
     * orientation between the hex grid and the 3D world.
     *
     * @param hex The hex vector with q and r coordinates
     * @param gridHeight The diameter of the hexagon in blocks
     * @return Array with [x, z] center coordinates in world space
     */
    public static int[] hexToCartesian(HexVector2 hex, int gridHeight) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        if (gridHeight <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }
        if (gridHeight % 2 != 0) {
            throw new IllegalArgumentException("Grid height must be even for proper hex dimensions");
        }

        int gridWidth = getGridWidth(gridHeight);
        int x = hex.getQ() * gridWidth;
        if (hex.getR() % 2 != 0) {
            x += gridWidth / 2; // Offset for odd rows
        }
        int z =  (hex.getR() * 3 * gridHeight) / 4;
        return new int[]{x, z};
    }

    public static int getGridWidth(int gridHeight) {
        int radius = gridHeight / 2;
        int width = (int)Math.floor(radius * SQRT_3);
        if (width % 2 != 0) {
            width++; // Ensure width is even for proper staggering
        }
        return width;
    }

    public static int getGridRadius(int gridHeight) {
        return gridHeight / 2;
    }

    /**
     * Tests if a cartesian point is inside a pointy-top hexagon.
     * Uses integer-based dimensions from {@link #getGridWidth(int)} to avoid
     * floating-point rounding errors and stay consistent with {@link #hexToCartesian}.
     *
     * For a pointy-top hexagon with gridHeight H and gridWidth W = getGridWidth(H):
     * - Height (vertical span) = H, vertices at (0, ±H/2)
     * - Width (horizontal span) = W, flat edges at ±W/2
     * - Upper-right vertex at (W/2, H/4)
     *
     * Two constraints (using absolute values due to 6-fold symmetry):
     * 1. |dx| <= W/2 (left/right flat edges)
     * 2. H * |dx| + 2W * |dz| <= W * H (4 diagonal edges)
     *
     * @param x The x coordinate of the point to test
     * @param z The z coordinate of the point to test
     * @param hexCenterX The x coordinate of the hex center
     * @param hexCenterZ The z coordinate of the hex center
     * @param gridSize The diameter (height) of the hexagon in blocks
     * @return true if the point is inside the hexagon
     */
    public static boolean isPointInHex(double x, double z, double hexCenterX, double hexCenterZ, int gridSize) {
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        int gridWidth = getGridWidth(gridSize);
        int halfWidth = gridWidth / 2;

        double dx = x - hexCenterX;
        double dz = z - hexCenterZ;
        double adx = Math.abs(dx);
        double adz = Math.abs(dz);

        // Strictly outside
        if (adx > halfWidth) return false;
        double lhs = gridSize * adx + 2.0 * gridWidth * adz;
        double rhs = (double) gridWidth * gridSize;
        if (lhs > rhs) return false;

        // Strictly inside (neither constraint at equality)
        if (adx < halfWidth && lhs < rhs) return true;

        // On boundary: half-open rule (like rectangles [left, right))
        // Exclude points on "positive" side → they belong to the neighbor
        if (dx > 0) return false;
        if (dx == 0 && dz >= 0) return false;
        return true;  // dx < 0, or (dx == 0 && dz < 0) → belongs to this hex
    }

    /**
     * Creates an iterator that lazily generates flat positions within a hexagon.
     * This is memory-efficient for large grid sizes as it doesn't allocate a full set.
     *
     * @param hex The hex vector with q and r coordinates
     * @param gridSize The diameter of the hexagon in blocks
     * @return Iterator over FlatPosition objects within the hexagon
     */
    public static Iterator<Vector2Int> createFlatPositionIterator(HexVector2 hex, int gridSize) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        return new HexPositionIterator(hex, gridSize);
    }

    /**
     * Returns the neighbor hex position in offset coordinates (odd-r stagger).
     * The neighbor offsets depend on whether the current row is even or odd,
     * matching the stagger layout used by {@link #hexToCartesian}.
     *
     * Even row (r%2==0): odd neighbor rows are staggered RIGHT by halfWidth.
     * Odd row (r%2!=0): even neighbor rows have NO stagger.
     */
    public static HexVector2 getNeighborPosition(HexVector2 position, WHexGrid.EDGE nabor) {
        int q = position.getQ();
        int r = position.getR();
        boolean evenRow = (r % 2 == 0);

        switch (nabor) {
            case EAST:
                return HexVector2.builder().q(q + 1).r(r).build();
            case WEST:
                return HexVector2.builder().q(q - 1).r(r).build();
            case NORTH_EAST:
                return evenRow
                        ? HexVector2.builder().q(q).r(r + 1).build()
                        : HexVector2.builder().q(q + 1).r(r + 1).build();
            case NORTH_WEST:
                return evenRow
                        ? HexVector2.builder().q(q - 1).r(r + 1).build()
                        : HexVector2.builder().q(q).r(r + 1).build();
            case SOUTH_EAST:
                return evenRow
                        ? HexVector2.builder().q(q).r(r - 1).build()
                        : HexVector2.builder().q(q + 1).r(r - 1).build();
            case SOUTH_WEST:
                return evenRow
                        ? HexVector2.builder().q(q - 1).r(r - 1).build()
                        : HexVector2.builder().q(q).r(r - 1).build();
            default:
                throw new IllegalArgumentException("Unknown nabor direction: " + nabor);
        }
    }

    /**
     * Returns the two corner positions for a pointy-top hex edge, relative to hex center.
     * Uses integer-based dimensions from {@link #getGridWidth(int)}.
     *
     * Corner order follows the spec (North-to-South direction per side):
     * NE: N→NE, E: NE→SE, SE: SE→S, SW: SW→S, W: NW→SW, NW: N→NW
     *
     * @param side The hex edge
     * @param hexGridSize The hex diameter (gridHeight)
     * @return int[2][2] where [0] is start corner {x, z} and [1] is end corner {x, z}, relative to center
     */
    public static int[][] getCornersForSide(WHexGrid.EDGE side, int hexGridSize) {
        int gridWidth = getGridWidth(hexGridSize);
        int halfWidth = gridWidth / 2;
        int halfHeight = hexGridSize / 2;
        int quarterHeight = hexGridSize / 4;

        // Pointy-top corners (relative to center):
        // N=(0, halfHeight), NE=(halfWidth, quarterHeight), SE=(halfWidth, -quarterHeight)
        // S=(0, -halfHeight), SW=(-halfWidth, -quarterHeight), NW=(-halfWidth, quarterHeight)
        switch (side) {
            case NORTH_EAST:
                return new int[][]{{0, halfHeight}, {halfWidth, quarterHeight}};
            case EAST:
                return new int[][]{{halfWidth, quarterHeight}, {halfWidth, -quarterHeight}};
            case SOUTH_EAST:
                return new int[][]{{halfWidth, -quarterHeight}, {0, -halfHeight}};
            case SOUTH_WEST:
                return new int[][]{{-halfWidth, -quarterHeight}, {0, -halfHeight}};
            case WEST:
                return new int[][]{{-halfWidth, quarterHeight}, {-halfWidth, -quarterHeight}};
            case NORTH_WEST:
                return new int[][]{{0, halfHeight}, {-halfWidth, quarterHeight}};
            default:
                throw new IllegalArgumentException("Unknown edge: " + side);
        }
    }

    /**
     * Converts world coordinates to hex axial coordinates (q, r).
     * Exact inverse of {@link #hexToCartesian} using the same integer-based
     * {@link #getGridWidth(int)} dimensions. Determines the correct hex by
     * testing candidates with {@link #isPointInHex} rather than floating-point
     * cube-coordinate rounding, ensuring voxel-exact results.
     *
     * @param flatPos The world position (x, z)
     * @param hexGridSize The diameter of the hexagon in blocks (size, not radius)
     * @return HexVector2 with q and r coordinates
     */
    public static HexVector2 flatToHex(Vector2Int flatPos, int hexGridSize) {
        int x = flatPos.getX();
        int z = flatPos.getZ();
        int gridWidth = getGridWidth(hexGridSize);
        int halfWidth = gridWidth / 2;

        // Row height from hexToCartesian: z = r * 3 * hexGridSize / 4
        double rowHeight = 3.0 * hexGridSize / 4.0;

        // Estimate row from z coordinate
        int r0 = (int) Math.floor(z / rowHeight);

        // Test 4 candidates (2 rows x 2 columns) using hexToCartesian + isPointInHex
        HexVector2 best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int r = r0; r <= r0 + 1; r++) {
            // Account for row stagger: hexToCartesian offsets odd rows by halfWidth
            int xOffset = (r % 2 != 0) ? halfWidth : 0;
            int q0 = (int) Math.floor((double) (x - xOffset) / gridWidth);

            for (int q = q0; q <= q0 + 1; q++) {
                HexVector2 candidate = HexVector2.builder().q(q).r(r).build();
                int[] center = hexToCartesian(candidate, hexGridSize);
                if (isPointInHex(x, z, center[0], center[1], hexGridSize)) {
                    return candidate;
                }
                // Track nearest center as fallback for boundary points
                int dx = x - center[0];
                int dz = z - center[1];
                int dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }

        return best;
    }


    /**
     * Internal iterator implementation for lazy position generation.
     */
    private static class HexPositionIterator implements Iterator<Vector2Int> {
        private final int hexCenterX;
        private final int hexCenterZ;
        private final int gridSize;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private int currentX;
        private int currentZ;
        private Vector2Int nextPosition;
        private boolean hasSearchedNext;

        HexPositionIterator(HexVector2 hex, int gridSize) {
            int[] center = hexToCartesian(hex, gridSize);
            this.hexCenterX = center[0];
            this.hexCenterZ = center[1];
            this.gridSize = gridSize;

            // Calculate bounding box
            // gridSize is diameter, so radius = gridSize / 2
            int radius = gridSize / 2;
            this.minX = hexCenterX - radius;
            this.maxX = hexCenterX + radius;
            this.minZ = hexCenterZ - radius;
            this.maxZ = hexCenterZ + radius;

            // Start iteration
            this.currentX = minX;
            this.currentZ = minZ;
            this.hasSearchedNext = false;
        }

        @Override
        public boolean hasNext() {
            if (!hasSearchedNext) {
                searchNext();
            }
            return nextPosition != null;
        }

        @Override
        public Vector2Int next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more positions in hexagon");
            }

            Vector2Int result = nextPosition;
            hasSearchedNext = false;
            nextPosition = null;
            return result;
        }

        private void searchNext() {
            hasSearchedNext = true;

            while (currentZ <= maxZ) {
                while (currentX <= maxX) {
                    if (isPointInHex(currentX, currentZ, hexCenterX, hexCenterZ, gridSize)) {
                        nextPosition = TypeUtil.vector2int(currentX, currentZ);
                        currentX++;
                        return;
                    }
                    currentX++;
                }

                // Move to next row
                currentZ++;
                currentX = minX;
            }

            // No more positions found
            nextPosition = null;
        }
    }

    public static HexVector2[] getHexesForChunk(WWorld world, int cx, int cz) {
        int hexSize = world.getPublicData().getHexGridSize();
        int chunkSize = world.getPublicData().getChunkSize();
        return getHexesForChunk(hexSize, chunkSize, cx, cz);
    }

    public static HexVector2[] getHexesForChunk(int hexSize, int chunkSize, int cx, int cz) {
        // Weltkoordinaten der vier Ecken des Chunks
        int[][] ecken = new int[][]{
                {cx * chunkSize, cz * chunkSize}, // oben links
                {(cx + 1) * chunkSize - 1, cz * chunkSize}, // oben rechts
                {cx * chunkSize, (cz + 1) * chunkSize - 1}, // unten links
                {(cx + 1) * chunkSize - 1, (cz + 1) * chunkSize - 1} // unten rechts
        };
        java.util.Set<String> uniqueHexes = new java.util.HashSet<>();
        java.util.List<HexVector2> result = new java.util.ArrayList<>();
        for (int[] ecke : ecken) {
            // Use flatToHex for proper axial coordinate conversion
            HexVector2 hex = flatToHex(
                    de.mhus.nimbus.generated.types.Vector2Int.builder()
                            .x(ecke[0])
                            .z(ecke[1])
                            .build(),
                    hexSize
            );
            String key = hex.getQ() + ";" + hex.getR();
            if (!uniqueHexes.contains(key)) {
                uniqueHexes.add(key);
                result.add(hex);
                if (result.size() == 3) break; // maximal 3 Hexfelder
            }
        }
        return result.toArray(new HexVector2[result.size()]);
    }

    public static HexVector2[] getHexesForArea(int hexSize, Area area) {
        int worldX1 = area.getPosition().getX();
        int worldZ1 = area.getPosition().getZ();
        int worldX2 = worldX1 + area.getSize().getX();
        int worldZ2 = worldZ1 + area.getSize().getZ();
        // Weltkoordinaten der vier Ecken der Area
        int[][] ecken = new int[][]{
                {worldX1, worldZ1}, // oben links
                {worldX2 - 1, worldZ1}, // oben rechts
                {worldX1, worldZ2 - 1}, // unten links
                {worldX2 - 1, worldZ2 - 1} // unten rechts
        };
        java.util.Set<String> uniqueHexes = new java.util.HashSet<>();
        java.util.List<HexVector2> result = new java.util.ArrayList<>();
        for (int[] ecke : ecken) {
            // Use flatToHex for proper axial coordinate conversion
            HexVector2 hex = flatToHex(
                    de.mhus.nimbus.generated.types.Vector2Int.builder()
                            .x(ecke[0])
                            .z(ecke[1])
                            .build(),
                    hexSize
            );
            String key = hex.getQ() + ";" + hex.getR();
            if (!uniqueHexes.contains(key)) {
                uniqueHexes.add(key);
                result.add(hex);
                if (result.size() == 3) break; // maximal 3 Hexfelder
            }
        }
        return result.toArray(new HexVector2[result.size()]);
    }

    /**
     * Determines the hex grid coordinate (HexVector2) that has the largest overlapping area with the given chunk.
     * This is done by checking all hexes overlapping the chunk and selecting the one with the largest intersection area.
     *
     * @param world The world instance
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return The HexVector2 of the hex with the largest overlap
     */
    public static HexVector2 getDominantHexForChunk(WWorld world, int cx, int cz) {
        int hexSize = world.getPublicData().getHexGridSize();
        int chunkSize = world.getPublicData().getChunkSize();
        // Get all hexes overlapping the chunk
        HexVector2[] hexes = getHexesForChunk(hexSize, chunkSize, cx, cz);
        // Rectangle (chunk) bounds
        double minX = cx * chunkSize;
        double minZ = cz * chunkSize;
        double maxX = (cx + 1) * chunkSize-1;
        double maxZ = (cz + 1) * chunkSize-1;
        // For each hex, estimate overlap area by sampling points in the chunk
        int sampleStep = Math.max(1, chunkSize / 8); // sample grid granularity
        HexVector2 bestHex = hexes[0];
        int maxCount = -1;
        for (HexVector2 hex : hexes) {
            int[] hexCenter = hexToCartesian(hex, hexSize);
            int count = 0;
            for (double x = minX; x < maxX; x += sampleStep) {
                for (double z = minZ; z < maxZ; z += sampleStep) {
                    if (isPointInHex(x, z, hexCenter[0], hexCenter[1], hexSize)) {
                        count++;
                    }
                }
            }
            if (count > maxCount) {
                maxCount = count;
                bestHex = hex;
            }
        }
        return bestHex;
    }

    /**
     * Efficiently calculates all chunk keys where the given hex has the dominant (largest) overlap.
     * This is much more efficient than checking isDominantChunkForHexGrid for each chunk individually.
     *
     * @param hexCoord The hex coordinate to find dominant chunks for
     * @param chunkSize The size of chunks in blocks
     * @param gridSize The diameter of the hexagon in blocks
     * @return Set of chunk keys (format: "cx:cz") where this hex is dominant
     */
    public static java.util.Set<String> getDominantChunkKeysForHex(HexVector2 hexCoord, int chunkSize, int gridSize) {
        if (hexCoord == null) {
            throw new IllegalArgumentException("hexCoord cannot be null");
        }
        if (chunkSize <= 0 || gridSize <= 0) {
            throw new IllegalArgumentException("chunkSize and gridSize must be positive");
        }

        java.util.Set<String> dominantChunks = new java.util.HashSet<>();

        // Get the hex center in world coordinates
        int[] hexCenter = hexToCartesian(hexCoord, gridSize);
        int hexCenterX = hexCenter[0];
        int hexCenterZ = hexCenter[1];

        // Calculate bounding box for the hex (conservative estimate)
        // gridSize is diameter, so radius = gridSize / 2
        int radius = gridSize / 2;
        int minX = hexCenterX - radius;
        int maxX = hexCenterX + radius;
        int minZ = hexCenterZ - radius;
        int maxZ = hexCenterZ + radius;

        // Calculate chunk range that could overlap with this hex
        int minCx = Math.floorDiv(minX, chunkSize);
        int maxCx = Math.floorDiv(maxX, chunkSize);
        int minCz = Math.floorDiv(minZ, chunkSize);
        int maxCz = Math.floorDiv(maxZ, chunkSize);

        // For each potentially overlapping chunk
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                // Get all hexes that overlap with this chunk
                HexVector2[] overlappingHexes = getHexesForChunk(gridSize, chunkSize, cx, cz);

                if (overlappingHexes.length == 0) {
                    continue;
                }

                // If only one hex overlaps, it's automatically dominant
                if (overlappingHexes.length == 1) {
                    HexVector2 onlyHex = overlappingHexes[0];
                    if (onlyHex.getQ() == hexCoord.getQ() && onlyHex.getR() == hexCoord.getR()) {
                        dominantChunks.add(cx + ":" + cz);
                    }
                    continue;
                }

                // Multiple hexes overlap - need to find which has largest overlap
                double chunkMinX = cx * chunkSize;
                double chunkMinZ = cz * chunkSize;
                double chunkMaxX = (cx + 1) * chunkSize;
                double chunkMaxZ = (cz + 1) * chunkSize;

                int sampleStep = Math.max(1, chunkSize / 8);
                int maxOverlap = -1;
                HexVector2 dominantHex = null;

                // Calculate overlap for each hex
                for (HexVector2 candidateHex : overlappingHexes) {
                    int[] candidateCenter = hexToCartesian(candidateHex, gridSize);
                    int overlap = 0;

                    for (double x = chunkMinX; x < chunkMaxX; x += sampleStep) {
                        for (double z = chunkMinZ; z < chunkMaxZ; z += sampleStep) {
                            if (isPointInHex(x, z, candidateCenter[0], candidateCenter[1], gridSize)) {
                                overlap++;
                            }
                        }
                    }

                    if (overlap > maxOverlap) {
                        maxOverlap = overlap;
                        dominantHex = candidateHex;
                    }
                }

                // Check if our target hex is the dominant one
                if (dominantHex != null &&
                    dominantHex.getQ() == hexCoord.getQ() &&
                    dominantHex.getR() == hexCoord.getR()) {
                    dominantChunks.add(cx + ":" + cz);
                }
            }
        }

        return dominantChunks;
    }

    /**
     * Determines the hex grid coordinate (HexVector2) that has the largest overlapping area with the given chunk.
     * This is done by checking all hexes overlapping the chunk and selecting the one with the largest intersection area.
     *
     * @param world The world instance
     * @param area The area to evaluate
     * @return The HexVector2 of the hex with the largest overlap
     */
    public static HexVector2 getDominantHexForArea(WWorld world, Area area) {
        int hexSize = world.getPublicData().getHexGridSize();
        int chunkSize = world.getPublicData().getChunkSize();
        // Get all hexes overlapping the chunk
        HexVector2[] hexes = getHexesForArea(hexSize, area);
        // Rectangle (chunk) bounds
        int minX = area.getPosition().getX();
        int minZ = area.getPosition().getZ();
        int maxX = minX + area.getSize().getX();
        int maxZ = minZ + area.getSize().getZ();
        int worldX1 = area.getPosition().getX();
        int worldZ1 = area.getPosition().getZ();
        int worldX2 = worldX1 + area.getSize().getX();
        int worldZ2 = worldZ1 + area.getSize().getZ();
        // For each hex, estimate overlap area by sampling points in the chunk
        int sampleStep = Math.max(1, chunkSize / 8); // sample grid granularity
        HexVector2 bestHex = hexes[0];
        int maxCount = -1;
        for (HexVector2 hex : hexes) {
            int[] hexCenter = hexToCartesian(hex, hexSize);
            int count = 0;
            for (double x = minX; x < maxX; x += sampleStep) {
                for (double z = minZ; z < maxZ; z += sampleStep) {
                    if (isPointInHex(x, z, hexCenter[0], hexCenter[1], hexSize)) {
                        count++;
                    }
                }
            }
            if (count > maxCount) {
                maxCount = count;
                bestHex = hex;
            }
        }
        return bestHex;
    }

}
