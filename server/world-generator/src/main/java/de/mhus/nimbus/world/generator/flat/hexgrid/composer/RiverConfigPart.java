package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
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

    // Common fields
    private SIDE side;
    private Integer width;
    private Integer depth;
    private Integer level;

    // Group ID for river merging
    private String groupId;

    /**
     * Creates a FROM part for river entry
     */
    public static RiverConfigPart createFromPart(SIDE side, Integer width, Integer depth,
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
    public static RiverConfigPart createToPart(SIDE side, Integer width, Integer depth,
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
}
