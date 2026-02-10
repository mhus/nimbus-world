package de.mhus.nimbus.world.generator.composer.point;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.structure.PreparedPosition;
import de.mhus.nimbus.world.generator.composer.area.RelativePosition;
import de.mhus.nimbus.world.generator.composer.biome.BiomeDistance;
import de.mhus.nimbus.world.generator.composer.town.TownConnectionPoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Point represents a specific location within a biome.
 * Points are used to mark important locations (cities, landmarks, spawn points)
 * and can later be used as connection points for Flows.
 *
 * Unlike Areas, Points don't occupy multiple hexagons - they are placed at a
 * specific coordinate (q, r) with local position (lx, lz) within that hex grid.
 *
 * This is an abstract base class. Use concrete subclasses:
 * - PositionPoint: Standard positioning (most common)
 * - EdgePoint: Positioned at biome edges
 * - OceanEdgePoint: Positioned at ocean edges
 *
 * Example: Minas Tirith, Mount Doom, Village Centers, Quest Markers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "pointType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PositionPoint.class, name = "point"),
    @JsonSubTypes.Type(value = EdgePoint.class, name = "edge"),
    @JsonSubTypes.Type(value = OceanEdgePoint.class, name = "ocean-edge"),
    @JsonSubTypes.Type(value = TownConnectionPoint.class, name = "village-connection")
})
public abstract class Point extends Feature {

    // ========== INPUT: User-defined relative positioning ==========

    /**
     * ID of the biome this point belongs to.
     * Points are always defined relative to a biome.
     */
    private String biomeId;

    /**
     * Direction from biome center (N, NE, E, SE, S, SW, W, NW, CENTER).
     * Used to position point relative to the biome's center.
     */
    private Direction direction;

    /**
     * Distance from biome center in hexes (CENTER=0, NEAR=1, NORMAL=2, FAR=3, VERY_FAR=4).
     * Determines how many hexes away from center in the given direction.
     */
    private BiomeDistance biomeDistance;

    /**
     * Side of the biome (N, NE, E, SE, S, SW, W, NW).
     * Used for points that should be placed at the edge of a biome.
     * If set, direction and biomeDistance are ignored.
     */
    private Direction biomeSide;

    /**
     * Offset along the biome side (0.0 = start of side, 1.0 = end of side).
     * Only used when biomeSide is set.
     */
    private Double sideOffset;

    /**
     * List of relative positions to other points in the same biome.
     * Allows defining position based on other points.
     */
    private List<RelativeToPoint> relativeToPoints;

    /**
     * Custom parameters for the point (e.g., role, level, type).
     */
    private Map<String, String> parameters;

    /**
     * If true, this point is synthetic/pre-composed and should not be processed by PointComposer.
     * Used for points that have fixed positions (e.g., VillageConnectionPoint) that are
     * already positioned and don't need composition logic.
     * Default: false (point will be composed normally)
     */
    private boolean precomposed;

    // ========== LEGACY: Deprecated fields for backward compatibility ==========

    /**
     * @deprecated Use biomeId, direction, biomeDistance instead
     */
    @Deprecated
    private List<RelativePosition> positions;

    /**
     * @deprecated Use biomeId with direction/biomeDistance or biomeSide/sideOffset instead
     */
    @Deprecated
    private SnapConfig snap;

    /**
     * Composed data - calculated during composition phase at Point level.
     * Separates input configuration from runtime computed values.
     */
    private PointComposed pointComposed;

    /**
     * Inner class for pointComposed (calculated) data at Point level.
     * Stores values computed during composition, separate from user input.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointComposed {
        /**
         * Grid coordinate - which HexGrid this point is in (q, r).
         */
        private HexVector2 gridCoordinate;

        /**
         * Biome name where this point is located.
         */
        private String biome;

        /**
         * Position within a hex grid using shared HexLocalPosition.
         * Used when point is placed at a specific location within a hex.
         * Either hexLocalPosition OR hexLocalEdgeVector is set, not both.
         */
        private de.mhus.nimbus.world.shared.world.HexLocalPosition hexLocalPosition;

        /**
         * Position on the side/edge of a hex grid using shared HexLocalEdgeVector.
         * Used when point should be placed at the boundary between hexes.
         * Either hexLocalPosition OR hexLocalEdgeVector is set, not both.
         */
        private de.mhus.nimbus.world.shared.world.HexLocalEdgeVector hexLocalEdgeVector;

        /**
         * Prepared positions with calculated angles.
         * Set during preparation phase.
         */
        private List<PreparedPosition> preparedPositions;

        // Legacy fields for backward compatibility
        /**
         * @deprecated Use gridCoordinate instead
         */
        @Deprecated
        private HexVector2 placedCoordinate;

        /**
         * @deprecated Use hexLocalPosition instead (no direct lx/lz)
         */
        @Deprecated
        private Integer placedLx;

        /**
         * @deprecated Use hexLocalPosition instead (no direct lx/lz)
         */
        @Deprecated
        private Integer placedLz;

        /**
         * @deprecated Use biome field instead
         */
        @Deprecated
        private String placedInBiome;

    }

    /**
     * Selects the grid coordinate for this point within the given biome.
     * Default: returns biome center. Subclasses can override to select different coordinates.
     *
     * @param biome The biome this point belongs to
     * @param context The compose context
     * @return The selected grid coordinate, or null to use biome center
     */
    public HexVector2 selectGridCoordinate(Area biome, ComposeContext context) {
        // If biomeSide is specified, select a grid at that edge of the biome
        if (biomeSide != null) {
            HexVector2 center = biome.getPlacedCenter();
            if (center == null) {
                return null;
            }

            // Convert Direction to EDGE enum
            de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge = convertDirectionToEdge(biomeSide);
            if (edge != null) {
                // Get grid at the specified edge relative to center
                HexVector2 edgeGrid = getGridAtEdge(center, edge);
                return edgeGrid;
            }
        }

        // Default: use biome center
        return biome.getPlacedCenter();
    }

    /**
     * Converts Direction enum to EDGE enum.
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid.EDGE convertDirectionToEdge(Direction direction) {
        return switch (direction) {
            case NE -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
            case E -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.EAST;
            case SE -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_EAST;
            case SW -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_WEST;
            case W -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.WEST;
            case NW -> de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_WEST;
            case N, S -> null; // N and S are not valid hex edges
        };
    }

    /**
     * Gets a grid coordinate at the specified edge relative to center.
     */
    private HexVector2 getGridAtEdge(HexVector2 center, de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge) {
        // Offset by 1 grid in the direction of the edge
        return switch (edge) {
            case NORTH_EAST -> HexVector2.builder().q(center.getQ() + 1).r(center.getR() - 1).build();
            case EAST -> HexVector2.builder().q(center.getQ() + 1).r(center.getR()).build();
            case SOUTH_EAST -> HexVector2.builder().q(center.getQ()).r(center.getR() + 1).build();
            case SOUTH_WEST -> HexVector2.builder().q(center.getQ() - 1).r(center.getR() + 1).build();
            case WEST -> HexVector2.builder().q(center.getQ() - 1).r(center.getR()).build();
            case NORTH_WEST -> HexVector2.builder().q(center.getQ()).r(center.getR() - 1).build();
        };
    }

    /**
     * Prepares this point for composition by calculating concrete values from positions.
     */
    public void prepareForComposition() {
        // Initialize pointComposed if needed
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }

        // Prepare positions (convert RelativePosition → PreparedPosition)
        if (positions != null && !positions.isEmpty()) {
            List<PreparedPosition> preparedPositions = new java.util.ArrayList<>();
            for (RelativePosition pos : positions) {
                preparedPositions.add(preparePosition(pos));
            }
            pointComposed.setPreparedPositions(preparedPositions);
        }
    }

    /**
     * Converts RelativePosition to PreparedPosition with concrete values.
     */
    private PreparedPosition preparePosition(RelativePosition position) {
        PreparedPosition prepared = new PreparedPosition();
        prepared.setOriginal(position);
        prepared.setDirection(position.getDirection());
        prepared.setDirectionAngle(convertDirectionToAngle(position.getDirection()));
        prepared.setDistanceFrom(position.getEffectiveDistanceFrom());
        prepared.setDistanceTo(position.getEffectiveDistanceTo());
        prepared.setAnchor(position.getAnchor());
        prepared.setPriority(position.getPriority());
        return prepared;
    }

    /**
     * Converts Direction enum to angle in degrees.
     */
    private int convertDirectionToAngle(Direction direction) {
        if (direction == null) return 0;
        // Pointy-top hex has 6 sides at 60° intervals (no N/S, starts with NE):
        // NE(0°), E(60°), SE(120°), SW(180°), W(240°), NW(300°)
        // N and S are mapped to nearest hex directions
        return switch (direction) {
            case N -> 330;    // North (top spike) → rounds to NW/NE
            case NE -> 0;     // Northeast: top-right side
            case E -> 60;     // East: right side
            case SE -> 120;   // Southeast: bottom-right side
            case S -> 150;    // South (bottom spike) → rounds to SE/SW
            case SW -> 180;   // Southwest: bottom-left side
            case W -> 240;    // West: left side
            case NW -> 300;   // Northwest: top-left side
        };
    }

    /**
     * Returns true if this point has been placed (has calculated coordinates).
     */
    @JsonIgnore
    public boolean isPlaced() {
        if (pointComposed == null) {
            return false;
        }
        // Check if gridCoordinate is set AND either position type is set
        if (pointComposed.getGridCoordinate() != null) {
            if (pointComposed.getHexLocalPosition() != null || pointComposed.getHexLocalEdgeVector() != null) {
                return true;
            }
        }
        return pointComposed.getPlacedCoordinate() != null
            && pointComposed.getPlacedLx() != null
            && pointComposed.getPlacedLz() != null;
    }

    /**
     * Gets a human-readable position string.
     */
    @JsonIgnore
    public String getPlacedPositionString() {
        if (!isPlaced()) {
            return "not placed";
        }

        HexVector2 gridCoord = pointComposed.getGridCoordinate();
        String biomeName = pointComposed.getBiome() != null ? pointComposed.getBiome() : "unknown";

        // New format: shared HexLocalPosition
        if (pointComposed.getHexLocalPosition() != null) {
            de.mhus.nimbus.world.shared.world.HexLocalPosition pos = pointComposed.getHexLocalPosition();
            return String.format("hex[%d,%d] local[%d,%d] (divider=%d) in %s",
                gridCoord.getQ(), gridCoord.getR(),
                pos.position().getQ(), pos.position().getR(),
                pos.divider(),
                biomeName);
        }

        // New format: shared HexLocalEdgeVector
        if (pointComposed.getHexLocalEdgeVector() != null) {
            de.mhus.nimbus.world.shared.world.HexLocalEdgeVector edge = pointComposed.getHexLocalEdgeVector();
            return String.format("hex[%d,%d] edge[%s %d/%d] in %s",
                gridCoord.getQ(), gridCoord.getR(),
                edge.side(),
                edge.numerator(), edge.denominator(),
                biomeName);
        }

        // Legacy format: placedLx/placedLz
        HexVector2 placedCoordinate = pointComposed.getPlacedCoordinate();
        Integer placedLx = pointComposed.getPlacedLx();
        Integer placedLz = pointComposed.getPlacedLz();
        String placedInBiome = pointComposed.getPlacedInBiome();
        if (placedCoordinate != null && placedLx != null && placedLz != null) {
            return String.format("hex[%d,%d] local[%d,%d] in %s",
                placedCoordinate.getQ(), placedCoordinate.getR(),
                placedLx, placedLz,
                placedInBiome != null ? placedInBiome : "unknown");
        }

        return "placed but no position data";
    }

    // Helper methods for backward compatibility

    public HexVector2 getPlacedCoordinate() {
        return pointComposed != null ? pointComposed.getPlacedCoordinate() : null;
    }

    public void setPlacedCoordinate(HexVector2 placedCoordinate) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setPlacedCoordinate(placedCoordinate);
    }

    public Integer getPlacedLx() {
        return pointComposed != null ? pointComposed.getPlacedLx() : null;
    }

    public void setPlacedLx(Integer placedLx) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setPlacedLx(placedLx);
    }

    public Integer getPlacedLz() {
        return pointComposed != null ? pointComposed.getPlacedLz() : null;
    }

    public void setPlacedLz(Integer placedLz) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setPlacedLz(placedLz);
    }

    public String getPlacedInBiome() {
        return pointComposed != null ? pointComposed.getPlacedInBiome() : null;
    }

    public void setPlacedInBiome(String placedInBiome) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setPlacedInBiome(placedInBiome);
    }

    public List<PreparedPosition> getPreparedPositions() {
        return pointComposed != null ? pointComposed.getPreparedPositions() : null;
    }

    public void setPreparedPositions(List<PreparedPosition> preparedPositions) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setPreparedPositions(preparedPositions);
    }

    // New helper methods for shared types

    public HexVector2 getGridCoordinate() {
        return pointComposed != null ? pointComposed.getGridCoordinate() : null;
    }

    public void setGridCoordinate(HexVector2 gridCoordinate) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setGridCoordinate(gridCoordinate);
    }

    public String getBiome() {
        return pointComposed != null ? pointComposed.getBiome() : null;
    }

    public void setBiome(String biome) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setBiome(biome);
    }

    public de.mhus.nimbus.world.shared.world.HexLocalPosition getHexLocalPosition() {
        return pointComposed != null ? pointComposed.getHexLocalPosition() : null;
    }

    public void setHexLocalPosition(de.mhus.nimbus.world.shared.world.HexLocalPosition hexLocalPosition) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setHexLocalPosition(hexLocalPosition);
    }

    public de.mhus.nimbus.world.shared.world.HexLocalEdgeVector getHexLocalEdgeVector() {
        return pointComposed != null ? pointComposed.getHexLocalEdgeVector() : null;
    }

    public void setHexLocalEdgeVector(de.mhus.nimbus.world.shared.world.HexLocalEdgeVector hexLocalEdgeVector) {
        if (pointComposed == null) {
            pointComposed = new PointComposed();
        }
        pointComposed.setHexLocalEdgeVector(hexLocalEdgeVector);
    }
}
