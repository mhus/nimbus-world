package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Layer type enumeration.
 *
 * TERRAIN: Chunk-based layers with external storage (like WChunk)
 * MODEL: Entity-based layers with blocks stored in document
 */
public enum LayerType {
    /**
     * Terrain only layer - chunk-oriented with external storage.
     * Data stored per chunk in w_layer_terrain collection.
     */
    GROUND,

    /**
     * Model based terrain layer - one chunk orriented w_layer_terrain
     * and many w_layer_model documents. The model layres will be mergerd into the terrain layer in the first step
     * and the terrain layer will be merged into the block chunks in the second step.
     */
    MODEL
}
