package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder for orchestrating the complete hex composition pipeline.
 * Centralizes all composition steps to avoid error-prone manual orchestration in tests and production.
 *
 * Usage:
 * <pre>
 * CompositionResult result = HexCompositeBuilder.builder()
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

            // Step 3: Compose biomes (positioning only, no WHexGrids yet)
            log.info("Step 3: Composing biomes (positioning)");
            BiomeComposer biomeComposer = new BiomeComposer();
            BiomePlacementResult placementResult = biomeComposer.compose(composition, worldId, seed);

            if (!placementResult.isSuccess()) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Biome composition failed: " + placementResult.getErrorMessage())
                    .biomePlacementResult(placementResult)
                    .build();
            }

            log.info("Placed {} biomes (positioning complete, WHexGrids not yet created)",
                placementResult.getPlacedBiomes().size());

            resultBuilder.biomePlacementResult(placementResult);
            resultBuilder.totalBiomes(placementResult.getPlacedBiomes().size());

            // Track biome grid count before fillers
            int initialBiomeCount = placementResult.getPlacedBiomes().size();
            int initialBiomeGridCount = placementResult.getPlacedBiomes().stream()
                .mapToInt(PlacedBiome::getActualSize)
                .sum();

            // Step 4: Fill gaps with specialized fillers (optional)
            HexGridFillResult fillResult = null;
            int mountainAdded = 0, lowlandAdded = 0, coastAdded = 0, oceanAdded = 0;

            if (fillGaps) {
                log.info("Step 4: Filling gaps with MountainFiller, LowlandFiller, CoastFiller (coastRings={})", oceanBorderRings);

                // Helper to build GridIndex from PlacedBiomes
                java.util.function.Supplier<Set<String>> buildGridIndex = () -> {
                    Set<String> coords = new java.util.HashSet<>();
                    for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                        for (de.mhus.nimbus.generated.types.HexVector2 coord : placed.getCoordinates()) {
                            coords.add(coord.getQ() + ":" + coord.getR());
                        }
                    }
                    return coords;
                };

                // Execute fillers in sequence, rebuilding GridIndex each time

                // 1. MountainFiller
                Set<String> gridIndex = buildGridIndex.get();
                MountainFiller mountainFiller = new MountainFiller();
                mountainAdded = mountainFiller.fill(composition, gridIndex, placementResult);
                log.info("MountainFiller: added {} PlacedBiomes", mountainAdded);

                // 2. LowlandFiller
                gridIndex = buildGridIndex.get();
                LowlandFiller lowlandFiller = new LowlandFiller();
                lowlandAdded = lowlandFiller.fill(composition, gridIndex, placementResult);
                log.info("LowlandFiller: added {} PlacedBiomes", lowlandAdded);

                // 3. CoastFiller
                gridIndex = buildGridIndex.get();
                CoastFiller coastFiller = new CoastFiller(oceanBorderRings);
                coastAdded = coastFiller.fill(composition, gridIndex, placementResult);
                log.info("CoastFiller: added {} PlacedBiomes", coastAdded);

                // 4. OceanFiller (ensures all regions are connected)
                gridIndex = buildGridIndex.get();
                OceanFiller oceanFiller = new OceanFiller();
                oceanAdded = oceanFiller.fill(composition, gridIndex, placementResult);
                log.info("OceanFiller: added {} PlacedBiomes", oceanAdded);

                int totalFillerBiomes = mountainAdded + lowlandAdded + coastAdded + oceanAdded;

                log.info("Filling complete: added {} filler PlacedBiomes (Mountain: {}, Lowland: {}, Coast: {}, Ocean: {})",
                    totalFillerBiomes, mountainAdded, lowlandAdded, coastAdded, oceanAdded);
            } else {
                log.info("Step 4: Skipping gap filling (disabled)");
            }

            // Step 4b: Convert all PlacedBiomes to WHexGrids
            log.info("Step 4b: Converting {} PlacedBiomes to WHexGrids", placementResult.getPlacedBiomes().size());
            for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                List<de.mhus.nimbus.world.shared.world.WHexGrid> wHexGrids =
                    biomeComposer.createWHexGridsForBiome(placed.getBiome(), placed.getCoordinates(), worldId);
                placementResult.getHexGrids().addAll(wHexGrids);
            }
            log.info("Created {} WHexGrids from PlacedBiomes", placementResult.getHexGrids().size());

            // Set totalGrids to initial biome grids (before fillers)
            resultBuilder.totalGrids(initialBiomeGridCount);

            // Calculate number of filler grids
            int totalWHexGrids = placementResult.getHexGrids().size();
            int fillerGridCount = totalWHexGrids - initialBiomeGridCount;

            // Create HexGridFillResult for backward compatibility
            if (fillGaps) {
                // Build map of existing grids for fast lookup
                Map<String, de.mhus.nimbus.world.shared.world.WHexGrid> gridMap = new HashMap<>();
                for (de.mhus.nimbus.world.shared.world.WHexGrid grid : placementResult.getHexGrids()) {
                    gridMap.put(grid.getPosition(), grid);
                }

                List<FilledHexGrid> allGrids = new ArrayList<>();

                // Add all biome grids (from placed biomes, including filler biomes)
                for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                    for (de.mhus.nimbus.generated.types.HexVector2 coord : placed.getCoordinates()) {
                        String key = coord.getQ() + ":" + coord.getR();
                        de.mhus.nimbus.world.shared.world.WHexGrid grid = gridMap.get(key);
                        if (grid != null) {
                            boolean isFiller = "true".equals(grid.getParameters().get("filler"));
                            FillerType fillerType = null;

                            if (isFiller) {
                                String fillerTypeStr = grid.getParameters().get("fillerType");
                                if ("mountain".equals(fillerTypeStr)) {
                                    fillerType = FillerType.LAND;
                                } else if ("coast".equals(fillerTypeStr)) {
                                    fillerType = FillerType.COAST;
                                } else if ("ocean".equals(fillerTypeStr)) {
                                    fillerType = FillerType.OCEAN;
                                } else {
                                    fillerType = FillerType.LAND;
                                }
                            }

                            allGrids.add(FilledHexGrid.builder()
                                .coordinate(coord)
                                .hexGrid(grid)
                                .isFiller(isFiller)
                                .fillerType(fillerType)
                                .biome(placed)
                                .build());
                        }
                    }
                }

                // Create fill result for backward compatibility
                fillResult = HexGridFillResult.builder()
                    .placementResult(placementResult)
                    .allGrids(allGrids)
                    .totalGridCount(placementResult.getHexGrids().size())
                    .oceanFillCount(oceanAdded)
                    .landFillCount(mountainAdded + lowlandAdded)
                    .coastFillCount(coastAdded)
                    .success(true)
                    .build();

                resultBuilder.fillResult(fillResult);
                resultBuilder.filledGrids(fillerGridCount);
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
                composition, placementResult, placementResult.getHexGrids());
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
