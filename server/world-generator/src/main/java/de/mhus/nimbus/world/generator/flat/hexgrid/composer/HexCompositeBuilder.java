package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for orchestrating the complete hex composition pipeline.
 * Centralizes all composition steps to avoid error-prone manual orchestration in tests and production.
 *
 * Usage:
 * <pre>
 * HexCompositeBuilder.CompositionResult result = HexCompositeBuilder.builder()
 *     .composition(hexComposition)
 *     .worldId("test-world")
 *     .seed(12345L)
 *     .repository(mockRepository)  // optional, for testing
 *     .fillGaps(true)              // optional, default true
 *     .oceanBorderRings(1)         // optional, default 1
 *     .build()
 *     .compose();
 * </pre>
 */
@Slf4j
@Builder
public class HexCompositeBuilder {

    /**
     * The composition to process
     */
    private final HexComposition composition;

    /**
     * World ID for the generated grids
     */
    private final String worldId;

    /**
     * Random seed for composition
     */
    @Builder.Default
    private final Long seed = System.currentTimeMillis();

    /**
     * Optional repository for WHexGrid persistence (required for HexGridGenerator step)
     */
    private final WHexGridRepository repository;

    /**
     * Whether to fill gaps with ocean/land/coast (default: true)
     */
    @Builder.Default
    private final boolean fillGaps = true;

    /**
     * Number of ocean border rings around all features (default: 1)
     */
    @Builder.Default
    private final int oceanBorderRings = 1;

    /**
     * Whether to generate WHexGrids (default: false, only if repository provided)
     */
    @Builder.Default
    private final boolean generateWHexGrids = false;

    /**
     * Result of the complete composition pipeline
     */
    @Data
    @Builder
    public static class CompositionResult {
        private boolean success;
        private String errorMessage;
        private List<String> warnings;

        // Step results
        private BiomePlacementResult biomePlacementResult;
        private HexGridFillResult fillResult;
        private FlowComposer.FlowCompositionResult flowCompositionResult;
        private HexGridGenerator.GenerationResult generationResult;

        // Summary statistics
        private int totalBiomes;
        private int totalStructures;
        private int totalFlows;
        private int totalGrids;
        private int filledGrids;
        private int generatedWHexGrids;

        /**
         * Returns all FilledHexGrids from the fill result (if filling was enabled)
         */
        public List<FilledHexGrid> getAllGrids() {
            if (fillResult != null) {
                return fillResult.getAllGrids();
            }
            return new ArrayList<>();
        }

        /**
         * Returns all WHexGrids from placement result
         */
        public List<WHexGrid> getWHexGrids() {
            if (biomePlacementResult != null) {
                return biomePlacementResult.getHexGrids();
            }
            return new ArrayList<>();
        }
    }

    /**
     * Executes the complete composition pipeline.
     *
     * Steps:
     * 1. Initialize composition (applyDefaults)
     * 2. Prepare composition (HexCompositionPreparer)
     * 3. Compose biomes (BiomeComposer)
     * 4. Fill gaps with ocean/land/coast (HexGridFiller) - optional
     * 5. Compose flows - roads/rivers/walls (FlowComposer)
     * 6. Sync parameters from FeatureHexGrids to WHexGrids (HexGridParameterSync)
     * 7. Generate WHexGrids (HexGridGenerator) - optional, only if repository provided
     *
     * @return CompositionResult with all intermediate results and statistics
     */
    public CompositionResult compose() {
        log.info("=== Starting HexComposite Pipeline ===");
        log.info("WorldId: {}, Seed: {}, FillGaps: {}, OceanBorderRings: {}, GenerateWHexGrids: {}",
            worldId, seed, fillGaps, oceanBorderRings, generateWHexGrids);

        List<String> warnings = new ArrayList<>();
        CompositionResult.CompositionResultBuilder resultBuilder = CompositionResult.builder()
            .warnings(warnings);

        try {
            // Validate inputs
            if (composition == null) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Composition is null")
                    .build();
            }
            if (worldId == null || worldId.isBlank()) {
                return resultBuilder
                    .success(false)
                    .errorMessage("WorldId is required")
                    .build();
            }

            // Step 1: Initialize composition (apply defaults to all features)
            log.info("Step 1: Initializing composition");
            composition.initialize();

            // Step 2: Prepare composition
            log.info("Step 2: Preparing composition");
            HexCompositionPreparer preparer = new HexCompositionPreparer();
            boolean prepareSuccess = preparer.prepare(composition);
            if (!prepareSuccess) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Composition preparation failed")
                    .build();
            }

            // Step 3: Compose biomes
            log.info("Step 3: Composing biomes");
            BiomeComposer biomeComposer = new BiomeComposer();
            BiomePlacementResult placementResult = biomeComposer.compose(composition, worldId, seed);

            if (!placementResult.isSuccess()) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Biome composition failed: " + placementResult.getErrorMessage())
                    .biomePlacementResult(placementResult)
                    .build();
            }

            log.info("Placed {} biomes creating {} hex grids",
                placementResult.getPlacedBiomes().size(),
                placementResult.getHexGrids().size());

            resultBuilder.biomePlacementResult(placementResult);
            resultBuilder.totalBiomes(placementResult.getPlacedBiomes().size());
            resultBuilder.totalGrids(placementResult.getHexGrids().size());

            // Step 4: Fill gaps (optional)
            HexGridFillResult fillResult = null;
            if (fillGaps) {
                log.info("Step 4: Filling gaps with ocean/land/coast (borderRings={})", oceanBorderRings);
                HexGridFiller filler = new HexGridFiller();
                fillResult = filler.fill(placementResult, worldId, oceanBorderRings);

                if (!fillResult.isSuccess()) {
                    warnings.add("Gap filling had issues: " + fillResult.getErrorMessage());
                } else {
                    log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
                        fillResult.getTotalGridCount(),
                        fillResult.getOceanFillCount(),
                        fillResult.getLandFillCount(),
                        fillResult.getCoastFillCount());

                    resultBuilder.fillResult(fillResult);
                    resultBuilder.filledGrids(fillResult.getTotalGridCount());
                }
            } else {
                log.info("Step 4: Skipping gap filling (disabled)");
            }

            // Step 5: Compose flows (roads, rivers, walls)
            log.info("Step 5: Composing flows");
            FlowComposer flowComposer = new FlowComposer();
            FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
                composition, placementResult);

            if (!flowResult.isSuccess()) {
                warnings.add("Flow composition had issues: errors=" + flowResult.getFailedFlows());
            } else {
                log.info("Composed {} flows with {} total segments",
                    flowResult.getComposedFlows(),
                    flowResult.getTotalSegments());
            }

            resultBuilder.flowCompositionResult(flowResult);
            resultBuilder.totalFlows(flowResult.getComposedFlows());

            // Step 6: Sync parameters from FeatureHexGrids to WHexGrids
            log.info("Step 6: Syncing parameters from FeatureHexGrids to WHexGrids");
            HexGridParameterSync parameterSync = new HexGridParameterSync();
            int syncedCount = parameterSync.syncParametersToWHexGrids(
                composition, placementResult.getHexGrids());
            log.info("Synced parameters to {} WHexGrids", syncedCount);

            // Step 7: Generate WHexGrids (optional, only if repository provided)
            if (generateWHexGrids && repository != null) {
                log.info("Step 7: Generating WHexGrids");
                HexGridGenerator generator = new HexGridGenerator(repository);
                HexGridGenerator.GenerationResult genResult = generator.generateHexGrids(composition);

                if (!genResult.isSuccess()) {
                    warnings.add("WHexGrid generation had issues: " + genResult.getErrors());
                } else {
                    log.info("Generated {} WHexGrids", genResult.getCreatedGrids());
                }

                resultBuilder.generationResult(genResult);
                resultBuilder.generatedWHexGrids(genResult.getCreatedGrids());
            } else {
                if (generateWHexGrids && repository == null) {
                    warnings.add("WHexGrid generation requested but no repository provided");
                }
                log.info("Step 7: Skipping WHexGrid generation (disabled or no repository)");
            }

            // Success!
            log.info("=== HexComposite Pipeline Complete ===");
            log.info("Summary: biomes={}, flows={}, grids={}, filled={}, warnings={}",
                placementResult.getPlacedBiomes().size(),
                flowResult.getComposedFlows(),
                placementResult.getHexGrids().size(),
                fillResult != null ? fillResult.getTotalGridCount() : 0,
                warnings.size());

            return resultBuilder
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("HexComposite pipeline failed with exception", e);
            return resultBuilder
                .success(false)
                .errorMessage("Pipeline failed: " + e.getMessage())
                .build();
        }
    }
}
