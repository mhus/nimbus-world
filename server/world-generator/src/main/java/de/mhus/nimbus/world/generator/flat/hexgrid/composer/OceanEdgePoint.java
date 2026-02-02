package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Special point type that must be placed at the edge of an ocean biome.
 * The composing logic ensures this point is positioned at the ocean boundary.
 *
 * Example uses:
 * - Harbor points at ocean edges
 * - Beach landing points
 * - Coastal fortress locations
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OceanEdgePoint extends Point {

    /**
     * Preferred direction towards ocean (optional).
     * If set, tries to find ocean edge in this direction.
     */
    private Direction oceanDirection;

    @Override
    public de.mhus.nimbus.generated.types.HexVector2 selectGridCoordinate(Area biome, ComposeContext context) {
        // Find edge coordinates (coordinates at biome boundary)
        List<de.mhus.nimbus.generated.types.HexVector2> coordinates = biome.getAssignedCoordinates();
        if (coordinates == null || coordinates.isEmpty()) {
            log.info("OceanEdgePoint {}: No coordinates in biome {}, using center", getName(), biome.getName());
            return biome.getPlacedCenter(); // Fallback
        }

        log.info("OceanEdgePoint {}: Biome {} (center={}) has {} coordinates", getName(), biome.getName(),
            biome.getPlacedCenter(), coordinates.size());

        List<de.mhus.nimbus.generated.types.HexVector2> edgeCoords = new ArrayList<>();
        for (de.mhus.nimbus.generated.types.HexVector2 coord : coordinates) {
            if (isAtBiomeEdge(coord, biome, context)) {
                edgeCoords.add(coord);
            }
        }

        log.info("OceanEdgePoint {}: Found {} edge coordinates: {}", getName(), edgeCoords.size(),
            edgeCoords.stream().limit(5).map(c -> String.format("(%d,%d)", c.getQ(), c.getR())).toList());

        if (edgeCoords.isEmpty()) {
            log.info("OceanEdgePoint {}: No edge found, using center", getName());
            return biome.getPlacedCenter(); // Fallback
        }

        // If oceanDirection is set, prefer coordinates in that direction
        de.mhus.nimbus.generated.types.HexVector2 selected;
        if (oceanDirection != null && biome.getPlacedCenter() != null) {
            selected = findBestCoordInDirection(edgeCoords, biome.getPlacedCenter(), oceanDirection);
            log.info("OceanEdgePoint {}: Selected edge coordinate {} in direction {}", getName(), selected, oceanDirection);
        } else {
            selected = edgeCoords.get(0);
            log.info("OceanEdgePoint {}: Selected first edge coordinate {}", getName(), selected);
        }

        return selected;
    }

    /**
     * Composes the position for this ocean edge point.
     * The grid coordinate has already been selected by selectGridCoordinate().
     * This method just returns the local position within that grid (center).
     *
     * @param biome The biome this point belongs to
     * @param context The compose context with all biomes, points, and maps
     * @return Composed HexLocalPosition (shared type) at center of selected grid
     */
    public de.mhus.nimbus.world.shared.world.HexLocalPosition composePosition(Area biome, ComposeContext context) {
        // Grid coordinate was already selected by selectGridCoordinate()
        // Just return center position (0,0) within that grid
        return createCenterPosition(context);
    }

    private de.mhus.nimbus.world.shared.world.HexLocalPosition createCenterPosition(ComposeContext context) {
        de.mhus.nimbus.generated.types.HexVector2 hexPosition =
            de.mhus.nimbus.generated.types.HexVector2.builder()
                .q(0)
                .r(0)
                .build();

        int divider = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPosition, divider, size);
    }

    private boolean isAtBiomeEdge(de.mhus.nimbus.generated.types.HexVector2 coord, Area biome, ComposeContext context) {
        // Check all 6 neighbors - if any neighbor is not in this biome, this is an edge
        int q = coord.getQ();
        int r = coord.getR();

        // Hex neighbors: NE, E, SE, SW, W, NW
        int[][] neighbors = {
            {q+1, r-1},  // NE
            {q+1, r},    // E
            {q, r+1},    // SE
            {q-1, r+1},  // SW
            {q-1, r},    // W
            {q, r-1}     // NW
        };

        for (int[] neighbor : neighbors) {
            de.mhus.nimbus.generated.types.HexVector2 neighborCoord =
                de.mhus.nimbus.generated.types.HexVector2.builder()
                    .q(neighbor[0])
                    .r(neighbor[1])
                    .build();

            // Check if neighbor is in this biome
            boolean inBiome = biome.getAssignedCoordinates().stream()
                .anyMatch(c -> c.getQ() == neighborCoord.getQ() && c.getR() == neighborCoord.getR());

            if (!inBiome) {
                // Found a neighbor outside this biome - this is an edge
                return true;
            }
        }

        return false;
    }

    private de.mhus.nimbus.generated.types.HexVector2 findBestCoordInDirection(
            List<de.mhus.nimbus.generated.types.HexVector2> edgeCoords,
            de.mhus.nimbus.generated.types.HexVector2 center,
            Direction direction) {

        // Find coordinate that is furthest in the desired direction from center
        de.mhus.nimbus.generated.types.HexVector2 bestCoord = edgeCoords.get(0);
        double bestScore = scoreCoordInDirection(bestCoord, center, direction);

        for (de.mhus.nimbus.generated.types.HexVector2 coord : edgeCoords) {
            double score = scoreCoordInDirection(coord, center, direction);
            if (score > bestScore) {
                bestScore = score;
                bestCoord = coord;
            }
        }

        return bestCoord;
    }

    private double scoreCoordInDirection(de.mhus.nimbus.generated.types.HexVector2 coord,
                                        de.mhus.nimbus.generated.types.HexVector2 center,
                                        Direction direction) {
        int dq = coord.getQ() - center.getQ();
        int dr = coord.getR() - center.getR();
        int ds = -dq - dr;  // s = -q-r in cube coordinates

        // Score based on direction in hex coordinates (higher score = better match)
        // In pointy-top hex: q-axis is E-W, r-axis is diagonal, s-axis is diagonal
        return switch (direction) {
            case N -> -dr;      // North: negative r
            case NE -> dq - dr; // NorthEast: positive q, negative r
            case E -> dq;       // East: positive q
            case SE -> dr;      // SouthEast: positive r
            case S -> dr;       // South: positive r (same as SE)
            case SW -> -dq;     // SouthWest: negative q
            case W -> -dq;      // West: negative q (maximize negative q)
            case NW -> -dr - dq; // NorthWest: negative r, negative q
        };
    }
}
