package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Builder;
import lombok.Data;

/**
 * A part of a road configuration that will be assembled into final road={} JSON.
 * Different sources (Villages, Flows, etc.) can add parts that are merged later.
 */
@Data
@Builder
public class RoadConfigPart {

    /**
     * Type of road part
     */
    public enum PartType {
        CENTER,    // Center/plaza configuration (lx, lz, level, plazaSize, plazaMaterial)
        ROUTE      // Route entry (side or lx/lz, width, level, type)
    }

    private PartType partType;

    // CENTER fields
    private Integer centerLx;
    private Integer centerLz;
    private Integer centerLevel;
    private Integer plazaSize;
    private String plazaMaterial;

    // ROUTE fields - either side-based OR position-based
    private SIDE side;           // Side-based route (NE, NW, etc.)
    private Integer routeLx;     // Position-based route x
    private Integer routeLz;     // Position-based route z
    private Integer width;
    private Integer level;
    private String type;         // "street", "trail", etc.

    /**
     * Creates a CENTER part for plaza configuration
     */
    public static RoadConfigPart createCenterPart(Integer lx, Integer lz, Integer level,
                                                   Integer plazaSize, String plazaMaterial) {
        return RoadConfigPart.builder()
            .partType(PartType.CENTER)
            .centerLx(lx)
            .centerLz(lz)
            .centerLevel(level)
            .plazaSize(plazaSize)
            .plazaMaterial(plazaMaterial)
            .build();
    }

    /**
     * Creates a ROUTE part for side-based routing (from Flow)
     */
    public static RoadConfigPart createRouteSidePart(SIDE side, Integer width, Integer level, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .side(side)
            .width(width)
            .level(level)
            .type(type)
            .build();
    }

    /**
     * Creates a ROUTE part for position-based routing (from Village)
     */
    public static RoadConfigPart createRoutePositionPart(Integer lx, Integer lz, Integer width,
                                                          Integer level, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .routeLx(lx)
            .routeLz(lz)
            .width(width)
            .level(level)
            .type(type)
            .build();
    }
}
