package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * EdgeFiller manipulator builder.
 * Fills edges of hex grids with data from their neighbors to avoid gaps during export.
 * Sides without neighboring grids are filled with BEDROCK material at ground level.
 *
 * Set g_edge_flat_north_east, g_edge_flat_east, g_edge_flat_south_east,
 * g_edge_flat_south_west, g_edge_flat_west, g_edge_flat_north_west to define side flat ids.
 * The flats will be loaded from WFlatService.
 *
 * Only fills points where material==0 (empty).
 */
@Slf4j
public class EdgeFillerBuilder extends HexGridBuilder {

    private static final int BEDROCK_MATERIAL = 6;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.trace("Filling edges for flat: {}", flat.getFlatId());

        // Disable protection to allow edge modifications
        boolean originalUnknownProtected = flat.isUnknownProtected();
        boolean originalBorderProtected = flat.isBorderProtected();
        flat.setUnknownProtected(false);
        flat.setBorderProtected(false);

        try {
            // Note: parameters come from HexGridBuilderService which already strips the "g_" prefix
            HashMap<WHexGrid.EDGE, String> sideFlats = new HashMap<>();
            for (var side : WHexGrid.EDGE.values()) {
                String key = "edge_flat_" + side.name().toLowerCase();
                String flatId = parameters.get(key);
                if (flatId != null) {
                    sideFlats.put(side, flatId);
                    log.debug("Found side flat for {}: {}", side, flatId);
                }
            }

            if (sideFlats.isEmpty()) {
                log.debug("No side flats defined for filling in flat: {}", flat.getFlatId());
                return;
            }

            log.debug("Side flats for filling: {}", sideFlats);

            // Check if flatService is available
            if (context.getFlatService() == null) {
                log.error("FlatService is not available in context - cannot fill edges");
                return;
            }

            // Get ground level from world
            int groundLevel = context.getWorld().getGroundLevel();

            // Fill edges with neighbors using the edge filler
            HexGridEdgeFiller edgeFiller = new HexGridEdgeFiller(flat, context, groundLevel);
            edgeFiller.fillAllSides(sideFlats);

            log.debug("Edge filling completed for flat: {}", flat.getFlatId());

            // Update the flat
            context.getFlatService().update(flat);
            log.debug("Updated flat after edge filling: {}", flat.getFlatId());

        } finally {
            // Restore protection flags
            flat.setUnknownProtected(originalUnknownProtected);
            flat.setBorderProtected(originalBorderProtected);
        }
    }

    @Override
    protected int getDefaultLandOffset() {
        return 0;  // EdgeFiller doesn't use land offset
    }

    @Override
    protected int getDefaultLandLevel() {
        return 0;  // EdgeFiller doesn't use land level
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        // EdgeFiller uses the land level from the hex grid parameters
        // If not specified, use the center level
        return getLandCenterLevel();
    }
}
