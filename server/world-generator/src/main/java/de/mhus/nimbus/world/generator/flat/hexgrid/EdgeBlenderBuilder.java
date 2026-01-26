package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * EdgeBlender manipulator builder.
 * Blends edges of hex grids with their neighbors, using per-side land levels.
 * This is a manipulator builder that can be applied after the main terrain generation.
 */
@Slf4j
public class EdgeBlenderBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Blending edges for flat: {}", flat.getFlatId());

        HashMap<WHexGrid.SIDE, String> edgeFlats = new HashMap<>();
        for (var edge : WHexGrid.SIDE.values()) {
            String key = "g_edge_flat_" + edge.name().toLowerCase();
            String flatName = parameters.get(key);
            if (flatName != null) {
                edgeFlats.put(edge, flatName);
            }
        }
        if (edgeFlats.isEmpty()) {
            log.info("No edge flats defined for edge blending in flat: {}", flat.getFlatId());
            return;
        }
        log.info("Edge flats for blending: {}", edgeFlats);

        // Blend edges with neighbors using the edge blender
        HexGridEdgeBlender edgeBlender = new HexGridEdgeBlender(flat, context);
        edgeBlender.blendAllEdges(edgeFlats);

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
