package de.mhus.nimbus.world.generator.composer.flow;

import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A part of a road configuration that will be assembled into final road={} JSON.
 * Different sources (Villages, Flows, etc.) can add parts that are merged later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    // ROUTE fields - either side-based, position-string-based, OR coordinate-based
    private EDGE side;           // Side-based route (NE, NW, etc.)
    private String position;     // HexLocal position string (e.g., "<NE 2>" or "<0;0>")
    private Integer routeLx;     // Coordinate-based route x (deprecated, use position)
    private Integer routeLz;     // Coordinate-based route z (deprecated, use position)
    private Integer width;
    private Integer level;       // Deprecated: use fromLevel/toLevel instead
    private Integer fromLevel;   // Level when entering this position
    private Integer toLevel;     // Level when exiting this position
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
    public static RoadConfigPart createRouteSidePart(EDGE side, Integer width, Integer level, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .side(side)
            .width(width)
            .level(level)
            .fromLevel(level)  // Backward compatibility
            .toLevel(level)
            .type(type)
            .build();
    }

    /**
     * Creates a ROUTE part for side-based routing with fromLevel/toLevel (from Flow)
     */
    public static RoadConfigPart createRouteSidePartWithLevels(EDGE side, Integer width,
                                                                Integer fromLevel, Integer toLevel, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .side(side)
            .width(width)
            .level(fromLevel)  // Backward compatibility: use fromLevel as default
            .fromLevel(fromLevel)
            .toLevel(toLevel)
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
            .fromLevel(level)  // Backward compatibility
            .toLevel(level)
            .type(type)
            .build();
    }

    /**
     * Creates a ROUTE part for position-based routing with fromLevel/toLevel (from Flow)
     */
    public static RoadConfigPart createRoutePositionPartWithLevels(Integer lx, Integer lz, Integer width,
                                                                    Integer fromLevel, Integer toLevel, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .routeLx(lx)
            .routeLz(lz)
            .width(width)
            .level(fromLevel)  // Backward compatibility: use fromLevel as default
            .fromLevel(fromLevel)
            .toLevel(toLevel)
            .type(type)
            .build();
    }

    /**
     * Creates a ROUTE part using a HexLocal position string with fromLevel/toLevel.
     * Used for Point endpoints where the position is already in HexLocal format.
     */
    public static RoadConfigPart createRoutePositionStringPartWithLevels(String position, Integer width,
                                                                          Integer fromLevel, Integer toLevel, String type) {
        return RoadConfigPart.builder()
            .partType(PartType.ROUTE)
            .position(position)
            .width(width)
            .level(fromLevel)  // Backward compatibility
            .fromLevel(fromLevel)
            .toLevel(toLevel)
            .type(type)
            .build();
    }
}
