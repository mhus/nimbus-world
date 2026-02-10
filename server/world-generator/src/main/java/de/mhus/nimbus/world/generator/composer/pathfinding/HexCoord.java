package de.mhus.nimbus.world.generator.composer.pathfinding;

import de.mhus.nimbus.world.generator.composer.town.DistrictGrid;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a hex coordinate in the village pathfinding system.
 * Combines district position, local hex position, and cartesian coordinates.
 * Can also represent edge transition points between districts.
 */
@Data
@EqualsAndHashCode(exclude = {"neighbors", "linkedEdgeCoord", "district"})
public class HexCoord {
    // District position (outer hex grid)
    private final int districtQ;
    private final int districtR;
    private final String districtName;
    private final DistrictGrid district;

    // Local hex position (inner hex grid within district)
    private final int localQ;
    private final int localR;

    // Cartesian coordinates (for calculations and nearest-search)
    private final int x;
    private final int z;

    // Node type
    private HexNodeType type;

    // Edge transition info (if this is an edge transition point)
    private boolean isEdge;
    private EdgeSide edgeSide;
    private int edgeIndex; // 1, 2, or 3 (which of the 3 edge points)

    // Linked edge coordinate on opposite district
    private HexCoord linkedEdgeCoord;

    // Connection point info
    private String connectionPointName;

    // Neighbors (up to 6 for regular hex, fewer for edges)
    private final List<HexCoord> neighbors = new ArrayList<>(6);

    // Walkability
    private boolean walkable;

    /**
     * Constructor for regular hex coordinate.
     */
    public HexCoord(DistrictGrid district, int localQ, int localR, int x, int z) {
        this.district = district;
        this.districtName = district.getName();
        this.districtQ = district.getGridPosition().getQ();
        this.districtR = district.getGridPosition().getR();
        this.localQ = localQ;
        this.localR = localR;
        this.x = x;
        this.z = z;
        this.type = HexNodeType.EMPTY;
        this.walkable = true;
        this.isEdge = false;
    }

    /**
     * Constructor for edge transition point.
     */
    public HexCoord(DistrictGrid district, int localQ, int localR, int x, int z,
                    EdgeSide edgeSide, int edgeIndex) {
        this(district, localQ, localR, x, z);
        this.isEdge = true;
        this.edgeSide = edgeSide;
        this.edgeIndex = edgeIndex;
        this.type = HexNodeType.EDGE;
    }

    /**
     * Get unique key for this coordinate.
     */
    public String getKey() {
        if (isEdge) {
            return String.format("%s:edge_%s_%d", districtName, edgeSide, edgeIndex);
        }
        return String.format("%s:%d,%d", districtName, localQ, localR);
    }

    /**
     * Add a neighbor coordinate.
     */
    public void addNeighbor(HexCoord neighbor) {
        if (!neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
        }
    }

    /**
     * Mark as occupied (not walkable).
     */
    public void markOccupied() {
        this.type = HexNodeType.OCCUPIED;
        this.walkable = false;
    }

    /**
     * Mark as connection point.
     */
    public void markConnectionPoint(String pointName) {
        this.type = HexNodeType.CONNECTION_POINT;
        this.walkable = true;
        this.connectionPointName = pointName;
    }

    /**
     * Link to opposite edge coordinate.
     */
    public void linkToOppositeEdge(HexCoord opposite) {
        this.linkedEdgeCoord = opposite;
        if (opposite.linkedEdgeCoord != this) {
            opposite.linkedEdgeCoord = this;
        }
    }

    /**
     * Calculate hex distance to another coordinate in same district.
     */
    public int hexDistance(HexCoord other) {
        if (!this.districtName.equals(other.districtName)) {
            // Different districts - use cartesian distance
            int dx = other.x - this.x;
            int dz = other.z - this.z;
            return (int) Math.sqrt(dx * dx + dz * dz);
        }

        // Same district - use hex distance
        int dq = Math.abs(this.localQ - other.localQ);
        int dr = Math.abs(this.localR - other.localR);
        int ds = Math.abs((this.localQ + this.localR) - (other.localQ + other.localR));
        return (dq + dr + ds) / 2;
    }

    /**
     * Calculate cartesian distance to another coordinate.
     */
    public double cartesianDistance(HexCoord other) {
        int dx = other.x - this.x;
        int dz = other.z - this.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        if (isEdge) {
            return String.format("HexCoord[%s:edge_%s_%d (%d,%d)]",
                    districtName, edgeSide, edgeIndex, x, z);
        }
        return String.format("HexCoord[%s:(%d,%d) local=(%d,%d) cart=(%d,%d) type=%s]",
                districtName, districtQ, districtR, localQ, localR, x, z, type);
    }
}
