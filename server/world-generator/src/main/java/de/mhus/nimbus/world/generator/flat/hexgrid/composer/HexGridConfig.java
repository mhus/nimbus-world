package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single HexGrid
 * Generates village={...} and road={...} parameter strings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class HexGridConfig {
    private HexVector2 gridPosition;
    private int baseLevel;

    // VillageBuilder configuration
    @Builder.Default
    private List<VillagePlotDefinition> plots = new ArrayList<>();

    @Builder.Default
    private List<VillageRoadDefinition> internalRoads = new ArrayList<>();

    // RoadBuilder configuration (for Plaza)
    private RoadConfig roadConfig;

    // Boundary roads (for Grid-Übergänge)
    @Builder.Default
    private List<BoundaryRoadDefinition> boundaryRoads = new ArrayList<>();

    /**
     * Adds a plot to this grid
     */
    public void addPlot(VillagePlotDefinition plot) {
        if (plots == null) {
            plots = new ArrayList<>();
        }
        plots.add(plot);
    }

    /**
     * Adds an internal road to this grid
     */
    public void addInternalRoad(VillageRoadDefinition road) {
        if (internalRoads == null) {
            internalRoads = new ArrayList<>();
        }
        internalRoads.add(road);
    }

    /**
     * Adds a boundary road to this grid
     */
    public void addBoundaryRoad(BoundaryRoadDefinition road) {
        if (boundaryRoads == null) {
            boundaryRoads = new ArrayList<>();
        }
        boundaryRoads.add(road);
    }

    /**
     * Generates village={...} parameter string for WHexGrid
     */
    public String toVillageParameter() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            root.put("level", baseLevel);
            root.put("material", 1); // GRASS

            // Add plots
            ArrayNode plotsArray = mapper.createArrayNode();
            for (VillagePlotDefinition plot : plots) {
                ObjectNode plotNode = mapper.createObjectNode();
                plotNode.put("lx", plot.getLx());
                plotNode.put("lz", plot.getLz());
                plotNode.put("level", plot.getLevel());
                plotNode.put("material", plot.getMaterial());

                // Shape: rectangular or circular
                if (plot.getSizeX() != null && plot.getSizeZ() != null) {
                    plotNode.put("sizeX", plot.getSizeX());
                    plotNode.put("sizeZ", plot.getSizeZ());
                } else if (plot.getSize() != null) {
                    plotNode.put("size", plot.getSize());
                }

                if (plot.getId() != null) {
                    plotNode.put("groupId", plot.getId());
                }

                if (plot.getRoad() != null) {
                    plotNode.put("g_road", plot.getRoad());
                }

                plotsArray.add(plotNode);
            }
            root.set("plots", plotsArray);

            // Add internal roads
            ArrayNode roadsArray = mapper.createArrayNode();
            for (VillageRoadDefinition road : internalRoads) {
                ObjectNode roadNode = mapper.createObjectNode();

                ObjectNode fromNode = mapper.createObjectNode();
                fromNode.put("lx", road.getFromX());
                fromNode.put("lz", road.getFromZ());
                fromNode.put("level", road.getLevel());
                roadNode.set("from", fromNode);

                ObjectNode toNode = mapper.createObjectNode();
                toNode.put("lx", road.getToX());
                toNode.put("lz", road.getToZ());
                toNode.put("level", road.getLevel());
                roadNode.set("to", toNode);

                roadNode.put("width", road.getWidth());
                roadNode.put("type", road.getType());

                roadsArray.add(roadNode);
            }
            root.set("roads", roadsArray);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to generate village parameter", e);
            throw new RuntimeException("Failed to generate village parameter", e);
        }
    }

    /**
     * Generates road={...} parameter string for WHexGrid
     */
    public String toRoadParameter() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            if (roadConfig == null) {
                // No road config - return empty JSON
                return "{}";
            }

            root.put("lx", roadConfig.getLx());
            root.put("lz", roadConfig.getLz());
            root.put("level", roadConfig.getLevel());

            if (roadConfig.getPlazaSize() != null && roadConfig.getPlazaSize() > 0) {
                root.put("plazaSize", roadConfig.getPlazaSize());
            }

            if (roadConfig.getPlazaMaterial() != null) {
                root.put("plazaMaterial", roadConfig.getPlazaMaterial());
            }

            // Add routes
            ArrayNode routesArray = mapper.createArrayNode();

            // Add routes from roadConfig
            if (roadConfig.getRoutes() != null) {
                for (RouteDefinition route : roadConfig.getRoutes()) {
                    ObjectNode routeNode = mapper.createObjectNode();

                    if (route.getSide() != null) {
                        routeNode.put("side", route.getSide().name());
                    } else if (route.getLx() != null && route.getLz() != null) {
                        routeNode.put("lx", route.getLx());
                        routeNode.put("lz", route.getLz());
                    }

                    routeNode.put("width", route.getWidth());
                    routeNode.put("level", route.getLevel());
                    routeNode.put("type", route.getType());

                    routesArray.add(routeNode);
                }
            }

            // Add boundary roads
            for (BoundaryRoadDefinition boundary : boundaryRoads) {
                ObjectNode routeNode = mapper.createObjectNode();
                routeNode.put("side", boundary.getSide().name());
                routeNode.put("width", boundary.getWidth());
                routeNode.put("level", boundary.getLevel());
                routeNode.put("type", boundary.getType());
                routesArray.add(routeNode);
            }

            root.set("route", routesArray);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to generate road parameter", e);
            throw new RuntimeException("Failed to generate road parameter", e);
        }
    }
}
