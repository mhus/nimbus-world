package de.mhus.nimbus.world.generator.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
import lombok.Builder;
import lombok.Data;

/**
 * A part of a river configuration that will be assembled into final river={} JSON.
 * Rivers flow from entry sides (from) to exit sides (to).
 */
@Data
@Builder
public class RiverConfigPart {

    /**
     * Type of river part
     */
    public enum PartType {
        FROM,      // Entry point (from side)
        TO         // Exit point (to side)
    }

    private PartType partType;

    // Common fields - either side-based OR position-based OR position-string-based
    private EDGE side;           // Side-based routing (NE, NW, etc.) - DEPRECATED, use position instead
    private String position;     // HexLocal position string (e.g., "<NE 2/4>") for grid-to-grid transitions
    private Integer lx;          // Position-based routing x (for Point endpoints)
    private Integer lz;          // Position-based routing z (for Point endpoints)
    private Integer width;
    private Integer depth;
    private Integer level;

    // Group ID for river merging
    private String groupId;

    /**
     * Creates a FROM part for river entry
     */
    public static RiverConfigPart createFromPart(EDGE side, Integer width, Integer depth,
                                                 Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.FROM)
            .side(side)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }

    /**
     * Creates a TO part for river exit
     */
    public static RiverConfigPart createToPart(EDGE side, Integer width, Integer depth,
                                               Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.TO)
            .side(side)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }

    /**
     * Creates a FROM part for river entry with position coordinates
     */
    public static RiverConfigPart createFromPositionPart(Integer lx, Integer lz, Integer width,
                                                          Integer depth, Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.FROM)
            .lx(lx)
            .lz(lz)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }

    /**
     * Creates a TO part for river exit with position coordinates
     */
    public static RiverConfigPart createToPositionPart(Integer lx, Integer lz, Integer width,
                                                        Integer depth, Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.TO)
            .lx(lx)
            .lz(lz)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }

    /**
     * Creates a FROM part for river entry with HexLocal position string
     */
    public static RiverConfigPart createFromPositionStringPart(String position, Integer width,
                                                                 Integer depth, Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.FROM)
            .position(position)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }

    /**
     * Creates a TO part for river exit with HexLocal position string
     */
    public static RiverConfigPart createToPositionStringPart(String position, Integer width,
                                                               Integer depth, Integer level, String groupId) {
        return RiverConfigPart.builder()
            .partType(PartType.TO)
            .position(position)
            .width(width)
            .depth(depth)
            .level(level)
            .groupId(groupId)
            .build();
    }
}
