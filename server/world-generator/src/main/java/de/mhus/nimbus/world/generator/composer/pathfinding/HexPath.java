package de.mhus.nimbus.world.generator.composer.pathfinding;

import de.mhus.nimbus.world.generator.composer.village.DistrictGrid;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a path through hex coordinates in a village.
 * Result of A* pathfinding between two connection points.
 */
@Data
public class HexPath {
    /**
     * Ordered list of hex coordinates forming the path (from start to goal)
     */
    private final List<HexCoord> coords;

    /**
     * Total cost of this path
     */
    private final int totalCost;

    /**
     * Constructor.
     *
     * @param coords Ordered list of hex coordinates
     * @param totalCost Total path cost
     */
    public HexPath(List<HexCoord> coords, int totalCost) {
        this.coords = coords;
        this.totalCost = totalCost;
    }

    /**
     * Get the list of districts crossed by this path.
     *
     * @return List of unique districts in path order
     */
    public List<DistrictGrid> getCrossedDistricts() {
        List<DistrictGrid> districts = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (HexCoord coord : coords) {
            String districtName = coord.getDistrictName();
            if (!seen.contains(districtName)) {
                seen.add(districtName);
                districts.add(coord.getDistrict());
            }
        }

        return districts;
    }

    /**
     * Get the start coordinate of the path.
     *
     * @return Start coordinate
     */
    public HexCoord getStart() {
        return coords.isEmpty() ? null : coords.get(0);
    }

    /**
     * Get the goal coordinate of the path.
     *
     * @return Goal coordinate
     */
    public HexCoord getGoal() {
        return coords.isEmpty() ? null : coords.get(coords.size() - 1);
    }

    /**
     * Get the number of coordinates in the path.
     *
     * @return Path length
     */
    public int getLength() {
        return coords.size();
    }

    /**
     * Check if the path crosses district boundaries.
     *
     * @return True if path crosses between districts
     */
    public boolean crossesDistricts() {
        return getCrossedDistricts().size() > 1;
    }

    @Override
    public String toString() {
        return String.format("HexPath[length=%d cost=%d districts=%d start=%s goal=%s]",
                getLength(), totalCost, getCrossedDistricts().size(),
                getStart(), getGoal());
    }
}
