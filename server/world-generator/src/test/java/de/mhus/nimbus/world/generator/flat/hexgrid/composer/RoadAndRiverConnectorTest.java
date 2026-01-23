package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RoadAndRiverConnector
 */
@Slf4j
public class RoadAndRiverConnectorTest {

    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        outputDir = Paths.get("target/test-output/road-river-connector");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testSimpleRoadConnection() {
        log.info("=== Testing Simple Road Connection ==");

        // Create 3 grids in a line: [0,0] -> [1,0] -> [2,0]
        HexGridFillResult fillResult = createSimpleGridLine();

        // Define road connections
        List<RoadConnection> roadConnections = new ArrayList<>();

        // Road from [0,0] EAST to [1,0] WEST
        roadConnections.add(RoadConnection.builder()
            .fromGrid(HexVector2.builder().q(0).r(0).build())
            .toGrid(HexVector2.builder().q(1).r(0).build())
            .fromSide(SIDE.EAST)
            .toSide(SIDE.WEST)
            .width(4)
            .level(95)
            .type("street")
            .groupId("road-1")
            .build());

        // Road from [1,0] EAST to [2,0] WEST
        roadConnections.add(RoadConnection.builder()
            .fromGrid(HexVector2.builder().q(1).r(0).build())
            .toGrid(HexVector2.builder().q(2).r(0).build())
            .fromSide(SIDE.EAST)
            .toSide(SIDE.WEST)
            .width(4)
            .level(95)
            .type("street")
            .groupId("road-1")
            .build());

        // Apply connections
        RoadAndRiverConnector connector = new RoadAndRiverConnector();
        ConnectionResult result = connector.connect(fillResult, roadConnections, new ArrayList<>());

        // Verify
        assertTrue(result.isSuccess());
        assertEquals(2, result.getRoadsApplied());
        assertEquals(0, result.getRiversApplied());

        // Check grid [0,0] has road parameter with EAST side
        WHexGrid grid00 = findGrid(result.getHexGrids(), 0, 0);
        assertNotNull(grid00);
        String roadParam = grid00.getParameters().get("road");
        assertNotNull(roadParam);
        assertTrue(roadParam.contains("EAST"), "Grid [0,0] should have EAST road");
        log.info("Grid [0,0] road: {}", roadParam);

        // Check grid [1,0] has road parameters with WEST and EAST sides
        WHexGrid grid10 = findGrid(result.getHexGrids(), 1, 0);
        assertNotNull(grid10, "Grid [1,0] should exist");
        roadParam = grid10.getParameters() != null ? grid10.getParameters().get("road") : null;
        assertNotNull(roadParam, "Grid [1,0] should have road parameters");
        assertTrue(roadParam.contains("WEST"), "Grid [1,0] should have WEST road");
        assertTrue(roadParam.contains("EAST"), "Grid [1,0] should have EAST road");
        log.info("Grid [1,0] road: {}", roadParam);

        // Check grid [2,0] has road parameter with WEST side
        WHexGrid grid20 = findGrid(result.getHexGrids(), 2, 0);
        assertNotNull(grid20);
        roadParam = grid20.getParameters().get("road");
        assertNotNull(roadParam);
        assertTrue(roadParam.contains("WEST"), "Grid [2,0] should have WEST road");
        log.info("Grid [2,0] road: {}", roadParam);
    }

    @Test
    public void testRiverConnection() {
        log.info("=== Testing River Connection ===");

        // Create 2 grids: [0,0] -> [0,1]
        HexGridFillResult fillResult = createSimpleGridLine();

        // Define river connection from [0,0] SOUTH_EAST to [0,1] NORTH_WEST
        List<RiverConnection> riverConnections = new ArrayList<>();
        riverConnections.add(RiverConnection.builder()
            .fromGrid(HexVector2.builder().q(0).r(0).build())
            .toGrid(HexVector2.builder().q(0).r(1).build())
            .fromSide(SIDE.SOUTH_EAST)
            .toSide(SIDE.NORTH_WEST)
            .width(5)
            .depth(2)
            .level(45)
            .groupId("river-1")
            .build());

        // Apply connections
        RoadAndRiverConnector connector = new RoadAndRiverConnector();
        ConnectionResult result = connector.connect(fillResult, new ArrayList<>(), riverConnections);

        // Verify
        assertTrue(result.isSuccess());
        assertEquals(0, result.getRoadsApplied());
        assertEquals(1, result.getRiversApplied());

        // Check grids have river parameters
        WHexGrid grid00 = findGrid(result.getHexGrids(), 0, 0);
        assertNotNull(grid00);
        String riverParam = grid00.getParameters().get("river");
        assertNotNull(riverParam);
        assertTrue(riverParam.contains("SOUTH_EAST"));
        log.info("Grid [0,0] river: {}", riverParam);

        WHexGrid grid01 = findGrid(result.getHexGrids(), 0, 1);
        assertNotNull(grid01);
        riverParam = grid01.getParameters().get("river");
        assertNotNull(riverParam);
        assertTrue(riverParam.contains("NORTH_WEST"));
        log.info("Grid [0,1] river: {}", riverParam);
    }

    @Test
    public void testOppositeSideCalculation() {
        log.info("=== Testing Opposite Side Calculation ===");

        assertEquals(SIDE.SOUTH_WEST, RoadAndRiverConnector.getOppositeSide(SIDE.NORTH_EAST));
        assertEquals(SIDE.WEST, RoadAndRiverConnector.getOppositeSide(SIDE.EAST));
        assertEquals(SIDE.NORTH_WEST, RoadAndRiverConnector.getOppositeSide(SIDE.SOUTH_EAST));
        assertEquals(SIDE.NORTH_EAST, RoadAndRiverConnector.getOppositeSide(SIDE.SOUTH_WEST));
        assertEquals(SIDE.EAST, RoadAndRiverConnector.getOppositeSide(SIDE.WEST));
        assertEquals(SIDE.SOUTH_EAST, RoadAndRiverConnector.getOppositeSide(SIDE.NORTH_WEST));

        log.info("All opposite side calculations correct");
    }

    @Test
    public void testNeighborCoordinateCalculation() {
        log.info("=== Testing Neighbor Coordinate Calculation ===");

        HexVector2 center = HexVector2.builder().q(0).r(0).build();

        HexVector2 ne = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.NORTH_EAST);
        assertEquals(1, ne.getQ());
        assertEquals(-1, ne.getR());

        HexVector2 e = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.EAST);
        assertEquals(1, e.getQ());
        assertEquals(0, e.getR());

        HexVector2 se = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.SOUTH_EAST);
        assertEquals(0, se.getQ());
        assertEquals(1, se.getR());

        HexVector2 sw = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.SOUTH_WEST);
        assertEquals(-1, sw.getQ());
        assertEquals(1, sw.getR());

        HexVector2 w = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.WEST);
        assertEquals(-1, w.getQ());
        assertEquals(0, w.getR());

        HexVector2 nw = RoadAndRiverConnector.getNeighborCoordinate(center, SIDE.NORTH_WEST);
        assertEquals(0, nw.getQ());
        assertEquals(-1, nw.getR());

        log.info("All neighbor coordinate calculations correct");
    }

    @Test
    public void testDetermineSide() {
        log.info("=== Testing Determine Side ===");

        HexVector2 center = HexVector2.builder().q(0).r(0).build();

        assertEquals(SIDE.NORTH_EAST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(1).r(-1).build()));
        assertEquals(SIDE.EAST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(1).r(0).build()));
        assertEquals(SIDE.SOUTH_EAST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(0).r(1).build()));
        assertEquals(SIDE.SOUTH_WEST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(-1).r(1).build()));
        assertEquals(SIDE.WEST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(-1).r(0).build()));
        assertEquals(SIDE.NORTH_WEST,
            RoadAndRiverConnector.determineSide(center, HexVector2.builder().q(0).r(-1).build()));

        log.info("All side determinations correct");
    }

    @Test
    public void testComplexRoadNetwork() {
        log.info("=== Testing Complex Road Network ===");

        // Create a cross-shaped grid:
        //       [0,-1]
        // [-1,0][0,0][1,0]
        //       [0,1]
        HexGridFillResult fillResult = createCrossGrid();

        List<RoadConnection> roadConnections = new ArrayList<>();

        // Create roads from center to all 4 directions
        // Center [0,0] NORTH_WEST to [0,-1] SOUTH_EAST
        roadConnections.add(createRoadConnection(
            HexVector2.builder().q(0).r(0).build(), SIDE.NORTH_WEST,
            HexVector2.builder().q(0).r(-1).build(), SIDE.SOUTH_EAST));

        // Center [0,0] WEST to [-1,0] EAST
        roadConnections.add(createRoadConnection(
            HexVector2.builder().q(0).r(0).build(), SIDE.WEST,
            HexVector2.builder().q(-1).r(0).build(), SIDE.EAST));

        // Center [0,0] EAST to [1,0] WEST
        roadConnections.add(createRoadConnection(
            HexVector2.builder().q(0).r(0).build(), SIDE.EAST,
            HexVector2.builder().q(1).r(0).build(), SIDE.WEST));

        // Center [0,0] SOUTH_EAST to [0,1] NORTH_WEST
        roadConnections.add(createRoadConnection(
            HexVector2.builder().q(0).r(0).build(), SIDE.SOUTH_EAST,
            HexVector2.builder().q(0).r(1).build(), SIDE.NORTH_WEST));

        // Apply connections
        RoadAndRiverConnector connector = new RoadAndRiverConnector();
        ConnectionResult result = connector.connect(fillResult, roadConnections, new ArrayList<>());

        // Verify
        assertTrue(result.isSuccess());
        assertEquals(4, result.getRoadsApplied());

        // Check center grid has 4 road routes
        WHexGrid centerGrid = findGrid(result.getHexGrids(), 0, 0);
        assertNotNull(centerGrid);
        String roadParam = centerGrid.getParameters().get("road");
        assertNotNull(roadParam);
        assertTrue(roadParam.contains("NORTH_WEST"));
        assertTrue(roadParam.contains("WEST"));
        assertTrue(roadParam.contains("EAST"));
        assertTrue(roadParam.contains("SOUTH_EAST"));
        log.info("Center grid road: {}", roadParam);

        // Check all outer grids have one road each
        WHexGrid nw = findGrid(result.getHexGrids(), 0, -1);
        assertNotNull(nw);
        assertTrue(nw.getParameters().get("road").contains("SOUTH_EAST"));

        WHexGrid w = findGrid(result.getHexGrids(), -1, 0);
        assertNotNull(w);
        assertTrue(w.getParameters().get("road").contains("EAST"));

        WHexGrid e = findGrid(result.getHexGrids(), 1, 0);
        assertNotNull(e);
        assertTrue(e.getParameters().get("road").contains("WEST"));

        WHexGrid se = findGrid(result.getHexGrids(), 0, 1);
        assertNotNull(se);
        assertTrue(se.getParameters().get("road").contains("NORTH_WEST"));

        log.info("Complex road network validated successfully");
    }

    /**
     * Creates a simple line of 3 grids for testing
     */
    private HexGridFillResult createSimpleGridLine() {
        List<FilledHexGrid> grids = new ArrayList<>();

        // Grid [0,0]
        grids.add(createFilledGrid(0, 0, "Grid-0-0"));

        // Grid [1,0]
        grids.add(createFilledGrid(1, 0, "Grid-1-0"));

        // Grid [2,0]
        grids.add(createFilledGrid(2, 0, "Grid-2-0"));

        // Grid [0,1]
        grids.add(createFilledGrid(0, 1, "Grid-0-1"));

        return HexGridFillResult.builder()
            .allGrids(grids)
            .success(true)
            .build();
    }

    /**
     * Creates a cross-shaped grid for testing
     */
    private HexGridFillResult createCrossGrid() {
        List<FilledHexGrid> grids = new ArrayList<>();

        // Center
        grids.add(createFilledGrid(0, 0, "Center"));

        // North-West
        grids.add(createFilledGrid(0, -1, "NW"));

        // West
        grids.add(createFilledGrid(-1, 0, "W"));

        // East
        grids.add(createFilledGrid(1, 0, "E"));

        // South-East
        grids.add(createFilledGrid(0, 1, "SE"));

        return HexGridFillResult.builder()
            .allGrids(grids)
            .success(true)
            .build();
    }

    /**
     * Creates a filled grid for testing
     */
    private FilledHexGrid createFilledGrid(int q, int r, String name) {
        HexVector2 coord = HexVector2.builder().q(q).r(r).build();

        WHexGrid hexGrid = WHexGrid.builder()
            .worldId("test-world")
            .position(q + ":" + r)
            .parameters(new HashMap<>())
            .enabled(true)
            .build();

        return FilledHexGrid.builder()
            .coordinate(coord)
            .hexGrid(hexGrid)
            .isFiller(false)
            .build();
    }

    /**
     * Creates a road connection helper
     */
    private RoadConnection createRoadConnection(HexVector2 from, SIDE fromSide,
                                                HexVector2 to, SIDE toSide) {
        return RoadConnection.builder()
            .fromGrid(from)
            .toGrid(to)
            .fromSide(fromSide)
            .toSide(toSide)
            .width(4)
            .level(95)
            .type("street")
            .build();
    }

    /**
     * Finds grid by coordinates
     */
    private WHexGrid findGrid(List<WHexGrid> grids, int q, int r) {
        String searchPos = q + ":" + r;
        return grids.stream()
            .filter(g -> g.getPosition().equals(searchPos))
            .findFirst()
            .orElse(null);
    }
}
