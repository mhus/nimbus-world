package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector2Pair;
import de.mhus.nimbus.shared.utils.TypeUtil;
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

    private static final double SQRT_3 = Math.sqrt(3.0);

    /**
     * Converts hex coordinates to cartesian coordinates (center position).
     * Uses pointy-top hexagon orientation.
     *
     * @param hex The hex vector with q and r coordinates
     * @param gridSize The diameter of the hexagon in blocks
     * @return Array with [x, z] center coordinates
     */
    public static double[] hexToCartesian(HexVector2 hex, int gridSize) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        double radius = gridSize / 2.0;
        double x = radius * SQRT_3 * (hex.getQ() + hex.getR() / 2.0);
        double z = radius * 1.5 * hex.getR();

        return new double[]{x, z};
    }

    /**
     * Tests if a cartesian point is inside a hexagon.
     * Uses cube coordinate conversion for accurate point-in-hex testing.
     *
     * @param x The x coordinate of the point to test
     * @param z The z coordinate of the point to test
     * @param hexCenterX The x coordinate of the hex center
     * @param hexCenterZ The z coordinate of the hex center
     * @param gridSize The diameter of the hexagon in blocks
     * @return true if the point is inside the hexagon
     */
    public static boolean isPointInHex(double x, double z, double hexCenterX, double hexCenterZ, int gridSize) {
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        double radius = gridSize / 2.0;

        // Transform to hex-relative coordinates
        double dx = x - hexCenterX;
        double dz = z - hexCenterZ;

        // Convert to cube coordinates (fractional)
        double q = (SQRT_3 / 3.0 * dx - 1.0 / 3.0 * dz) / radius;
        double r = (2.0 / 3.0 * dz) / radius;
        double s = -q - r;

        // Point is inside if all cube coordinate magnitudes <= 1
        return Math.abs(q) <= 1.0 && Math.abs(r) <= 1.0 && Math.abs(s) <= 1.0;
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

    public static HexVector2 getNeighborPosition(HexVector2 position, WHexGrid.NEIGHBOR nabor) {
        int q = position.getQ();
        int r = position.getR();
        switch (nabor) {
            case TOP_RIGHT:
                return HexVector2.builder().q(q + 1).r(r - 1).build();
            case RIGHT:
                return HexVector2.builder().q(q + 1).r(r).build();
            case BOTTOM_RIGHT:
                return HexVector2.builder().q(q).r(r + 1).build();
            case BOTTOM_LEFT:
                return HexVector2.builder().q(q - 1).r(r + 1).build();
            case LEFT:
                return HexVector2.builder().q(q - 1).r(r).build();
            case TOP_LEFT:
                return HexVector2.builder().q(q).r(r - 1).build();
            default:
                throw new IllegalArgumentException("Unknown nabor direction: " + nabor);
        }
    }

    public static HexVector2 flatToHex(Vector2Int flatPos, int hexGridSize) {
        int x = flatPos.getX();
        int z = flatPos.getZ();
        double radius = hexGridSize / 2.0;

        double q = (SQRT_3 / 3.0 * x - 1.0 / 3.0 * z) / radius;
        double r = (2.0 / 3.0 * z) / radius;

        int rq = (int) Math.round(q);
        int rr = (int) Math.round(r);
        int rs = (int) Math.round(-q - r);

        double q_diff = Math.abs(rq - q);
        double r_diff = Math.abs(rr - r);
        double s_diff = Math.abs(rs + q + r);

        if (q_diff > r_diff && q_diff > s_diff) {
            rq = -rr - rs;
        } else if (r_diff > s_diff) {
            rr = -rq - rs;
        }

        return HexVector2.builder().q(rq).r(rr).build();
    }

    /**
     * Checks if the given chunk (cx, cz) is the dominant chunk for the specified hex coordinate.
     * A chunk is dominant for a hex if the hex has the largest overlapping area with that chunk
     * compared to all other hexes that overlap the chunk.
     *
     * @param chunkSize The size of chunks in blocks
     * @param gridSize The diameter of the hexagon in blocks
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @param hexCoordinate The hex coordinate to test
     * @return true if this chunk is the dominant chunk for the given hex, false otherwise
     */
    public static boolean isDominantChunkForHexGrid(int chunkSize, int gridSize, int cx, int cz, HexVector2 hexCoordinate) {
        if (hexCoordinate == null) {
            throw new IllegalArgumentException("hexCoordinate cannot be null");
        }
        if (chunkSize <= 0 || gridSize <= 0) {
            throw new IllegalArgumentException("chunkSize and gridSize must be positive");
        }

        // Get all hexes overlapping the chunk
        HexVector2[] hexes = getHexesForChunk(gridSize, chunkSize, cx, cz);

        // Rectangle (chunk) bounds
        double minX = cx * chunkSize;
        double minZ = cz * chunkSize;
        double maxX = (cx + 1) * chunkSize;
        double maxZ = (cz + 1) * chunkSize;

        // For each hex, estimate overlap area by sampling points in the chunk
        int sampleStep = Math.max(1, chunkSize / 8); // sample grid granularity
        HexVector2 bestHex = hexes[0];
        int maxCount = -1;

        for (HexVector2 hex : hexes) {
            double[] hexCenter = hexToCartesian(hex, gridSize);
            int count = 0;
            for (double x = minX; x < maxX; x += sampleStep) {
                for (double z = minZ; z < maxZ; z += sampleStep) {
                    if (isPointInHex(x, z, hexCenter[0], hexCenter[1], gridSize)) {
                        count++;
                    }
                }
            }
            if (count > maxCount) {
                maxCount = count;
                bestHex = hex;
            }
        }

        // Check if the dominant hex matches the provided hex coordinate
        return bestHex.getQ() == hexCoordinate.getQ() &&
               bestHex.getR() == hexCoordinate.getR();
    }

    /**
     * Internal iterator implementation for lazy position generation.
     */
    private static class HexPositionIterator implements Iterator<Vector2Int> {
        private final double hexCenterX;
        private final double hexCenterZ;
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
            double[] center = hexToCartesian(hex, gridSize);
            this.hexCenterX = center[0];
            this.hexCenterZ = center[1];
            this.gridSize = gridSize;

            // Calculate bounding box
            this.minX = (int) Math.floor(hexCenterX - gridSize);
            this.maxX = (int) Math.ceil(hexCenterX + gridSize);
            this.minZ = (int) Math.floor(hexCenterZ - gridSize);
            this.maxZ = (int) Math.ceil(hexCenterZ + gridSize);

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

    /**
     * Calculates the world coordinate bounds of all hexagons within a specified range from a center hex.
     *
     * @param hexSize   The size (radius) of each hexagon in blocks
     * @param centerHex The center hex coordinates
     * @param range     The range (in hexes) from the center to include
     * @return Array of Vector2Pair representing the world coordinates of the hex corners
     */
    public static Vector2[] getHexAreaBounds(int hexSize, HexVector2 centerHex, int range) {
        java.util.List<Vector2> bounds = new java.util.ArrayList<>();
        for (int dq = -range; dq <= range; dq++) {
            for (int dr = Math.max(-range, -dq - range); dr <= Math.min(range, -dq + range); dr++) {
                int q = centerHex.getQ() + dq;
                int r = centerHex.getR() + dr;
                // Berechne die Weltkoordinaten der Hex-Ecken
                double centerX = hexSize * (Math.sqrt(3) * q + Math.sqrt(3) / 2 * r);
                double centerZ = hexSize * (3.0 / 2 * r);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI / 3 * i;
                    double x = centerX + hexSize * Math.cos(angle);
                    double z = centerZ + hexSize * Math.sin(angle);
                    bounds.add(Vector2.builder().x(x).z(z).build());
                }
            }
        }
        return bounds.toArray(new Vector2[bounds.size()]);
    }

    /**
     * Calculates the hex coordinate for a given chunk in the world.
     * This allocates one exact hex coordinate per chunk.
     *
     * @param world
     * @param cx
     * @param cz
     * @return
     */
    public static HexVector2 getHexForChunk(WWorld world, int cx, int cz) {
        int hexSize = world.getPublicData().getHexGridSize();
        int chunkSize = world.getPublicData().getChunkSize();
        return getHexForChunk(hexSize, chunkSize, cx, cz);
    }

    public static HexVector2 getHexForChunk(int hexSize, int chunkSize, int cx, int cz) {
        // Berechne Weltkoordinaten des Chunk-Ursprungs
        int worldX = cx * chunkSize;
        int worldZ = cz * chunkSize;
        // Umrechnung in Hex-Koordinaten (axial)
        // Annahme: HexMathUtil bietet eine Methode worldToHex(int x, int z, int hexSize)
        // Falls nicht, einfache Umrechnung: q = worldX / hexSize, r = worldZ / hexSize
        int q = worldX / hexSize;
        int r = worldZ / hexSize;
        return HexVector2.builder().q(q).r(r).build();
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
            int q = ecke[0] / hexSize;
            int r = ecke[1] / hexSize;
            String key = q + "," + r;
            if (!uniqueHexes.contains(key)) {
                uniqueHexes.add(key);
                result.add(HexVector2.builder().q(q).r(r).build());
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
            int q = ecke[0] / hexSize;
            int r = ecke[1] / hexSize;
            String key = q + "," + r;
            if (!uniqueHexes.contains(key)) {
                uniqueHexes.add(key);
                result.add(HexVector2.builder().q(q).r(r).build());
                if (result.size() == 3) break; // maximal 3 Hexfelder
            }
        }
        return result.toArray(new HexVector2[result.size()]);
    }

    /**
     * Calculates up to three intersection lines (Vector2Pair) between a chunk (rectangle)
     * and the corresponding hexagon (hex tile) on the x,z plane.
     * Returns an array with 1 to 3 Vector2Pair objects representing the intersection lines.
     *
     * @param world The world instance
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return Array with 1 to 3 Vector2Pair (each representing an intersection line)
     */
    public Vector2Pair[] getHexChunkIntersectionLines(WWorld world, int cx, int cz) {
        int hexSize = world.getPublicData().getHexGridSize();
        int chunkSize = world.getPublicData().getChunkSize();
        double minX = cx * chunkSize;
        double minZ = cz * chunkSize;
        double maxX = (cx + 1) * chunkSize;
        double maxZ = (cz + 1) * chunkSize;
        double[][] chunkCorners = new double[][] {
            {minX, minZ},
            {maxX, minZ},
            {maxX, maxZ},
            {minX, maxZ}
        };
        HexVector2 hex = HexVector2.builder().q((int)(minX / hexSize)).r((int)(minZ / hexSize)).build();
        double[] hexCenter = hexToCartesian(hex, hexSize);
        double radius = hexSize / 2.0;
        double[][] hexCorners = new double[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180 * (60 * i - 30);
            hexCorners[i][0] = hexCenter[0] + radius * Math.cos(angle);
            hexCorners[i][1] = hexCenter[1] + radius * Math.sin(angle);
        }
        java.util.List<Vector2> intersections = new java.util.ArrayList<>();
        for (int ci = 0; ci < 4; ci++) {
            double[] c1 = chunkCorners[ci];
            double[] c2 = chunkCorners[(ci + 1) % 4];
            for (int hi = 0; hi < 6; hi++) {
                double[] h1 = hexCorners[hi];
                double[] h2 = hexCorners[(hi + 1) % 6];
                double[] p = intersectSegments(c1, c2, h1, h2);
                if (p != null) {
                    intersections.add(Vector2.builder().x(p[0]).z(p[1]).build());
                }
            }
        }
        java.util.List<Vector2Pair> result = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < intersections.size(); i += 2) {
            result.add(Vector2Pair.builder().a(intersections.get(i)).b(intersections.get(i + 1)).build());
            if (result.size() == 3) break;
        }
        return result.toArray(new Vector2Pair[result.size()]);
    }

    // Helper function: intersection point of two line segments (2D)
    private static double[] intersectSegments(double[] p1, double[] p2, double[] q1, double[] q2) {
        double s1_x = p2[0] - p1[0];
        double s1_z = p2[1] - p1[1];
        double s2_x = q2[0] - q1[0];
        double s2_z = q2[1] - q1[1];
        double denom = (-s2_x * s1_z + s1_x * s2_z);
        if (denom == 0) return null; // parallel
        double s = (-s1_z * (p1[0] - q1[0]) + s1_x * (p1[1] - q1[1])) / denom;
        double t = ( s2_x * (p1[1] - q1[1]) - s2_z * (p1[0] - q1[0])) / denom;
        if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
            // Schnittpunkt innerhalb beider Segmente
            double ix = p1[0] + (t * s1_x);
            double iz = p1[1] + (t * s1_z);
            return new double[] {ix, iz};
        }
        return null;
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
        double maxX = (cx + 1) * chunkSize;
        double maxZ = (cz + 1) * chunkSize;
        // For each hex, estimate overlap area by sampling points in the chunk
        int sampleStep = Math.max(1, chunkSize / 8); // sample grid granularity
        HexVector2 bestHex = hexes[0];
        int maxCount = -1;
        for (HexVector2 hex : hexes) {
            double[] hexCenter = hexToCartesian(hex, hexSize);
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
        double[] hexCenter = hexToCartesian(hexCoord, gridSize);
        double hexCenterX = hexCenter[0];
        double hexCenterZ = hexCenter[1];

        // Calculate bounding box for the hex (conservative estimate)
        int minX = (int) Math.floor(hexCenterX - gridSize);
        int maxX = (int) Math.ceil(hexCenterX + gridSize);
        int minZ = (int) Math.floor(hexCenterZ - gridSize);
        int maxZ = (int) Math.ceil(hexCenterZ + gridSize);

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
                    double[] candidateCenter = hexToCartesian(candidateHex, gridSize);
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
            double[] hexCenter = hexToCartesian(hex, hexSize);
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
