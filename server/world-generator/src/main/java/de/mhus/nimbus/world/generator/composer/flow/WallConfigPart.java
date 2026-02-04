package de.mhus.nimbus.world.generator.composer.flow;

import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
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

    // SIDE fields - either side-based OR position-based
    private EDGE side;           // Side-based wall (NE, NW, etc.)
    private String position;     // HexLocal position string (e.g., "<NE 2/4>" or "<256;256>")
    @Deprecated
    private Integer lx;          // Deprecated: use position instead
    @Deprecated
    private Integer lz;          // Deprecated: use position instead
    private Integer height;
    private Integer width;
    private Integer level;
    private String material;

    // GATE fields
    private EDGE gateSide;
    private Integer gatePosition;
    private Integer gateWidth;

    /**
     * Creates a SIDE part for wall segment
     */
    public static WallConfigPart createSidePart(EDGE side, Integer height, Integer width,
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
     * Creates a position-based wall segment part with HexLocal position string
     */
    public static WallConfigPart createPositionPart(String position, Integer height,
                                                     Integer width, Integer level, String material) {
        return WallConfigPart.builder()
            .partType(PartType.SIDE)
            .position(position)
            .height(height)
            .width(width)
            .level(level)
            .material(material)
            .build();
    }

    /**
     * Creates a position-based wall segment part (deprecated version using lx/lz)
     * @deprecated Use createPositionPart(String position, ...) instead
     */
    @Deprecated
    public static WallConfigPart createPositionPartDeprecated(Integer lx, Integer lz, Integer height,
                                                               Integer width, Integer level, String material) {
        return WallConfigPart.builder()
            .partType(PartType.SIDE)
            .lx(lx)
            .lz(lz)
            .height(height)
            .width(width)
            .level(level)
            .material(material)
            .build();
    }

    /**
     * Creates a GATE part for opening in wall
     */
    public static WallConfigPart createGatePart(EDGE side, Integer position, Integer width) {
        return WallConfigPart.builder()
            .partType(PartType.GATE)
            .gateSide(side)
            .gatePosition(position)
            .gateWidth(width)
            .build();
    }
}
