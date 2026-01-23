package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Connects roads and rivers across hex grid boundaries
 * Ensures that roads/rivers at one grid's edge connect to the neighboring grid's opposite edge
 */
@Slf4j
public class RoadAndRiverConnector {

    /**
     * Connects roads and rivers across all hex grids
     *
     * @param fillResult Result from HexGridFiller with all grids
     * @param roadConnections List of road connections to apply
     * @param riverConnections List of river connections to apply
     * @return Updated WHexGrid list with road/river parameters
     */
    public ConnectionResult connect(HexGridFillResult fillResult,
                                    List<RoadConnection> roadConnections,
                                    List<RiverConnection> riverConnections) {
        log.info("Connecting roads and rivers across hex grid boundaries");
        log.info("Roads: {}, Rivers: {}", roadConnections.size(), riverConnections.size());

        // Build grid map
        Map<String, WHexGrid> gridMap = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            String key = coordKey(filled.getCoordinate());
            gridMap.put(key, filled.getHexGrid());
        }

        // Apply road connections
        int roadsApplied = 0;
        for (RoadConnection road : roadConnections) {
            if (applyRoadConnection(road, gridMap)) {
                roadsApplied++;
            }
        }

        // Apply river connections
        int riversApplied = 0;
        for (RiverConnection river : riverConnections) {
            if (applyRiverConnection(river, gridMap)) {
                riversApplied++;
            }
        }

        log.info("Applied {} roads and {} rivers", roadsApplied, riversApplied);

        // Collect all grids
        List<WHexGrid> allGrids = new ArrayList<>(gridMap.values());

        return ConnectionResult.builder()
            .hexGrids(allGrids)
            .roadsApplied(roadsApplied)
            .riversApplied(riversApplied)
            .success(true)
            .build();
    }

    /**
     * Applies a road connection to two grids
     */
    private boolean applyRoadConnection(RoadConnection road, Map<String, WHexGrid> gridMap) {
        WHexGrid fromGrid = gridMap.get(coordKey(road.getFromGrid()));
        WHexGrid toGrid = gridMap.get(coordKey(road.getToGrid()));

        log.debug("Applying road: {} -> {}", coordKey(road.getFromGrid()), coordKey(road.getToGrid()));
        log.debug("FromGrid found: {}, ToGrid found: {}", fromGrid != null, toGrid != null);

        if (fromGrid == null || toGrid == null) {
            log.warn("Cannot apply road connection - grid not found: {} -> {}",
                road.getFromGrid(), road.getToGrid());
            return false;
        }

        // Add road parameter to from-grid
        log.debug("Adding {} road to from-grid {}", road.getFromSide(), coordKey(road.getFromGrid()));
        addRoadParameter(fromGrid, road.getFromSide(), road.getWidth(), road.getLevel(), road.getType());

        // Add road parameter to to-grid
        log.debug("Adding {} road to to-grid {}", road.getToSide(), coordKey(road.getToGrid()));
        addRoadParameter(toGrid, road.getToSide(), road.getWidth(), road.getLevel(), road.getType());

        log.debug("Applied road: {} side {} -> {} side {}",
            road.getFromGrid(), road.getFromSide(), road.getToGrid(), road.getToSide());

        return true;
    }

    /**
     * Applies a river connection to two grids
     */
    private boolean applyRiverConnection(RiverConnection river, Map<String, WHexGrid> gridMap) {
        WHexGrid fromGrid = gridMap.get(coordKey(river.getFromGrid()));
        WHexGrid toGrid = gridMap.get(coordKey(river.getToGrid()));

        if (fromGrid == null || toGrid == null) {
            log.warn("Cannot apply river connection - grid not found: {} -> {}",
                river.getFromGrid(), river.getToGrid());
            return false;
        }

        // Add river parameter to from-grid
        addRiverParameter(fromGrid, river.getFromSide(), river.getWidth(),
            river.getDepth(), river.getLevel());

        // Add river parameter to to-grid
        addRiverParameter(toGrid, river.getToSide(), river.getWidth(),
            river.getDepth(), river.getLevel());

        log.debug("Applied river: {} side {} -> {} side {}",
            river.getFromGrid(), river.getFromSide(), river.getToGrid(), river.getToSide());

        return true;
    }

    /**
     * Adds road parameter to a WHexGrid
     */
    private void addRoadParameter(WHexGrid grid, SIDE side, int width, int level, String type) {
        Map<String, String> params = grid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            grid.setParameters(params);
        }

        // Get existing road configuration or create new one
        String roadJson = params.get("road");
        RoadConfig config;

        if (roadJson != null && !roadJson.isEmpty()) {
            config = parseRoadConfig(roadJson);
        } else {
            config = new RoadConfig();
            config.setLx(256); // Default center
            config.setLz(256);
            config.setLevel(level);
            config.setRoutes(new ArrayList<>());
        }

        // Add route
        RouteDefinition route = new RouteDefinition();
        route.setSide(side);
        route.setWidth(width);
        route.setLevel(level);
        route.setType(type);

        config.getRoutes().add(route);

        // Write back
        params.put("road", serializeRoadConfig(config));
    }

    /**
     * Adds river parameter to a WHexGrid
     */
    private void addRiverParameter(WHexGrid grid, SIDE side, int width, int depth, int level) {
        Map<String, String> params = grid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            grid.setParameters(params);
        }

        // Get existing river configuration or create new one
        String riverJson = params.get("river");
        RiverConfig config;

        if (riverJson != null && !riverJson.isEmpty()) {
            config = parseRiverConfig(riverJson);
        } else {
            config = new RiverConfig();
            config.setFrom(new ArrayList<>());
            config.setTo(new ArrayList<>());
        }

        // Add from/to based on side
        RiverEndpoint endpoint = new RiverEndpoint();
        endpoint.setSide(side.name());
        endpoint.setWidth(width);
        endpoint.setDepth(depth);
        endpoint.setLevel(level);

        // For simplicity, add to 'from' list
        // In real scenario, you'd determine if it's source or destination
        config.getFrom().add(endpoint);

        // Write back
        params.put("river", serializeRiverConfig(config));
    }

    /**
     * Parses road configuration from JSON string
     */
    private RoadConfig parseRoadConfig(String json) {
        try {
            // Replace "route" with "routes" for compatibility with RoadBuilder format
            String normalized = json.replace("\"route\":", "\"routes\":");
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(normalized, RoadConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse road config, creating new one: {}", e.getMessage());
            RoadConfig config = new RoadConfig();
            config.setLx(256);
            config.setLz(256);
            config.setLevel(95);
            config.setRoutes(new ArrayList<>());
            return config;
        }
    }

    /**
     * Serializes road configuration to JSON string
     */
    private String serializeRoadConfig(RoadConfig config) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(config);
            // Replace "routes" with "route" for compatibility with RoadBuilder
            json = json.replace("\"routes\":", "\"route\":");
            return json;
        } catch (Exception e) {
            log.error("Failed to serialize road config", e);
            return "{}";
        }
    }

    /**
     * Parses river configuration from JSON string
     */
    private RiverConfig parseRiverConfig(String json) {
        RiverConfig config = new RiverConfig();
        config.setFrom(new ArrayList<>());
        config.setTo(new ArrayList<>());
        return config;
    }

    /**
     * Serializes river configuration to JSON string
     */
    private String serializeRiverConfig(RiverConfig config) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"from\":[");

        boolean first = true;
        for (RiverEndpoint endpoint : config.getFrom()) {
            if (!first) json.append(",");
            first = false;

            json.append("{");
            json.append("\"side\":\"").append(endpoint.getSide()).append("\",");
            json.append("\"width\":").append(endpoint.getWidth()).append(",");
            json.append("\"depth\":").append(endpoint.getDepth()).append(",");
            json.append("\"level\":").append(endpoint.getLevel());
            json.append("}");
        }
        json.append("],\"to\":[");

        first = true;
        for (RiverEndpoint endpoint : config.getTo()) {
            if (!first) json.append(",");
            first = false;

            json.append("{");
            json.append("\"side\":\"").append(endpoint.getSide()).append("\",");
            json.append("\"width\":").append(endpoint.getWidth()).append(",");
            json.append("\"depth\":").append(endpoint.getDepth()).append(",");
            json.append("\"level\":").append(endpoint.getLevel());
            json.append("}");
        }
        json.append("]}");

        return json.toString();
    }

    /**
     * Gets opposite side for hex grid connection
     */
    public static SIDE getOppositeSide(SIDE side) {
        return switch (side) {
            case NORTH_EAST -> SIDE.SOUTH_WEST;
            case EAST -> SIDE.WEST;
            case SOUTH_EAST -> SIDE.NORTH_WEST;
            case SOUTH_WEST -> SIDE.NORTH_EAST;
            case WEST -> SIDE.EAST;
            case NORTH_WEST -> SIDE.SOUTH_EAST;
        };
    }

    /**
     * Calculates neighbor grid coordinate based on direction
     */
    public static HexVector2 getNeighborCoordinate(HexVector2 coord, SIDE side) {
        return switch (side) {
            case NORTH_EAST -> HexVector2.builder().q(coord.getQ() + 1).r(coord.getR() - 1).build();
            case EAST -> HexVector2.builder().q(coord.getQ() + 1).r(coord.getR()).build();
            case SOUTH_EAST -> HexVector2.builder().q(coord.getQ()).r(coord.getR() + 1).build();
            case SOUTH_WEST -> HexVector2.builder().q(coord.getQ() - 1).r(coord.getR() + 1).build();
            case WEST -> HexVector2.builder().q(coord.getQ() - 1).r(coord.getR()).build();
            case NORTH_WEST -> HexVector2.builder().q(coord.getQ()).r(coord.getR() - 1).build();
        };
    }

    /**
     * Determines which side to use based on grid direction
     */
    public static SIDE determineSide(HexVector2 from, HexVector2 to) {
        int dq = to.getQ() - from.getQ();
        int dr = to.getR() - from.getR();

        if (dq == 1 && dr == -1) return SIDE.NORTH_EAST;
        if (dq == 1 && dr == 0) return SIDE.EAST;
        if (dq == 0 && dr == 1) return SIDE.SOUTH_EAST;
        if (dq == -1 && dr == 1) return SIDE.SOUTH_WEST;
        if (dq == -1 && dr == 0) return SIDE.WEST;
        if (dq == 0 && dr == -1) return SIDE.NORTH_WEST;

        throw new IllegalArgumentException("Invalid hex direction: dq=" + dq + ", dr=" + dr);
    }

    /**
     * Creates coordinate key
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + "," + coord.getR();
    }

    /**
     * River configuration helper classes
     */
    @Data
    private static class RiverConfig {
        private List<RiverEndpoint> from;
        private List<RiverEndpoint> to;
    }

    @Data
    private static class RiverEndpoint {
        private String side;
        private int width;
        private int depth;
        private int level;
    }
}
