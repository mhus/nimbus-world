package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Interface for features that need to build themselves internally to determine their size and coordinates.
 *
 * Examples:
 * - HexComposition: Uses HexCompositeBuilder to build the complete composition
 * - Village: Uses VillageDesigner to design the village layout
 *
 * The build() method is called before the feature is placed in the composition,
 * allowing it to determine its size and prepare its HexGrid coordinates.
 */
public interface BuildFeature {

    /**
     * Builds this feature internally to determine its size and HexGrid coordinates.
     *
     * @param context Build context with parameters like worldId, seed, repository, etc.
     * @return CompositionResult with the internal composition and build status
     */
    CompositionResult build(BuildContext context);
}
