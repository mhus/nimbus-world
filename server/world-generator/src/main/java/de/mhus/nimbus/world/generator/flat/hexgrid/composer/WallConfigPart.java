package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Builder;
import lombok.Data;

/**
 * A part of a wall configuration that will be assembled into final wall={} JSON.
 * Walls can span sides or positions.
 */
@Data
@Builder
public class WallConfigPart {

    /**
     * Type of wall part
     */
    public enum PartType {
        SIDE,      // Side-based wall segment
        GATE       // Gate/opening in wall
    }

    private PartType partType;

    // SIDE fields
    private SIDE side;
    private Integer height;
    private Integer width;
    private Integer level;
    private String material;

    // GATE fields
    private SIDE gateSide;
    private Integer gatePosition;
    private Integer gateWidth;

    /**
     * Creates a SIDE part for wall segment
     */
    public static WallConfigPart createSidePart(SIDE side, Integer height, Integer width,
                                                  Integer level, String material) {
        return WallConfigPart.builder()
            .partType(PartType.SIDE)
            .side(side)
            .height(height)
            .width(width)
            .level(level)
            .material(material)
            .build();
    }

    /**
     * Creates a GATE part for opening in wall
     */
    public static WallConfigPart createGatePart(SIDE side, Integer position, Integer width) {
        return WallConfigPart.builder()
            .partType(PartType.GATE)
            .gateSide(side)
            .gatePosition(position)
            .gateWidth(width)
            .build();
    }
}
