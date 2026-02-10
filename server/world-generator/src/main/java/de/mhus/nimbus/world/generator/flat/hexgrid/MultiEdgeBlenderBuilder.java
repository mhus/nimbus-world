package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Multi-Flat EdgeBlender manipulator builder.
 * Blends sides of hex grids with their neighbors by loading all adjacent flats
 * and processing their edges simultaneously for seamless transitions.
 *
 * By default, only EAST sides (NORTH_EAST, EAST, SOUTH_EAST) are processed to avoid
 * processing each edge twice. Set g_edge_blend_all_sides=true to process all sides.
 *
 * The center flat is NOT saved by this builder - only the adjacent flats are modified and saved.
 * The center flat will be saved by the calling builder.
 *
 * Set g_edge_flat_north_east, g_edge_flat_east, g_edge_flat_south_east,
 * g_edge_flat_south_west, g_edge_flat_west, g_edge_flat_north_west to define side flat ids.
 * Set g_edge_blend_width to define the width of the blending area (default 20).
 * Set edge_blend_range to control blur range for blending (default 10).
 * Set g_edge_blend_randomness to control random variations (default 0.5, range 0.0-1.0).
 * Set g_edge_shake_strength to add pixel swapping for more organic look (default 0.0, range 0.0-1.0).
 * Set g_edge_blur_radius to apply blur/smoothing after blending (default 0, range 0-5).
 */
@Slf4j
public class MultiEdgeBlenderBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat centerFlat = context.getFlat();

        log.trace("Multi-blending sides for center flat: {}", centerFlat.getFlatId());

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

        int width = CastUtil.toint(parameters.get("edge_blend_width"), 50);
        int range = CastUtil.toint(parameters.get("edge_blend_range"), 10);
        boolean blendAllSides = CastUtil.toboolean(parameters.get("edge_blend_all_sides"), false);

        if (sideFlats.isEmpty()) {
            log.debug("No side flats defined for multi-blending in flat: {}", centerFlat.getFlatId());
            return;
        }

        log.debug("Side flats for multi-blending: {}, width={}, allSides={}",
                sideFlats, width,  blendAllSides);

        // Check if flatService is available
        if (context.getFlatService() == null) {
            log.error("FlatService is not available in context - cannot multi-blend sides");
            return;
        }

        // Determine which sides to process
        Set<WHexGrid.EDGE> sidesToProcess = new HashSet<>();
        if (blendAllSides) {
            // Process all sides
            sidesToProcess.addAll(sideFlats.keySet());
            log.debug("Processing all sides: {}", sidesToProcess);
        } else {
            // Only process EAST sides (NORTH_EAST, EAST, SOUTH_EAST)
            for (WHexGrid.EDGE side : new WHexGrid.EDGE[]{WHexGrid.EDGE.NORTH_EAST, WHexGrid.EDGE.EAST, WHexGrid.EDGE.SOUTH_EAST}) {
                if (sideFlats.containsKey(side)) {
                    sidesToProcess.add(side);
                }
            }
            log.debug("Processing only EAST sides: {}", sidesToProcess);
        }

        if (sidesToProcess.isEmpty()) {
            log.debug("No sides to process for multi-blending in flat: {}", centerFlat.getFlatId());
            return;
        }

        // Load all adjacent flats that need to be modified
        HashMap<WHexGrid.EDGE, WFlat> loadedNeighbors = new HashMap<>();
        for (WHexGrid.EDGE side : sidesToProcess) {
            String neighborFlatId = sideFlats.get(side);
            if (neighborFlatId != null) {
                WFlat neighborFlat = context.getFlatService().findByWorldAndFlatId(
                        context.getWorld().getWorldId(), neighborFlatId);
                if (neighborFlat != null) {
                    loadedNeighbors.put(side, neighborFlat);
                    log.debug("Loaded neighbor flat for side {}: {}", side, neighborFlatId);
                } else {
                    log.warn("Neighbor flat not found: {} for side {}", neighborFlatId, side);
                }
            }
        }

        if (loadedNeighbors.isEmpty()) {
            log.debug("No neighbor flats loaded for multi-blending in flat: {}", centerFlat.getFlatId());
            return;
        }

        // DEBUG: Temporary log to verify blending is executed
        log.info("BLENDING: Executing multi-edge blend for flat {} (hexPos={}) with {} neighbors (width={}, range={})",
                centerFlat.getFlatId(), centerFlat.getHexGrid(), loadedNeighbors.size(), width, range);

        // DEBUG: Log which neighbors were loaded and their hex positions
        for (var entry : loadedNeighbors.entrySet()) {
            log.info("BLENDING:   - {} neighbor: flatId={}, hexPos={}",
                    entry.getKey(), entry.getValue().getFlatId(), entry.getValue().getHexGrid());
        }

        // Protection flags are NOT disabled - they protect areas outside the hexagon
        // We only write inside the hexagon, so no need to disable protection

        // Blend all sides simultaneously using multi-flat blender
        HexGridMultiEdgeBlender multiEdgeBlender = new HexGridMultiEdgeBlender(
                centerFlat, loadedNeighbors, width, range, context);
        multiEdgeBlender.blendAllEdges();

        // Save all modified neighbor flats (but NOT the center flat)
        for (var entry : loadedNeighbors.entrySet()) {
            WFlat neighborFlat = entry.getValue();
            context.getFlatService().update(neighborFlat);
            log.debug("Saved modified neighbor flat for side {}: {}", entry.getKey(), neighborFlat.getFlatId());
        }

        log.debug("Multi-side blending completed for flat: {}", centerFlat.getFlatId());
    }

    @Override
    protected int getDefaultOffset() {
        return 0;  // MultiEdgeBlender doesn't use land offset
    }

    @Override
    protected int getDefaultAsl() {
        return 0;  // MultiEdgeBlender doesn't use land level
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        // MultiEdgeBlender uses the land level from the hex grid parameters
        // If not specified, use the center level
        return getCenterAsl();
    }
}
