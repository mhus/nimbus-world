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
