package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the multi-HexGrid layout for a village
 * Villages can span 1-7 HexGrids in various patterns (1x1, 2x1, 3x1, 5-cross, 7-hex)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VillageGridLayout {
    private VillageSize size;
    private List<HexVector2> gridPositions;
    private HexVector2 centerGrid;
    private Map<String, HexVector2> namedLocations;

    /**
     * Checks if a grid coordinate is part of this layout
     */
    public boolean containsGrid(HexVector2 grid) {
        return gridPositions.stream()
            .anyMatch(g -> g.getQ() == grid.getQ() && g.getR() == grid.getR());
    }

    /**
     * Converts local template coordinates to absolute HexGrid + local flat coordinates
     *
     * @param localX Local X in template coordinate system
     * @param localZ Local Z in template coordinate system
     * @param flatSize Size of a flat grid (typically 512)
     * @return Grid position and local coordinates within that grid
     */
    public GridLocalCoordinate toAbsolute(int localX, int localZ, int flatSize) {
        // Calculate which grid this position falls into
        int gridQ = localX / flatSize;
        int gridR = localZ / flatSize;

        // Calculate local position within that grid
        int flatX = localX % flatSize;
        int flatZ = localZ % flatSize;

        HexVector2 gridPos = HexVector2.builder()
            .q(gridQ)
            .r(gridR)
            .build();

        return GridLocalCoordinate.builder()
            .gridPosition(gridPos)
            .localX(flatX)
            .localZ(flatZ)
            .build();
    }

    /**
     * Creates a 1x1 hamlet layout (single grid)
     */
    public static VillageGridLayout createHamlet() {
        List<HexVector2> grids = new ArrayList<>();
        grids.add(HexVector2.builder().q(0).r(0).build());

        Map<String, HexVector2> locations = new HashMap<>();
        locations.put("center", HexVector2.builder().q(0).r(0).build());

        return VillageGridLayout.builder()
            .size(VillageSize.HAMLET)
            .gridPositions(grids)
            .centerGrid(HexVector2.builder().q(0).r(0).build())
            .namedLocations(locations)
            .build();
    }

    /**
     * Creates a 2x1 small village layout (2 horizontal grids)
     */
    public static VillageGridLayout create2x1() {
        List<HexVector2> grids = new ArrayList<>();
        grids.add(HexVector2.builder().q(0).r(0).build());
        grids.add(HexVector2.builder().q(1).r(0).build());

        Map<String, HexVector2> locations = new HashMap<>();
        locations.put("center", HexVector2.builder().q(0).r(0).build());

        return VillageGridLayout.builder()
            .size(VillageSize.SMALL_VILLAGE)
            .gridPositions(grids)
            .centerGrid(HexVector2.builder().q(0).r(0).build())
            .namedLocations(locations)
            .build();
    }

    /**
     * Creates a 3x1 village layout (3 horizontal grids)
     */
    public static VillageGridLayout create3x1() {
        List<HexVector2> grids = new ArrayList<>();
        grids.add(HexVector2.builder().q(0).r(0).build());
        grids.add(HexVector2.builder().q(1).r(0).build());
        grids.add(HexVector2.builder().q(2).r(0).build());

        Map<String, HexVector2> locations = new HashMap<>();
        locations.put("center", HexVector2.builder().q(1).r(0).build());

        return VillageGridLayout.builder()
            .size(VillageSize.VILLAGE)
            .gridPositions(grids)
            .centerGrid(HexVector2.builder().q(1).r(0).build())
            .namedLocations(locations)
            .build();
    }

    /**
     * Creates a 5-grid cross/plus layout for towns
     * Pattern:
     *        [1,0]
     * [0,0] [1,1] [2,0]
     *        [1,2]
     */
    public static VillageGridLayout create5Cross() {
        List<HexVector2> grids = new ArrayList<>();
        grids.add(HexVector2.builder().q(1).r(0).build()); // North
        grids.add(HexVector2.builder().q(0).r(0).build()); // West
        grids.add(HexVector2.builder().q(1).r(1).build()); // Center
        grids.add(HexVector2.builder().q(2).r(0).build()); // East
        grids.add(HexVector2.builder().q(1).r(2).build()); // South

        Map<String, HexVector2> locations = new HashMap<>();
        locations.put("center", HexVector2.builder().q(1).r(1).build());
        locations.put("plaza", HexVector2.builder().q(1).r(1).build());

        return VillageGridLayout.builder()
            .size(VillageSize.TOWN)
            .gridPositions(grids)
            .centerGrid(HexVector2.builder().q(1).r(1).build())
            .namedLocations(locations)
            .build();
    }

    /**
     * Creates a 7-grid hexagon layout for large towns
     * Pattern:
     *     [1,0] [2,0]
     * [0,1] [1,1] [2,1]
     *     [1,2]
     */
    public static VillageGridLayout create7Hex() {
        List<HexVector2> grids = new ArrayList<>();
        grids.add(HexVector2.builder().q(1).r(0).build()); // North-West
        grids.add(HexVector2.builder().q(2).r(0).build()); // North-East
        grids.add(HexVector2.builder().q(0).r(1).build()); // West
        grids.add(HexVector2.builder().q(1).r(1).build()); // Center
        grids.add(HexVector2.builder().q(2).r(1).build()); // East
        grids.add(HexVector2.builder().q(1).r(2).build()); // South
        grids.add(HexVector2.builder().q(0).r(2).build()); // South-West

        Map<String, HexVector2> locations = new HashMap<>();
        locations.put("center", HexVector2.builder().q(1).r(1).build());
        locations.put("plaza", HexVector2.builder().q(1).r(1).build());

        return VillageGridLayout.builder()
            .size(VillageSize.LARGE_TOWN)
            .gridPositions(grids)
            .centerGrid(HexVector2.builder().q(1).r(1).build())
            .namedLocations(locations)
            .build();
    }
}
