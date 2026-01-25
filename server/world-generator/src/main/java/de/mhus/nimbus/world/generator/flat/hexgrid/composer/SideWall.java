package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SideWall creates walls along specific sides of a biome or point.
 * Unlike regular Wall (which routes from A to B), SideWall decorates
 * the edges of an existing feature.
 *
 * Example: City wall around a city biome
 * - targetBiomeId: "gondor-city"
 * - sides: ["NE", "E", "SE"]
 * - distance: 5
 * - height: 10
 * - material: "stone"
 *
 * This generates sidewall parameter on biome edge grids:
 * sidewall={"sides": ["NE","E","SE"], "height": 5, "level": 50, "width": 3, "distance": 5, "minimum": 3, "type": 3}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SideWall extends Flow {

    /**
     * The biome or point that should be surrounded by the wall.
     * Can be a biome featureId or point featureId.
     */
    private String targetBiomeId;

    /**
     * Which sides of the target should have walls.
     * If null or empty, all sides will have walls.
     */
    private List<WHexGrid.SIDE> sides;

    /**
     * Distance from the biome edge where the wall should be placed.
     * Default: 5 blocks inward from edge.
     */
    private Integer distance;

    /**
     * Minimum height the wall should be above the current terrain.
     * Wall will be at least this many blocks higher than ground level.
     * Default: 0 (wall height is absolute, not relative).
     */
    private Integer minimum;

    /**
     * Wall height in blocks.
     * Default: 10
     */
    private Integer height;

    /**
     * Base level of the wall.
     * Default: 95
     */
    private Integer level;

    /**
     * Wall material type/name.
     * Default: "stone"
     */
    private String material;

    /**
     * Material type as integer (for FlatMaterialService).
     * Default: 3 (STONE)
     */
    private Integer materialType;

    /**
     * Whether the wall should be interrupted when crossing a road.
     * Default: false
     */
    private Boolean respectRoad;

    /**
     * Whether the wall should be interrupted when crossing a river.
     * Default: false
     */
    private Boolean respectRiver;

    public static SideWallBuilder builder() {
        return new SideWallBuilder();
    }

    /**
     * Applies sidewall-specific default configuration from FlowType.SIDEWALL
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (height == null && defaults.containsKey("default_height")) {
            height = Integer.parseInt(defaults.get("default_height"));
        }
        if (level == null && defaults.containsKey("default_level")) {
            level = Integer.parseInt(defaults.get("default_level"));
        }
        if (material == null && defaults.containsKey("default_material")) {
            material = defaults.get("default_material");
        }
        if (materialType == null && defaults.containsKey("default_type")) {
            materialType = Integer.parseInt(defaults.get("default_type"));
        }
        if (distance == null && defaults.containsKey("default_distance")) {
            distance = Integer.parseInt(defaults.get("default_distance"));
        }
        if (minimum == null && defaults.containsKey("default_minimum")) {
            minimum = Integer.parseInt(defaults.get("default_minimum"));
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
        if (respectRoad == null && defaults.containsKey("default_respectRoad")) {
            respectRoad = Boolean.parseBoolean(defaults.get("default_respectRoad"));
        }
        if (respectRiver == null && defaults.containsKey("default_respectRiver")) {
            respectRiver = Boolean.parseBoolean(defaults.get("default_respectRiver"));
        }
    }

    /**
     * Returns the effective distance value.
     * Priority: explicit distance > default (5)
     */
    public int getEffectiveDistance() {
        return distance != null ? distance : 5;
    }

    /**
     * Returns the effective minimum value.
     * Priority: explicit minimum > default (0)
     */
    public int getEffectiveMinimum() {
        return minimum != null ? minimum : 0;
    }

    /**
     * Returns the effective height value.
     * Priority: explicit height > default (10)
     */
    public int getEffectiveHeight() {
        return height != null ? height : 10;
    }

    /**
     * Returns the effective level value.
     * Priority: explicit level > default (95)
     */
    public int getEffectiveLevel() {
        return level != null ? level : 95;
    }

    /**
     * Returns the effective materialType value.
     * Priority: explicit materialType > default (3 = STONE)
     */
    public int getEffectiveMaterialType() {
        return materialType != null ? materialType : 3;
    }

    /**
     * Returns the effective material value.
     * Priority: explicit material > default ("stone")
     */
    public String getEffectiveMaterial() {
        return material != null ? material : "stone";
    }

    /**
     * Returns the effective respectRoad value.
     * Priority: explicit respectRoad > default (false)
     */
    public boolean isEffectiveRespectRoad() {
        return respectRoad != null ? respectRoad : false;
    }

    /**
     * Returns the effective respectRiver value.
     * Priority: explicit respectRiver > default (false)
     */
    public boolean isEffectiveRespectRiver() {
        return respectRiver != null ? respectRiver : false;
    }

    /**
     * SideWall does not configure its own grids.
     * Instead, it adds sidewall parameters to the target biome's edge grids.
     * This is handled by FlowComposer.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // SideWall doesn't create its own grids
        // It decorates existing biome grids
        // FlowComposer handles this in composeSideWall()
    }
}
