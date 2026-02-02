package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
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

        // TODO: This connector needs to be reimplemented after Point composition is complete
        // RoadConnection and RiverConnection now use Point IDs instead of grid coordinates
        // Need to:
        // 1. Resolve Point IDs to get their HexLocalPosition or HexLocalSideCoordinate
        // 2. Extract grid coordinates and side information from Points
        // 3. Apply connections to the grids as before
        //
        // For now, skip the connections to allow compilation
        log.warn("RoadAndRiverConnector is temporarily disabled - needs Point composition data");

        // Collect all grids without applying connections
        List<WHexGrid> allGrids = new ArrayList<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            allGrids.add(filled.getHexGrid());
        }

        return ConnectionResult.builder()
            .hexGrids(allGrids)
            .roadsApplied(0)
            .riversApplied(0)
            .success(true)
            .build();
    }

    /**
     * Applies a road connection to two grids
     * TODO: Reimplement after Point composition is complete
     */
    @Deprecated
    private boolean applyRoadConnection(RoadConnection road, Map<String, WHexGrid> gridMap) {
        // TODO: This method needs to be reimplemented to work with Point IDs
        // RoadConnection now has fromPointId and toPointId instead of grid coordinates
        // Need to resolve Points to extract grid coordinates and side information
        log.warn("applyRoadConnection is not yet implemented for Point-based connections");
        return false;
    }

    /**
     * Applies a river connection to two grids
     * TODO: Reimplement after Point composition is complete
     */
    @Deprecated
    private boolean applyRiverConnection(RiverConnection river, Map<String, WHexGrid> gridMap) {
        // TODO: This method needs to be reimplemented to work with Point IDs
        // RiverConnection now has fromPointId and toPointId instead of grid coordinates
        // Need to resolve Points to extract grid coordinates and side information
        log.warn("applyRiverConnection is not yet implemented for Point-based connections");
        return false;
    }

    /**
     * Adds road parameter to a WHexGrid
     */
    private void addRoadParameter(WHexGrid grid, EDGE side, int width, int level, String type) {
        Map<String, String> params = grid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            grid.setParameters(params);
        }

        // Get existing road configuration or create new one
        String roadJson = params.get("g_road");
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
        params.put("g_road", serializeRoadConfig(config));
    }

    /**
     * Adds river parameter to a WHexGrid
     */
    private void addRiverParameter(WHexGrid grid, EDGE side, int width, int depth, int level) {
        Map<String, String> params = grid.getParameters();
        if (params == null) {
            params = new HashMap<>();
            grid.setParameters(params);
        }

        // Get existing river configuration or create new one
        String riverJson = params.get("g_river");
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
        params.put("g_river", serializeRiverConfig(config));
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
    public static EDGE getOppositeSide(EDGE side) {
        return switch (side) {
            case NORTH_EAST -> EDGE.SOUTH_WEST;
            case EAST -> EDGE.WEST;
            case SOUTH_EAST -> EDGE.NORTH_WEST;
            case SOUTH_WEST -> EDGE.NORTH_EAST;
            case WEST -> EDGE.EAST;
            case NORTH_WEST -> EDGE.SOUTH_EAST;
        };
    }

    /**
     * Calculates neighbor grid coordinate based on direction
     */
    public static HexVector2 getNeighborCoordinate(HexVector2 coord, EDGE side) {
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
    public static EDGE determineSide(HexVector2 from, HexVector2 to) {
        int dq = to.getQ() - from.getQ();
        int dr = to.getR() - from.getR();

        if (dq == 1 && dr == -1) return EDGE.NORTH_EAST;
        if (dq == 1 && dr == 0) return EDGE.EAST;
        if (dq == 0 && dr == 1) return EDGE.SOUTH_EAST;
        if (dq == -1 && dr == 1) return EDGE.SOUTH_WEST;
        if (dq == -1 && dr == 0) return EDGE.WEST;
        if (dq == 0 && dr == -1) return EDGE.NORTH_WEST;

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
