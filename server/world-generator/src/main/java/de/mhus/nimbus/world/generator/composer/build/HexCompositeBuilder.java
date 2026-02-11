package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomeComposer;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.CoastFiller;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.filler.ContinentFiller;
import de.mhus.nimbus.world.generator.composer.filler.FillerType;
import de.mhus.nimbus.world.generator.composer.filler.HexGridFillResult;
import de.mhus.nimbus.world.generator.composer.filler.LowlandFiller;
import de.mhus.nimbus.world.generator.composer.filler.MountainFiller;
import de.mhus.nimbus.world.generator.composer.filler.OceanFiller;
import de.mhus.nimbus.world.generator.composer.filler.OrphanGridFiller;
import de.mhus.nimbus.world.generator.composer.flow.FlowComposer;
import de.mhus.nimbus.world.generator.composer.point.PointComposer;
import de.mhus.nimbus.world.generator.composer.structure.StructureComposer;
import de.mhus.nimbus.world.generator.composer.structure.StructurePlacementResult;
import de.mhus.nimbus.world.generator.composer.town.Town;
import de.mhus.nimbus.world.generator.composer.town.TownExternalConnectionGenerator;
import de.mhus.nimbus.world.shared.world.WWorld;
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
     * Optional World object for accessing world configuration (hexGridSize, etc.)
     */
    private final WWorld world;

    /**
     * Random seed for composition
     */
    @Builder.Default
    private final Long seed = System.currentTimeMillis();

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
     * Executes the complete composition pipeline.
     *
     * Steps:
     * 1. Initialize composition (applyDefaults)
     * 2. Prepare composition (HexCompositionPreparer)
     * 3. Compose biomes (BiomeComposer) - positioning only, no WHexGrids yet
     * 3.5. Compose structures (StructureComposer) - villages, towns, etc. as multi-grid composites
     * 4. Fill gaps with ocean/land/coast (Fillers) - optional, creates PlacedBiomes only
     * 5. Compose points - precise locations within biomes (PointComposer)
     * 6. Compose flows - roads/rivers/walls (FlowComposer)
     * 6b. Fill ocean gaps for flows - creates PlacedBiomes for flow gaps
     * 7. Convert PlacedBiomes to WHexGrids - happens AFTER all compositions (biomes, points, flows)
     * 8. Sync parameters from FeatureHexGrids to WHexGrids (HexGridParameterSync)
     *
     * Note: WHexGrid persistence to database happens later in GenerateHexGridFromCompositeJobExecutor (Day3).
     * For tests that need WHexGrid persistence, use HexCompositeTestHelper.generateAndSaveWHexGrids().
     *
     * Architecture: During composition (Steps 1-6), all data is stored in FeatureHexGrids
     * (Feature.featureComposed.hexGrids). WHexGrids are only created at the end (Step 7)
     * to ensure all composition data (biomes, points, flows) is included.
     *
     * @return CompositionResult with all intermediate results and statistics
     */
    public CompositionResult compose() {
        log.debug("=== Starting HexComposite Pipeline ===");
        log.debug("WorldId: {}, Seed: {}, FillGaps: {}, OceanBorderRings: {}",
            worldId, seed, fillGaps, oceanBorderRings);

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
            log.debug("Step 1: Initializing composition");
            composition.initialize();

            // Step 2: Prepare composition
            log.debug("Step 2: Preparing composition");
            HexCompositionPreparer preparer = new HexCompositionPreparer();
            boolean prepareSuccess = preparer.prepare(composition);
            if (!prepareSuccess) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Composition preparation failed")
                    .build();
            }

            // Step 3: Compose biomes (positioning only, no WHexGrids yet)
            log.debug("Step 3: Composing biomes (positioning)");
            BiomeComposer biomeComposer = new BiomeComposer();
            BiomePlacementResult placementResult = biomeComposer.compose(composition, worldId, seed);

            if (!placementResult.isSuccess()) {
                return resultBuilder
                    .success(false)
                    .errorMessage("Biome composition failed: " + placementResult.getErrorMessage())
                    .biomePlacementResult(placementResult)
                    .build();
            }

            log.debug("Placed {} biomes (positioning complete, WHexGrids not yet created)",
                placementResult.getPlacedBiomes().size());

            resultBuilder.biomePlacementResult(placementResult);
            resultBuilder.totalBiomes(placementResult.getPlacedBiomes().size());

            // Step 3.5: Compose structures (villages, towns, etc.)
            log.debug("Step 3.5: Composing structures");

            // Create ComposeContext for structure composition
            ComposeContext structureContext = ComposeContext.builder()
                .composition(composition)
                .world(world)
                .build();

            StructureComposer structureComposer = new StructureComposer();
            StructurePlacementResult structureResult = structureComposer.composeStructures(
                structureContext, placementResult);

            if (!structureResult.isSuccess()) {
                warnings.add("Structure composition had issues: errors=" + structureResult.getErrors());
            } else {
                log.debug("Composed {} structures ({} failed)",
                    structureResult.getPlacedCount(),
                    structureResult.getFailedCount());
            }

            resultBuilder.structurePlacementResult(structureResult);
            resultBuilder.totalStructures(structureResult.getPlacedCount());

            // Track biome grid count before fillers
            int initialBiomeCount = placementResult.getPlacedBiomes().size();
            int initialBiomeGridCount = placementResult.getPlacedBiomes().stream()
                .mapToInt(PlacedBiome::getActualSize)
                .sum();

            // Step 4: Fill gaps with specialized fillers (optional)
            HexGridFillResult fillResult = null;
            int mountainAdded = 0, lowlandAdded = 0, continentAdded = 0, coastAdded = 0, oceanAdded = 0;

            if (fillGaps) {
                log.debug("Step 4: Filling gaps with MountainFiller, LowlandFiller, CoastFiller (coastRings={})", oceanBorderRings);

                // Helper to build GridIndex from PlacedBiomes
                java.util.function.Supplier<Set<String>> buildGridIndex = () -> {
                    Set<String> coords = new java.util.HashSet<>();
                    for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                        for (de.mhus.nimbus.generated.types.HexVector2 coord : placed.getCoordinates()) {
                            coords.add(TypeUtil.toStringHexCoord(coord.getQ(), coord.getR()));
                        }
                    }
                    return coords;
                };

                // Execute fillers in sequence, rebuilding GridIndex each time

                // 1. MountainFiller
                Set<String> gridIndex = buildGridIndex.get();
                MountainFiller mountainFiller = new MountainFiller();
                mountainAdded = mountainFiller.fill(composition, gridIndex, placementResult);
                log.debug("MountainFiller: added {} PlacedBiomes", mountainAdded);

                // 2. LowlandFiller
                gridIndex = buildGridIndex.get();
                LowlandFiller lowlandFiller = new LowlandFiller();
                lowlandAdded = lowlandFiller.fill(composition, gridIndex, placementResult);
                log.debug("LowlandFiller: added {} PlacedBiomes", lowlandAdded);

                // 2.5. ContinentFiller (fills gaps between biomes on same continent)
                gridIndex = buildGridIndex.get();
                ContinentFiller continentFiller = new ContinentFiller();
                continentAdded = continentFiller.fill(composition, gridIndex, placementResult);
                log.debug("ContinentFiller: added {} grids", continentAdded);

                // 3. CoastFiller
                gridIndex = buildGridIndex.get();
                CoastFiller coastFiller = new CoastFiller(oceanBorderRings);
                coastAdded = coastFiller.fill(composition, gridIndex, placementResult);
                log.debug("CoastFiller: added {} PlacedBiomes", coastAdded);

                // 4. OceanFiller (ensures all regions are connected)
                gridIndex = buildGridIndex.get();
                OceanFiller oceanFiller = new OceanFiller();
                oceanAdded = oceanFiller.fill(composition, gridIndex, placementResult);
                log.debug("OceanFiller: added {} PlacedBiomes", oceanAdded);

                int totalFillerBiomes = mountainAdded + lowlandAdded + continentAdded + coastAdded + oceanAdded;

                log.debug("Filling complete: added {} filler grids (Mountain: {}, Lowland: {}, Continent: {}, Coast: {}, Ocean: {})",
                    totalFillerBiomes, mountainAdded, lowlandAdded, continentAdded, coastAdded, oceanAdded);

                // Note: FeatureHexGrid configuration is now done in Step 6d by BiomeComposer.configureHexGridsForPlacedBiomes()
                // This registers all PlacedBiomes (including fillers) in the central FeatureHexGrid registry
            } else {
                log.debug("Step 4: Skipping gap filling (disabled)");
            }

            // Step 4c: Generate external connection points for villages
            log.debug("Step 4c: Generating external connection points for villages");
            TownExternalConnectionGenerator villageConnGenerator = new TownExternalConnectionGenerator();
            TownExternalConnectionGenerator.GenerationResult villageConnResult =
                    villageConnGenerator.generateExternalConnections(composition, placementResult);
            log.debug("Generated {} external connection points for villages", villageConnResult.getTotalPoints());

            // Step 4d: Connect external connection points to village internal points
            log.debug("Step 4d: Connecting external connection points to village interiors");
            int connectedVillages = 0;
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Town village) {
                    if (village.getExternalConnectionPoints() != null && !village.getExternalConnectionPoints().isEmpty()) {
                        village.connectExternalConnectionPoints(world.getPublicData().getHexGridSize());
                        connectedVillages++;
                    }
                }
            }
            log.debug("Connected external points for {} villages", connectedVillages);

            // Step 5: Compose points (place Points within biomes)
            log.debug("Step 5: Composing points");
            PointComposer pointComposer = new PointComposer();
            PointComposer.PointCompositionResult pointResult = pointComposer.composePoints(
                composition, placementResult, world);

            if (!pointResult.isSuccess()) {
                warnings.add("Point composition had issues: errors=" + pointResult.getErrors());
            } else {
                log.debug("Composed {} points ({} failed)",
                    pointResult.getComposedPoints(),
                    pointResult.getFailedPoints());
            }

            resultBuilder.pointCompositionResult(pointResult);
            resultBuilder.totalPoints(pointResult.getComposedPoints());

            // Step 5b: Register ALL PlacedBiomes (including fillers) in central FeatureHexGrid registry
            // MUST happen BEFORE FlowComposer, so that FlowComposer can add RoadConfigParts to filler grids
            log.debug("Step 5b: Registering all biomes (including fillers) in central registry");
            BiomeComposer biomeComposerForFillers = new BiomeComposer();
            biomeComposerForFillers.configureHexGridsForPlacedBiomes(
                placementResult.getPlacedBiomes(), composition);
            log.debug("Registered all biomes (including fillers) in central FeatureHexGrid registry");

            // Step 6: Compose flows (roads, rivers, walls)
            log.debug("Step 6: Composing flows");
            FlowComposer flowComposer = new FlowComposer();
            FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
                composition, placementResult);

            if (!flowResult.isSuccess()) {
                warnings.add("Flow composition had issues: errors=" + flowResult.getFailedFlows());
            } else {
                log.debug("Composed {} flows with {} total segments",
                    flowResult.getComposedFlows(),
                    flowResult.getTotalSegments());
            }

            resultBuilder.flowCompositionResult(flowResult);
            resultBuilder.totalFlows(flowResult.getComposedFlows());

            // Step 6b: Fill ocean gaps where flows cross empty space
            if (fillGaps && flowResult.getComposedFlows() > 0) {
                log.debug("Step 6b: Filling ocean gaps where flows cross empty space");

                // Rebuild grid index (includes all grids added so far)
                Set<String> gridIndex = new java.util.HashSet<>();
                for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                    for (de.mhus.nimbus.generated.types.HexVector2 coord : placed.getCoordinates()) {
                        gridIndex.add(TypeUtil.toStringHexCoord(coord.getQ(), coord.getR()));
                    }
                }

                OceanFiller oceanFlowFiller = new OceanFiller();
                int flowGapsFilled = oceanFlowFiller.fillFlowGaps(composition, gridIndex, placementResult);

                if (flowGapsFilled > 0) {
                    log.debug("OceanFiller.fillFlowGaps: added {} ocean PlacedBiomes (WHexGrids will be created in Step 7)", flowGapsFilled);
                } else {
                    log.debug("No flow gaps to fill - all flow grids already exist");
                }
            }

            // Step 6c: Fill orphan grids (grids used by features but not assigned to any biome)
            log.debug("Step 6c: Filling orphan grids");
            OrphanGridFiller orphanGridFiller = new OrphanGridFiller();
            int orphansAdded = orphanGridFiller.fill(composition, placementResult);
            if (orphansAdded > 0) {
                log.debug("OrphanGridFiller: assigned {} orphan grids to biomes", orphansAdded);
            } else {
                log.debug("No orphan grids found");
            }

            // Note: Registering biomes in central registry was moved to Step 5b (before FlowComposer)
            // so that FlowComposer can add RoadConfigParts to all grids including fillers

            // Step 7: Convert FeatureHexGrids to WHexGrids (after all compositions)
            // FeatureHexGrids are now managed centrally in composition.featureHexGridRegistry
            // They contain accumulated data from all features (biomes, points, flows)
            log.debug("Step 7: Converting FeatureHexGrids from central registry to WHexGrids");

            // Use FeatureHexGrids from central registry (single source of truth, no duplicates)
            Map<String, FeatureHexGrid> allFeatureHexGrids = composition.getFeatureHexGridRegistry();

            if (allFeatureHexGrids == null || allFeatureHexGrids.isEmpty()) {
                log.warn("No FeatureHexGrids found in central registry - composition might be incomplete");
                allFeatureHexGrids = new HashMap<>();
            }

            log.debug("Found {} FeatureHexGrids in central registry", allFeatureHexGrids.size());

            // Create WHexGrids from FeatureHexGrids
            for (FeatureHexGrid featureHexGrid : allFeatureHexGrids.values()) {
                de.mhus.nimbus.world.shared.world.WHexGrid wHexGrid = createWHexGridFromFeatureHexGrid(
                    featureHexGrid, worldId);
                placementResult.getHexGrids().add(wHexGrid);
            }

            log.debug("Created {} WHexGrids from central registry", placementResult.getHexGrids().size());

            // Set totalGrids to initial biome grids (before fillers)
            resultBuilder.totalGrids(initialBiomeGridCount);

            // Calculate number of filler grids
            int totalWHexGrids = placementResult.getHexGrids().size();
            int fillerGridCount = totalWHexGrids - initialBiomeGridCount;

            // Create HexGridFillResult for backward compatibility
            if (fillGaps) {
                // Filler information is already set in FeatureHexGrids by BiomeComposer
                // and copied to WHexGrids by createWHexGridFromFeatureHexGrid()
                log.debug("All {} FeatureHexGrids have filler information set by BiomeComposer", allFeatureHexGrids.size());

                // Points are ASPEKTE - they don't create separate grids, they only add
                // parameters to existing grids in the central registry

                // No need to store separate FilledHexGrids - all data is in central FeatureHexGrid registry
                log.debug("All grid data stored in central FeatureHexGrid registry ({} grids)",
                    allFeatureHexGrids.size());

                // Create fill result with statistics (grids are in central registry)
                fillResult = HexGridFillResult.builder()
                    .placementResult(placementResult)
                    .totalGridCount(placementResult.getHexGrids().size())
                    .oceanFillCount(oceanAdded)
                    .landFillCount(mountainAdded + lowlandAdded)
                    .coastFillCount(coastAdded)
                    .mountainFillCount(mountainAdded)
                    .lowlandFillCount(lowlandAdded)
                    .continentFillCount(continentAdded)
                    .success(true)
                    .build();

                resultBuilder.fillResult(fillResult);
                resultBuilder.filledGrids(fillerGridCount);
            } else {
                // Even without fillGaps, all grids are already in central registry
                // No filler grids, so isFiller remains false (default) for all grids
                log.debug("All grid data stored in central FeatureHexGrid registry ({} grids, no fillers)",
                    allFeatureHexGrids.size());
            }

            // Step 7.5: Populate central registry with Structure and Flow HexGrids
            // Biomes are already registered by BiomeComposer, but Structures and Flows
            // need to be transferred from their local storage to central registry
            log.debug("Step 7.5: Populating central registry with Structure and Flow HexGrids");
            populateCentralRegistry(composition);
            log.debug("Central registry populated with Structure/Flow HexGrids");

            // Step 7.5a: Convert FlowSegments to ConfigParts (AFTER flowSegments are in central registry)
            // Now that Flow.hexGrids have been transferred to central registry, we can convert
            // the flowSegments to RoadConfigParts/RiverConfigParts
            log.info("Step 7.5a: Converting FlowSegments to ConfigParts");
            int convertedFlows = flowComposer.convertAllFlowSegmentsToConfigParts(composition, placementResult);
            log.info("Converted FlowSegments for {} flows", convertedFlows);

            // Step 7.5b: Merge Flow Aspects (RoadConfigParts) into central registry
            // Flows store their aspects (RoadConfigParts, RiverConfigParts) in Flow.hexGrids
            // These need to be merged into the FeatureHexGrids in central registry
            log.info("Step 7.5b: Merging Flow Aspects into central registry");
            int mergedAspects = mergeFlowAspectsIntoCentralRegistry(composition);
            log.info("Merged {} flow aspects into central registry", mergedAspects);

            // Step 7.6: Configure road/river/wall parameters from RoadConfigParts
            // Must run AFTER populateCentralRegistry() so HexGridRoadConfigurator
            // works with the complete central registry
            log.debug("Step 7.6: Configuring road/river/wall parameters");
            HexGridRoadConfigurator roadConfigurator = new HexGridRoadConfigurator();
            HexGridRoadConfigurator.RoadConfigurationResult roadResult =
                roadConfigurator.configureRoads(composition, placementResult);
            log.debug("Road configuration: configured={}/{} grids, {} total segments",
                roadResult.getConfiguredGrids(), roadResult.getTotalGrids(),
                roadResult.getTotalSegments());
            if (!roadResult.isSuccess()) {
                log.warn("Road configuration had errors: {}", roadResult.getErrors());
            }

            // Step 8: Sync parameters from central registry to WHexGrids
            log.debug("Step 8: Syncing parameters from central registry to WHexGrids");
            HexGridParameterSync parameterSync = new HexGridParameterSync();
            int syncedCount = parameterSync.syncParametersToWHexGrids(
                composition, placementResult, placementResult.getHexGrids());
            log.debug("Synced parameters to {} WHexGrids", syncedCount);

            // WHexGrid persistence happens later in GenerateHexGridFromCompositeJobExecutor (Day3)
            // For tests, use HexCompositeTestHelper.generateAndSaveWHexGrids()

            // Success!
            log.debug("=== HexComposite Pipeline Complete ===");
            log.debug("Summary: biomes={}, structures={}, points={}, flows={}, grids={}, filled={}, warnings={}",
                placementResult.getPlacedBiomes().size(),
                structureResult.getPlacedCount(),
                pointResult.getComposedPoints(),
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

    /**
     * Finds a PlacedBiome by its biome name.
     *
     * @param placedBiomes List of placed biomes
     * @param biomeName The biome name to search for
     * @return The PlacedBiome with matching biome name, or null if not found
     */
    private PlacedBiome findPlacedBiomeByName(List<PlacedBiome> placedBiomes, String biomeName) {
        if (biomeName == null || placedBiomes == null) {
            return null;
        }

        return placedBiomes.stream()
            .filter(placed -> biomeName.equals(placed.getBiome().getName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Populates the central FeatureHexGrid registry from all features.
     * Collects all FeatureHexGrids from Biomes and other Area-based features
     * into the central composition registry to prevent duplicates.
     *
     * For grids that already exist in the registry, parameters are merged
     * with warnings on collision.
     *
     * @param composition The composition with central registry
     */
    private void populateCentralRegistry(HexComposition composition) {
        if (composition == null) {
            log.warn("Cannot populate central registry - composition is null");
            return;
        }

        int registeredCount = 0;
        int mergedCount = 0;
        int parameterCollisionCount = 0;

        log.debug("Populating central FeatureHexGrid registry from {} features",
            composition.getFeatures() != null ? composition.getFeatures().size() : 0);

        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                // Only Structures and Flows have local hexGrids
                // Biomes use central registry (already populated by BiomeComposer)
                List<FeatureHexGrid> hexGrids = null;
                if (feature instanceof de.mhus.nimbus.world.generator.composer.structure.Structure) {
                    hexGrids = ((de.mhus.nimbus.world.generator.composer.structure.Structure) feature).getHexGrids();
                } else if (feature instanceof de.mhus.nimbus.world.generator.composer.flow.Flow) {
                    hexGrids = ((de.mhus.nimbus.world.generator.composer.flow.Flow) feature).getHexGrids();
                }

                if (hexGrids == null || hexGrids.isEmpty()) {
                    continue;
                }

                for (FeatureHexGrid featureGrid : hexGrids) {
                    if (featureGrid.getCoordinate() == null) {
                        log.warn("Feature '{}' has HexGrid without coordinate, skipping",
                            feature.getName());
                        continue;
                    }

                    // Get or create in central registry
                    FeatureHexGrid centralGrid = composition.getOrCreateFeatureHexGrid(
                        featureGrid.getCoordinate());

                    // Check if grid was newly created or already existed
                    boolean isNew = centralGrid.getName() == null && centralGrid.getParameters().isEmpty();

                    if (isNew) {
                        // New grid - copy all data
                        centralGrid.setName(featureGrid.getName());
                        centralGrid.setDescription(featureGrid.getDescription());

                        if (featureGrid.getParameters() != null) {
                            centralGrid.getParameters().putAll(featureGrid.getParameters());
                        }

                        // Copy flowSegments from Flow/Structure to central registry
                        if (featureGrid.getFlowSegments() != null && !featureGrid.getFlowSegments().isEmpty()) {
                            centralGrid.getFlowSegments().addAll(featureGrid.getFlowSegments());
                        }

                        registeredCount++;
                        log.trace("Registered new grid [{},{}] from feature '{}'",
                            featureGrid.getCoordinate().getQ(),
                            featureGrid.getCoordinate().getR(),
                            feature.getName());
                    } else {
                        // Grid already exists - merge parameters with collision detection
                        mergedCount++;

                        if (featureGrid.getParameters() != null) {
                            for (Map.Entry<String, String> entry : featureGrid.getParameters().entrySet()) {
                                String existingValue = centralGrid.getParameters().get(entry.getKey());

                                if (existingValue != null && !existingValue.equals(entry.getValue())) {
                                    // Parameter collision
                                    log.warn("Parameter collision at grid [{},{}]: key='{}' " +
                                        "existing='{}' new='{}' - keeping existing value",
                                        featureGrid.getCoordinate().getQ(),
                                        featureGrid.getCoordinate().getR(),
                                        entry.getKey(),
                                        existingValue.substring(0, Math.min(50, existingValue.length())),
                                        entry.getValue().substring(0, Math.min(50, entry.getValue().length())));
                                    parameterCollisionCount++;
                                } else if (existingValue == null) {
                                    // New parameter - add it
                                    centralGrid.getParameters().put(entry.getKey(), entry.getValue());
                                }
                                // If values are equal, no action needed
                            }
                        }

                        // Merge flowSegments from Flow/Structure to central registry
                        if (featureGrid.getFlowSegments() != null && !featureGrid.getFlowSegments().isEmpty()) {
                            centralGrid.getFlowSegments().addAll(featureGrid.getFlowSegments());
                        }

                        log.trace("Merged parameters for grid [{},{}] from feature '{}'",
                            featureGrid.getCoordinate().getQ(),
                            featureGrid.getCoordinate().getR(),
                            feature.getName());
                    }
                }
            }
        }

        log.debug("Central registry populated: {} new grids registered, {} grids merged, {} parameter collisions",
            registeredCount, mergedCount, parameterCollisionCount);
    }

    /**
     * Merges Flow aspects (RoadConfigParts, RiverConfigParts, WallConfigParts) into the central registry.
     *
     * Flows store their aspects in Flow.hexGrids during composition. These aspects need to be
     * merged into the corresponding FeatureHexGrids in the central registry so that
     * HexGridRoadConfigurator can find them and convert them to g_road/g_river/g_wall parameters.
     *
     * Multiple Flows can contribute to the same HexGrid coordinate - all their ConfigParts
     * are collected and merged together.
     *
     * @param composition The composition with central registry
     * @return Number of aspect grids merged
     */
    private int mergeFlowAspectsIntoCentralRegistry(HexComposition composition) {
        if (composition == null || composition.getFeatures() == null) {
            log.warn("Cannot merge flow aspects - composition or features is null");
            return 0;
        }

        int mergedGridCount = 0;
        int roadPartsCount = 0;
        int riverPartsCount = 0;
        int wallPartsCount = 0;

        log.debug("Merging Flow aspects from {} features into central registry",
            composition.getFeatures().size());

        // Iterate through all features to find Flows
        for (Feature feature : composition.getFeatures()) {
            if (!(feature instanceof de.mhus.nimbus.world.generator.composer.flow.Flow)) {
                continue;
            }

            de.mhus.nimbus.world.generator.composer.flow.Flow flow =
                (de.mhus.nimbus.world.generator.composer.flow.Flow) feature;

            List<de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid> flowHexGrids = flow.getHexGrids();
            if (flowHexGrids == null || flowHexGrids.isEmpty()) {
                log.trace("Flow '{}' has no hexGrids, skipping", flow.getName());
                continue;
            }

            log.debug("Flow '{}' has {} aspect grids to merge", flow.getName(), flowHexGrids.size());

            // For each aspect grid in the Flow, merge its ConfigParts into central registry
            for (de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid flowGrid : flowHexGrids) {
                if (flowGrid.getCoordinate() == null) {
                    log.warn("Flow '{}' has hexGrid without coordinate, skipping", flow.getName());
                    continue;
                }

                // Get or create the corresponding grid in central registry
                de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid centralGrid =
                    composition.getOrCreateFeatureHexGrid(flowGrid.getCoordinate());

                boolean merged = false;

                // Merge RoadConfigParts
                if (flowGrid.getRoadConfigParts() != null && !flowGrid.getRoadConfigParts().isEmpty()) {
                    centralGrid.getRoadConfigParts().addAll(flowGrid.getRoadConfigParts());
                    roadPartsCount += flowGrid.getRoadConfigParts().size();
                    merged = true;
                    log.trace("Merged {} RoadConfigParts from Flow '{}' to grid [{},{}]",
                        flowGrid.getRoadConfigParts().size(), flow.getName(),
                        flowGrid.getCoordinate().getQ(), flowGrid.getCoordinate().getR());
                }

                // Merge RiverConfigParts
                if (flowGrid.getRiverConfigParts() != null && !flowGrid.getRiverConfigParts().isEmpty()) {
                    centralGrid.getRiverConfigParts().addAll(flowGrid.getRiverConfigParts());
                    riverPartsCount += flowGrid.getRiverConfigParts().size();
                    merged = true;
                    log.trace("Merged {} RiverConfigParts from Flow '{}' to grid [{},{}]",
                        flowGrid.getRiverConfigParts().size(), flow.getName(),
                        flowGrid.getCoordinate().getQ(), flowGrid.getCoordinate().getR());
                }

                // Merge WallConfigParts
                if (flowGrid.getWallConfigParts() != null && !flowGrid.getWallConfigParts().isEmpty()) {
                    centralGrid.getWallConfigParts().addAll(flowGrid.getWallConfigParts());
                    wallPartsCount += flowGrid.getWallConfigParts().size();
                    merged = true;
                    log.trace("Merged {} WallConfigParts from Flow '{}' to grid [{},{}]",
                        flowGrid.getWallConfigParts().size(), flow.getName(),
                        flowGrid.getCoordinate().getQ(), flowGrid.getCoordinate().getR());
                }

                if (merged) {
                    mergedGridCount++;
                }
            }
        }

        log.info("Merged {} aspect grids: {} road parts, {} river parts, {} wall parts",
            mergedGridCount, roadPartsCount, riverPartsCount, wallPartsCount);

        return mergedGridCount;
    }

    /**
     * Creates a WHexGrid from a FeatureHexGrid, preserving all accumulated composition data.
     * This is the correct way to convert composed data to final WHexGrid format.
     *
     * @param featureHexGrid The FeatureHexGrid with accumulated data from all features
     * @param worldId The world ID
     * @return WHexGrid with all parameters and data from the FeatureHexGrid
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid createWHexGridFromFeatureHexGrid(
        FeatureHexGrid featureHexGrid, String worldId) {

        de.mhus.nimbus.generated.types.HexVector2 coord = featureHexGrid.getCoordinate();

        // Create public HexGrid data
        de.mhus.nimbus.generated.types.HexGrid publicData = new de.mhus.nimbus.generated.types.HexGrid();
        publicData.setPosition(coord);
        publicData.setName(featureHexGrid.getName());
        publicData.setDescription(featureHexGrid.getDescription());

        // Copy all parameters from FeatureHexGrid
        Map<String, String> parameters = new HashMap<>();
        if (featureHexGrid.getParameters() != null) {
            parameters.putAll(featureHexGrid.getParameters());
        }

        // Debug: check if river parameter exists
        if (parameters.containsKey("g_river")) {
            log.debug("Grid ({},{}) has g_river parameter: {}",
                coord.getQ(), coord.getR(), parameters.get("g_river").substring(0, Math.min(100, parameters.get("g_river").length())));
        }

        // Add debug text overlay with coordinates
        String coordText = coord.getQ() + "," + coord.getR();
        parameters.put("debugText", coordText);

        // TODO: Convert riverConfigParts, roadConfigParts, wallConfigParts to JSON parameters
        // This should be done by HexGridRoadConfigurator or similar component

        return de.mhus.nimbus.world.shared.world.WHexGrid.builder()
            .worldId(worldId)
            .position(TypeUtil.toStringHexCoord(coord.getQ(), coord.getR()))
            .publicData(publicData)
            .parameters(parameters)
            .enabled(true)
            .build();
    }
}
