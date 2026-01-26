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
 */
@Slf4j
public class SideBlenderBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Blending sides for flat: {}", flat.getFlatId());

        HashMap<WHexGrid.SIDE, String> edgeFlats = new HashMap<>();
        for (var edge : WHexGrid.SIDE.values()) {
            String key = "g_side_flat_" + edge.name().toLowerCase();
            String flatName = parameters.get(key);
            if (flatName != null) {
                edgeFlats.put(edge, flatName);
            }
        }
        int width = CastUtil.toint(parameters.get("g_edge_blend_width"), 20);
        if (edgeFlats.isEmpty()) {
            log.info("No edge flats defined for edge blending in flat: {}", flat.getFlatId());
            return;
        }
        log.info("Edge flats for blending: {}", edgeFlats);

        // Blend edges with neighbors using the edge blender
        HexGridSideBlender sideBlender = new HexGridSideBlender(flat, width, context);
        sideBlender.blendAllSides(edgeFlats);

        log.info("Edge blending completed for flat: {}", flat.getFlatId());
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
