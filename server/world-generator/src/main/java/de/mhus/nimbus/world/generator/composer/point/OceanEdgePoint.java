package de.mhus.nimbus.world.generator.composer.point;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    public void initPosition(Area biome, ComposeContext context) {
        super.initPosition(biome, context);
        de.mhus.nimbus.world.shared.world.HexLocalEdgeVector edgeVector = composePosition(biome, context);
        if (edgeVector != null) {
            setHexLocalPosition(null);
            setHexLocalEdgeVector(edgeVector);
        }
        // Otherwise: parent already set center fallback — point lands at biome center
    }

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

        // Find edge coordinates - coords at biome edge OR coords that have ocean/coast neighbors
        List<de.mhus.nimbus.generated.types.HexVector2> edgeCoords = new ArrayList<>();
        for (de.mhus.nimbus.generated.types.HexVector2 coord : coordinates) {
            if (isAtBiomeEdge(coord, biome, context) || hasOceanCoastNeighbor(coord, context)) {
                edgeCoords.add(coord);
            }
        }

        // ALWAYS also check adjacent filler grids (they might be on the route)
        log.info("OceanEdgePoint {}: Found {} coords in biome, also checking adjacent filler grids", getName(), edgeCoords.size());
        List<de.mhus.nimbus.generated.types.HexVector2> adjacentFillers = findAdjacentFillerGridsWithOcean(biome, context);
        if (!adjacentFillers.isEmpty()) {
            log.info("OceanEdgePoint {}: Found {} adjacent filler grids with ocean access: {}",
                getName(), adjacentFillers.size(),
                adjacentFillers.stream().limit(10).map(c -> String.format("(%d,%d)", c.getQ(), c.getR())).toList());
            edgeCoords.addAll(adjacentFillers);
        } else {
            log.info("OceanEdgePoint {}: No adjacent filler grids with ocean access found", getName());
        }

        if (edgeCoords.isEmpty()) {
            log.warn("OceanEdgePoint {}: No edge found, using center", getName());
            return biome.getPlacedCenter(); // Fallback
        }

        // If oceanDirection is set, prefer coordinates in that direction
        de.mhus.nimbus.generated.types.HexVector2 selected;
        if (oceanDirection != null && biome.getPlacedCenter() != null) {
            selected = findBestCoordInDirection(edgeCoords, biome.getPlacedCenter(), oceanDirection);
            log.info("OceanEdgePoint {}: Selected grid ({},{}) in direction {}",
                getName(), selected.getQ(), selected.getR(), oceanDirection);
        } else {
            selected = edgeCoords.get(0);
            log.warn("OceanEdgePoint {}: No oceanDirection set, using first coordinate ({},{})",
                getName(), selected.getQ(), selected.getR());
        }

        return selected;
    }

    /**
     * Composes the position for this ocean edge point.
     * The grid coordinate has already been selected by selectGridCoordinate().
     * Finds the edge that leads to ocean/coast by checking all neighbors.
     *
     * @param biome The biome this point belongs to
     * @param context The compose context with all biomes, points, and maps
     * @return Composed HexLocalEdgeVector (shared type) at ocean edge
     */
    public de.mhus.nimbus.world.shared.world.HexLocalEdgeVector composePosition(Area biome, ComposeContext context) {
        // Get the selected grid coordinate
        de.mhus.nimbus.generated.types.HexVector2 gridCoord = getGridCoordinate();
        if (gridCoord == null) {
            log.warn("OceanEdgePoint {}: No grid coordinate set, using default WEST edge", getName());
            return createDefaultEdgeVector();
        }

        // Check all 6 neighbors to find which one is ocean/coast
        de.mhus.nimbus.world.shared.world.WHexGrid.EDGE oceanEdge = findOceanEdge(gridCoord, context);

        if (oceanEdge == null) {
            log.warn("OceanEdgePoint {}: No ocean/coast neighbor at grid ({},{}), using fallback",
                getName(), gridCoord.getQ(), gridCoord.getR());
            oceanEdge = oceanDirection != null ? directionToEdge(oceanDirection) : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.WEST;
        }

        // Position at middle of the edge (numerator = denominator/2)
        int denominator = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_EDGE_DIVIDER;
        int numerator = denominator / 2;

        log.info("OceanEdgePoint {}: Placed at grid ({},{}) edge {} leading to ocean/coast",
            getName(), gridCoord.getQ(), gridCoord.getR(), oceanEdge);

        return new de.mhus.nimbus.world.shared.world.HexLocalEdgeVector(oceanEdge, numerator, denominator);
    }

    /**
     * Finds the edge that leads to an ocean or coast neighbor.
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid.EDGE findOceanEdge(de.mhus.nimbus.generated.types.HexVector2 coord, ComposeContext context) {
        log.info("OceanEdgePoint {}: Checking neighbors of grid ({},{}) to find ocean/coast edge", getName(), coord.getQ(), coord.getR());

        for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values()) {
            de.mhus.nimbus.generated.types.HexVector2 neighborCoord =
                de.mhus.nimbus.world.shared.util.HexMathUtil.getNeighborPosition(coord, edge);

            Area neighborBiome = getBiomeAt(neighborCoord, context);
            if (neighborBiome == null) {
                log.info("  -> Edge {}: neighbor ({},{}) has no biome", edge, neighborCoord.getQ(), neighborCoord.getR());
            } else if (neighborBiome instanceof Biome) {
                BiomeType type = ((Biome) neighborBiome).getType();
                log.info("  -> Edge {}: neighbor ({},{}) is {} (biome: {})",
                    edge, neighborCoord.getQ(), neighborCoord.getR(), type, neighborBiome.getName());

                if (type == BiomeType.OCEAN || type == BiomeType.COAST) {
                    log.info("*** OceanEdgePoint {}: FOUND ocean/coast at edge {} pointing to ({},{}) type={}",
                        getName(), edge, neighborCoord.getQ(), neighborCoord.getR(), type);
                    return edge;
                }
            } else {
                log.info("  -> Edge {}: neighbor ({},{}) is not a Biome ({})",
                    edge, neighborCoord.getQ(), neighborCoord.getR(), neighborBiome.getClass().getSimpleName());
            }
        }

        log.warn("OceanEdgePoint {}: No ocean/coast neighbor found for grid ({},{})", getName(), coord.getQ(), coord.getR());
        return null;
    }

    private de.mhus.nimbus.world.shared.world.HexLocalEdgeVector createDefaultEdgeVector() {
        int denominator = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_EDGE_DIVIDER;
        int numerator = denominator / 2;
        return new de.mhus.nimbus.world.shared.world.HexLocalEdgeVector(
            de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.WEST, numerator, denominator);
    }

    /**
     * Converts Direction to WHexGrid.EDGE.
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid.EDGE directionToEdge(Direction direction) {
        return switch (direction) {
            case NE -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
            case E -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.EAST;
            case SE -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_EAST;
            case SW -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_WEST;
            case W -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.WEST;
            case NW -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_WEST;
            // N and S don't map directly to hex edges, use closest
            case N -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
            case S -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_EAST;
        };
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
        for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values()) {
            de.mhus.nimbus.generated.types.HexVector2 neighborCoord =
                de.mhus.nimbus.world.shared.util.HexMathUtil.getNeighborPosition(coord, edge);

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

    /**
     * Checks if a coordinate has any ocean or coast neighbor.
     */
    private boolean hasOceanCoastNeighbor(de.mhus.nimbus.generated.types.HexVector2 coord, ComposeContext context) {
        for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values()) {
            de.mhus.nimbus.generated.types.HexVector2 neighborCoord =
                de.mhus.nimbus.world.shared.util.HexMathUtil.getNeighborPosition(coord, edge);

            Area neighborBiome = getBiomeAt(neighborCoord, context);
            if (neighborBiome instanceof Biome) {
                BiomeType type = ((Biome) neighborBiome).getType();
                if (type == BiomeType.OCEAN || type == BiomeType.COAST) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds adjacent filler grids (neighbors of the biome) that have ocean/coast access.
     */
    private List<de.mhus.nimbus.generated.types.HexVector2> findAdjacentFillerGridsWithOcean(Area biome, ComposeContext context) {
        List<de.mhus.nimbus.generated.types.HexVector2> result = new ArrayList<>();
        Set<String> checked = new java.util.HashSet<>();

        // For each coord in the biome, check its neighbors
        for (de.mhus.nimbus.generated.types.HexVector2 coord : biome.getAssignedCoordinates()) {
            for (de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge : de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.values()) {
                de.mhus.nimbus.generated.types.HexVector2 neighborCoord =
                    de.mhus.nimbus.world.shared.util.HexMathUtil.getNeighborPosition(coord, edge);

                String key = TypeUtil.toStringHexCoord(neighborCoord);
                if (checked.contains(key)) {
                    continue;
                }
                checked.add(key);

                // Check if this neighbor is not in the biome
                boolean inBiome = biome.getAssignedCoordinates().stream()
                    .anyMatch(c -> c.getQ() == neighborCoord.getQ() && c.getR() == neighborCoord.getR());

                if (!inBiome) {
                    // Check if this neighbor has ocean/coast access
                    if (hasOceanCoastNeighbor(neighborCoord, context)) {
                        result.add(neighborCoord);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Gets the biome at a specific coordinate.
     */
    private Area getBiomeAt(de.mhus.nimbus.generated.types.HexVector2 coord, ComposeContext context) {
        for (PlacedBiome placed : context.getPlacedBiomes()) {
            if (placed.getCoordinates().stream()
                .anyMatch(c -> c.getQ() == coord.getQ() && c.getR() == coord.getR())) {
                return placed.getBiome();
            }
        }
        return null;
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
