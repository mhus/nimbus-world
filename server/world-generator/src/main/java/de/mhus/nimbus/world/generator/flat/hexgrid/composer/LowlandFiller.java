package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Fills areas between land features with lowland/plains grids.
 *
 * Algorithm (TODO - not yet implemented):
 * - Find enclosed empty areas between biomes
 * - Fill with appropriate lowland biome (plains, grassland, etc.)
 * - Consider surrounding biome types for appropriate fill
 *
 * Currently a stub - implementation coming later.
 */
@Slf4j
public class LowlandFiller {

    /**
     * Fills empty areas between land features with lowland biomes.
     *
     * @param composition The composition to fill
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result from BiomeComposer
     * @return Number of biomes added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.info("Starting LowlandFiller (stub - not yet implemented)");

        // TODO: Implement lowland filling logic
        // For now, do nothing

        log.info("LowlandFiller: 0 biomes added (stub)");

        return 0;
    }
}
