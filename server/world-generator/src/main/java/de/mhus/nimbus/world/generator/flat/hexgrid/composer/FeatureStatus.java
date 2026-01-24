package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Status of a feature in the composition workflow.
 * This enum is server-side only and not exposed to TypeScript.
 */
public enum FeatureStatus {
    NEW,        // Initial state
    PREPARED,   // After HexCompositionPreparer
    COMPOSED,   // After BiomeComposer placement
    CREATED     // After builders executed
}
