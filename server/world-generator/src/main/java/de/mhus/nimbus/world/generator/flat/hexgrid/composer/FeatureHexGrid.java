package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.*;

import java.util.*;

/**
 * Lightweight configuration object storing HexGrid information within a Feature.
 * Does NOT contain the actual WHexGrid instance, only coordinates and parameters.
 * Used during composition stage to track which HexGrids should be created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureHexGrid {

    /**
     * Hex coordinate where this grid should be placed
     */
    private HexVector2 coordinate;

    /**
     * Configuration parameters for this HexGrid
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Area-specific data for this HexGrid
     */
    @Builder.Default
    private Map<String, Map<String, String>> areas = new HashMap<>();

    /**
     * Flow segments passing through this HexGrid (roads, rivers, walls).
     * Multiple flows can pass through the same grid.
     */
    @Builder.Default
    private List<FlowSegment> flowSegments = new ArrayList<>();

    /**
     * Road configuration parts that will be assembled into final road={} JSON.
     * Phase 1: Different sources (Villages, Flows) add parts
     * Phase 2: HexGridRoadConfigurator assembles all parts into road={}
     */
    @Builder.Default
    private List<RoadConfigPart> roadConfigParts = new ArrayList<>();

    /**
     * River configuration parts that will be assembled into final river={} JSON.
     * Phase 1: Different sources (Flows) add parts
     * Phase 2: HexGridRoadConfigurator assembles all parts into river={}
     */
    @Builder.Default
    private List<RiverConfigPart> riverConfigParts = new ArrayList<>();

    /**
     * Wall configuration parts that will be assembled into final wall={} JSON.
     * Phase 1: Different sources (Flows, Structures) add parts
     * Phase 2: HexGridRoadConfigurator assembles all parts into wall={}
     */
    @Builder.Default
    private List<WallConfigPart> wallConfigParts = new ArrayList<>();

    /**
     * Optional name for this HexGrid
     */
    private String name;

    /**
     * Optional description for this HexGrid
     */
    private String description;

    /**
     * Adds or updates a parameter
     */
    public void addParameter(String key, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }

    /**
     * Adds area-specific data
     */
    public void addAreaData(String areaKey, Map<String, String> data) {
        if (areas == null) {
            areas = new HashMap<>();
        }
        areas.put(areaKey, data);
    }

    /**
     * Adds a road configuration part to this HexGrid
     */
    public void addRoadConfigPart(RoadConfigPart part) {
        if (roadConfigParts == null) {
            roadConfigParts = new ArrayList<>();
        }
        roadConfigParts.add(part);
    }

    /**
     * Checks if this HexGrid has any road configuration parts
     */
    public boolean hasRoadConfigParts() {
        return roadConfigParts != null && !roadConfigParts.isEmpty();
    }

    /**
     * Adds a river configuration part to this HexGrid
     */
    public void addRiverConfigPart(RiverConfigPart part) {
        if (riverConfigParts == null) {
            riverConfigParts = new ArrayList<>();
        }
        riverConfigParts.add(part);
    }

    /**
     * Checks if this HexGrid has any river configuration parts
     */
    public boolean hasRiverConfigParts() {
        return riverConfigParts != null && !riverConfigParts.isEmpty();
    }

    /**
     * Adds a wall configuration part to this HexGrid
     */
    public void addWallConfigPart(WallConfigPart part) {
        if (wallConfigParts == null) {
            wallConfigParts = new ArrayList<>();
        }
        wallConfigParts.add(part);
    }

    /**
     * Checks if this HexGrid has any wall configuration parts
     */
    public boolean hasWallConfigParts() {
        return wallConfigParts != null && !wallConfigParts.isEmpty();
    }

    /**
     * Adds a flow segment to this HexGrid
     */
    public void addFlowSegment(FlowSegment segment) {
        if (flowSegments == null) {
            flowSegments = new ArrayList<>();
        }
        flowSegments.add(segment);
    }

    /**
     * Returns all flow segments for this HexGrid
     */
    public List<FlowSegment> getFlowSegments() {
        return flowSegments != null ? flowSegments : new ArrayList<>();
    }

    /**
     * Returns flow segments of a specific type
     */
    public List<FlowSegment> getFlowSegmentsByType(FlowType type) {
        if (flowSegments == null) return new ArrayList<>();
        return flowSegments.stream()
            .filter(s -> s.getFlowType() == type)
            .toList();
    }

    /**
     * Returns true if this grid has any flow segments
     */
    public boolean hasFlowSegments() {
        return flowSegments != null && !flowSegments.isEmpty();
    }

    /**
     * Returns position key in format "q:r" for database lookup
     */
    public String getPositionKey() {
        if (coordinate == null) return null;
        return coordinate.getQ() + ":" + coordinate.getR();
    }
}
