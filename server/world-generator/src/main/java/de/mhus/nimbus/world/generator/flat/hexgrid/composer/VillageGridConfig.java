package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Village configuration for a single HexGrid.
 * This configuration is serialized to JSON and attached as g_village parameter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VillageGridConfig {

    /**
     * Village name
     */
    private String villageName;

    /**
     * Village style (e.g., "medieval", "modern")
     */
    private String style;

    /**
     * District name
     */
    private String districtName;

    /**
     * District title
     */
    private String districtTitle;

    /**
     * Base level for terrain
     */
    private int baseLevel;

    /**
     * Placed places in this district
     */
    private List<PlacedPlaceConfig> places;

    /**
     * Street segments in this district
     */
    private List<StreetSegmentConfig> streets;

    /**
     * Placed place configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlacedPlaceConfig {
        private String name;
        private String type; // "building", "free", "road", "river", "wall"
        private int hexQ; // Hexagonal Q coordinate
        private int hexR; // Hexagonal R coordinate
        private int localX; // Cartesian X (calculated from hex in VillageBuilder)
        private int localZ; // Cartesian Z (calculated from hex in VillageBuilder)
        private int rotation;
        private int divider; // Slot divider (1, 3, 5, or 7) - determines size
        private String buildingId;
        private String kind; // e.g., "house", "PARK", "STREET"
        private boolean oversized;
        private boolean connectionPoint;
    }

    /**
     * Street segment configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreetSegmentConfig {
        private int fromX;
        private int fromZ;
        private int toX;
        private int toZ;
        private int width;
        private String type;
        private int level;
    }
}
