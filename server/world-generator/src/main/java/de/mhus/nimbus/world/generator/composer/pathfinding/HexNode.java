package de.mhus.nimbus.world.generator.composer.pathfinding;

import de.mhus.nimbus.world.generator.composer.town.DistrictGrid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a hex cell node in the village pathfinding graph.
 * Used for A* pathfinding between connection points.
 */
@Data
public class HexNode {
    /**
     * Axial coordinate Q (column)
     */
    private final int q;

    /**
     * Axial coordinate R (row)
     */
    private final int r;

    /**
     * Cartesian X coordinate in local hex grid space
     */
    private final int localX;

    /**
     * Cartesian Z coordinate in local hex grid space
     */
    private final int localZ;

    /**
     * District this node belongs to
     */
    private final DistrictGrid district;

    /**
     * Node type classification
     */
    private HexNodeType type;

    /**
     * Whether this node is walkable (streets can pass through)
     */
    private boolean walkable;

    /**
     * Edge side if this is an edge node
     */
    private EdgeSide edgeSide;

    /**
     * Neighboring nodes (up to 6 for flat-top hexagons)
     */
    private final List<HexNode> neighbors = new ArrayList<>(6);

    /**
     * Connection point name if this is a connection point node
     */
    private String connectionPointName;

    /**
     * Linked edge node on the opposite side of a district boundary
     */
    private HexNode linkedEdgeNode;

    /**
     * Constructor for regular hex node.
     *
     * @param q Axial Q coordinate
     * @param r Axial R coordinate
     * @param localX Cartesian X coordinate in local space
     * @param localZ Cartesian Z coordinate in local space
     * @param district Owning district
     */
    public HexNode(int q, int r, int localX, int localZ, DistrictGrid district) {
        this.q = q;
        this.r = r;
        this.localX = localX;
        this.localZ = localZ;
        this.district = district;
        this.type = HexNodeType.EMPTY;
        this.walkable = true;
    }

    /**
     * Get unique key for this node (for HashMap storage).
     *
     * @return Unique key string
     */
    public String getKey() {
        return district.getName() + ":" + q + "," + r;
    }

    /**
     * Add a neighbor to this node.
     *
     * @param neighbor Neighboring hex node
     */
    public void addNeighbor(HexNode neighbor) {
        if (!neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
        }
    }

    /**
     * Mark this node as occupied (not walkable).
     */
    public void markOccupied() {
        this.type = HexNodeType.OCCUPIED;
        this.walkable = false;
    }

    /**
     * Mark this node as a connection point.
     *
     * @param pointName Name of the connection point
     */
    public void markConnectionPoint(String pointName) {
        this.type = HexNodeType.CONNECTION_POINT;
        this.walkable = true;
        this.connectionPointName = pointName;
    }

    /**
     * Mark this node as an edge node.
     *
     * @param side The edge side this node is on
     */
    public void markEdge(EdgeSide side) {
        this.type = HexNodeType.EDGE;
        this.walkable = true;
        this.edgeSide = side;
    }

    /**
     * Link this edge node to its counterpart on the opposite district boundary.
     *
     * @param oppositeNode The edge node on the opposite side
     */
    public void linkToOppositeEdge(HexNode oppositeNode) {
        this.linkedEdgeNode = oppositeNode;
        // Bidirectional link
        if (oppositeNode.linkedEdgeNode != this) {
            oppositeNode.linkedEdgeNode = this;
        }
    }

    @Override
    public String toString() {
        return String.format("HexNode[%s:(%d,%d) type=%s walkable=%s]",
                district.getName(), q, r, type, walkable);
    }
}
