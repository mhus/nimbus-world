package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * SideBlender manipulator builder.
 * Blends sides of hex grids with their neighbors, using side flats.
 * This is a manipulator builder that can be applied after the main terrain generation.
 * Set g_side_flat_north_east, g_side_flat_east, g_side_flat_south_east,
 * g_side_flat_south_west, g_side_flat_west, g_side_flat_north_west to define side flat ids.
 * The flats will be loaded from WFlatService.
 * Set g_edge_blend_width to define the width of the blending area (default 20).
 * Set g_edge_blend_randomness to control random variations (default 0.5, range 0.0-1.0).
 *   - 0.0 = no random variation (smooth, uniform blending)
 *   - 0.5 = moderate variation (default, balanced)
 *   - 1.0 = full random variation (very organic, rough transitions)
 */
@Slf4j
public class SideBlenderBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Blending sides for flat: {}", flat.getFlatId());

        // Note: parameters come from HexGridBuilderService which already strips the "g_" prefix
        HashMap<WHexGrid.SIDE, String> sideFlats = new HashMap<>();
        for (var side : WHexGrid.SIDE.values()) {
            String key = "side_flat_" + side.name().toLowerCase();
            String flatId = parameters.get(key);
            if (flatId != null) {
                sideFlats.put(side, flatId);
                log.debug("Found side flat for {}: {}", side, flatId);
            }
        }

        int width = CastUtil.toint(parameters.get("edge_blend_width"), 20);
        double randomness = CastUtil.todouble(parameters.get("edge_blend_randomness"), 0.5);

        if (sideFlats.isEmpty()) {
            log.info("No side flats defined for blending in flat: {}", flat.getFlatId());
            return;
        }

        log.info("Side flats for blending: {}, width={}, randomness={}", sideFlats, width, randomness);

        // Check if flatService is available
        if (context.getFlatService() == null) {
            log.error("FlatService is not available in context - cannot blend sides");
            return;
        }

        // Blend sides with neighbors using the side blender
        HexGridSideBlender sideBlender = new HexGridSideBlender(flat, width, context, randomness);
        sideBlender.blendAllSides(sideFlats);

        log.info("Side blending completed for flat: {}", flat.getFlatId());
    }

    @Override
    protected int getDefaultLandOffset() {
        return 0;  // EdgeBlender doesn't use land offset
    }

    @Override
    protected int getDefaultLandLevel() {
        return 0;  // EdgeBlender doesn't use land level
    }

    @Override
    public int getLandSideLevel(WHexGrid.SIDE side) {
        // EdgeBlender uses the land level from the hex grid parameters
        // If not specified, use the center level
        return getLandCenterLevel();
    }
}
