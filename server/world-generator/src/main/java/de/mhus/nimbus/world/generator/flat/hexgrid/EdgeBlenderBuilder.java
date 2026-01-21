package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

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

        // Blend edges with neighbors using the edge blender
        HexGridEdgeBlender edgeBlender = new HexGridEdgeBlender(flat, context);
        edgeBlender.blendAllEdges();

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
