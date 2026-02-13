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
     * Pipeline phases:
     * Phase A: Komposition (Feature-Design + Positionierung)
     *   Step 1:   Initialize composition (applyDefaults)
     *   Step 2:   Prepare composition (HexCompositionPreparer)
     *   Step 3:   Compose biomes (BiomeComposer) - positioning only
     *   Step 3.5: Compose structures (StructureComposer) - villages, towns, etc.
     *
     * Phase B: Gap Filling
     *   Step 4.1: MountainFiller
     *   Step 4.2: LowlandFiller
     *   Step 4.3: ContinentFiller
     *   Step 4.4: CoastFiller
     *   Step 4.5: OceanFiller
     *
     * Phase C: Central Registry vollständig befüllen
     *   Step 5a:  Register ALL PlacedBiomes (incl. fillers) in Central Registry
     *   Step 5b:  Register Structure HexGrids in Central Registry (populateCentralRegistry)
     *
     * Phase D: Verbindungen + Feature-Platzierung
     *   Step 6:   Village external connection generation
     *   Step 6.5: Village internal connection linking
     *   Step 7:   Compose Points (PointComposer writes g_mountain, g_lakes etc.)
     *   Step 8:   Compose Flows (FlowComposer writes FlowSegments)
     *
     * Phase E: Orphan-Handling
     *   Step 9:   OrphanGridFiller (always Coast or Ocean)
     *
     * Phase F: Parameter-Aufbereitung
     *   Step 10a: Convert FlowSegments → ConfigParts
     *   Step 10b: Configure roads/rivers/walls (HexGridRoadConfigurator)
     *
     * After Phase F the Central Registry is complete.
     * WHexGrid creation happens SEPARATELY in GenerateHexGridFromCompositeJobExecutor.
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

            // ============================================================
            // Phase A: Komposition (Feature-Design + Positionierung)
            // ============================================================

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

            // Step 3: Compose biomes (positioning only)
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

            log.debug("Placed {} biomes (positioning complete)",
                placementResult.getPlacedBiomes().size());

            resultBuilder.biomePlacementResult(placementResult);
            resultBuilder.totalBiomes(placementResult.getPlacedBiomes().size());

            // Step 3.5: Compose structures (villages, towns, etc.)
            log.debug("Step 3.5: Composing structures");

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
            int initialBiomeGridCount = placementResult.getPlacedBiomes().stream()
                .mapToInt(PlacedBiome::getActualSize)
                .sum();

            // ============================================================
            // Phase B: Gap Filling
            // ============================================================

            int mountainAdded = 0, lowlandAdded = 0, continentAdded = 0, coastAdded = 0, oceanAdded = 0;

            if (fillGaps) {
                log.debug("Phase B: Filling gaps (coastRings={})", oceanBorderRings);

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

                // Step 4.1: MountainFiller
                Set<String> gridIndex = buildGridIndex.get();
                MountainFiller mountainFiller = new MountainFiller();
                mountainAdded = mountainFiller.fill(composition, gridIndex, placementResult);
                log.debug("Step 4.1 MountainFiller: added {} PlacedBiomes", mountainAdded);

                // Step 4.2: LowlandFiller
                gridIndex = buildGridIndex.get();
                LowlandFiller lowlandFiller = new LowlandFiller();
                lowlandAdded = lowlandFiller.fill(composition, gridIndex, placementResult);
                log.debug("Step 4.2 LowlandFiller: added {} PlacedBiomes", lowlandAdded);

                // Step 4.3: ContinentFiller
                gridIndex = buildGridIndex.get();
                ContinentFiller continentFiller = new ContinentFiller();
                continentAdded = continentFiller.fill(composition, gridIndex, placementResult);
                log.debug("Step 4.3 ContinentFiller: added {} grids", continentAdded);

                // Step 4.4: CoastFiller
                gridIndex = buildGridIndex.get();
                CoastFiller coastFiller = new CoastFiller(oceanBorderRings);
                coastAdded = coastFiller.fill(composition, gridIndex, placementResult);
                log.debug("Step 4.4 CoastFiller: added {} PlacedBiomes", coastAdded);

                // Step 4.5: OceanFiller
                gridIndex = buildGridIndex.get();
                OceanFiller oceanFiller = new OceanFiller();
                oceanAdded = oceanFiller.fill(composition, gridIndex, placementResult);
                log.debug("Step 4.5 OceanFiller: added {} PlacedBiomes", oceanAdded);

                int totalFillerBiomes = mountainAdded + lowlandAdded + continentAdded + coastAdded + oceanAdded;
                log.debug("Phase B complete: added {} filler grids (Mountain: {}, Lowland: {}, Continent: {}, Coast: {}, Ocean: {})",
                    totalFillerBiomes, mountainAdded, lowlandAdded, continentAdded, coastAdded, oceanAdded);
            } else {
                log.debug("Phase B: Skipping gap filling (disabled)");
            }

            // ============================================================
            // Phase C: Central Registry vollständig befüllen
            // ============================================================

            // Step 5a: Register ALL PlacedBiomes (including fillers) in central FeatureHexGrid registry
            log.debug("Step 5a: Registering all biomes (including fillers) in central registry");
            BiomeComposer biomeComposerForFillers = new BiomeComposer();
            biomeComposerForFillers.configureHexGridsForPlacedBiomes(
                placementResult.getPlacedBiomes(), composition);
            log.debug("Registered all biomes (including fillers) in central FeatureHexGrid registry");

            // Step 5b: Register Structure HexGrids in central registry
            log.debug("Step 5b: Populating central registry with Structure HexGrids");
            populateCentralRegistry(composition);
            log.debug("Central registry populated with Structure HexGrids");

            // ============================================================
            // Phase D: Verbindungen + Feature-Platzierung
            // ============================================================

            // Step 6: Generate external connection points for villages
            log.debug("Step 6: Generating external connection points for villages");
            TownExternalConnectionGenerator villageConnGenerator = new TownExternalConnectionGenerator();
            TownExternalConnectionGenerator.GenerationResult villageConnResult =
                    villageConnGenerator.generateExternalConnections(composition, placementResult);
            log.debug("Generated {} external connection points for villages", villageConnResult.getTotalPoints());

            // Step 6.5: Connect external connection points to village internal points
            log.debug("Step 6.5: Connecting external connection points to village interiors");
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

            // Step 7: Compose points (place Points within biomes)
            log.debug("Step 7: Composing points");
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

            // Step 8: Compose flows (roads, rivers, walls)
            log.debug("Step 8: Composing flows");
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

            // ============================================================
            // Phase E: Orphan-Handling
            // ============================================================

            // Step 9: Fill orphan grids (always Coast or Ocean)
            log.debug("Step 9: Filling orphan grids");
            OrphanGridFiller orphanGridFiller = new OrphanGridFiller();
            int orphansAdded = orphanGridFiller.fill(composition, placementResult);
            if (orphansAdded > 0) {
                log.debug("OrphanGridFiller: assigned {} orphan grids as Coast/Ocean", orphansAdded);
            } else {
                log.debug("No orphan grids found");
            }

            // ============================================================
            // Phase F: Parameter-Aufbereitung
            // ============================================================

            // Step 10a: Convert FlowSegments to ConfigParts
            log.info("Step 10a: Converting FlowSegments to ConfigParts");
            int convertedFlows = flowComposer.convertAllFlowSegmentsToConfigParts(composition, placementResult);
            log.info("Converted FlowSegments for {} flows", convertedFlows);

            // Step 10b: Configure road/river/wall parameters from RoadConfigParts
            log.debug("Step 10b: Configuring road/river/wall parameters");
            HexGridRoadConfigurator roadConfigurator = new HexGridRoadConfigurator();
            HexGridRoadConfigurator.RoadConfigurationResult roadResult =
                roadConfigurator.configureRoads(composition, placementResult);
            log.debug("Road configuration: configured={}/{} grids, {} total segments",
                roadResult.getConfiguredGrids(), roadResult.getTotalGrids(),
                roadResult.getTotalSegments());
            if (!roadResult.isSuccess()) {
                log.warn("Road configuration had errors: {}", roadResult.getErrors());
            }

            // ============================================================
            // Build result — Central Registry is now complete
            // WHexGrid creation happens SEPARATELY in GenerateHexGridFromCompositeJobExecutor
            // ============================================================

            int registryGridCount = composition.getFeatureHexGridRegistry() != null
                ? composition.getFeatureHexGridRegistry().size() : 0;
            int fillerGridCount = registryGridCount - initialBiomeGridCount;

            resultBuilder.totalGrids(initialBiomeGridCount);

            if (fillGaps) {
                HexGridFillResult fillResult = HexGridFillResult.builder()
                    .placementResult(placementResult)
                    .totalGridCount(registryGridCount)
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
            }

            // Pipeline complete
            log.debug("=== HexComposite Pipeline Complete ===");
            log.debug("Summary: biomes={}, structures={}, points={}, flows={}, registryGrids={}, filled={}, warnings={}",
                placementResult.getPlacedBiomes().size(),
                structureResult.getPlacedCount(),
                pointResult.getComposedPoints(),
                flowResult.getComposedFlows(),
                registryGridCount,
                fillerGridCount,
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
     * Populates the central FeatureHexGrid registry from Structure features.
     * Transfers FeatureHexGrids from Structures' local storage into the central registry.
     *
     * Note: Biomes already write to central registry directly (via BiomeComposer).
     * Note: Flows already write to central registry directly (via FlowComposer).
     * Only Structures need to be transferred here.
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
                // Only Structures have local hexGrids
                // Biomes use central registry (already populated by BiomeComposer)
                // Flows write directly to central registry (no local hexGrids)
                List<FeatureHexGrid> hexGrids = null;
                if (feature instanceof de.mhus.nimbus.world.generator.composer.structure.Structure) {
                    hexGrids = ((de.mhus.nimbus.world.generator.composer.structure.Structure) feature).getHexGrids();
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

    // Note: mergeFlowAspectsIntoCentralRegistry() was removed
    // Flows now write directly to central registry, no merge needed

    /**
     * Creates WHexGrids from the central FeatureHexGrid registry of a composition.
     * This is the standard way to convert composed data to final WHexGrid format
     * after compose() has completed.
     *
     * In production, this is called by GenerateHexGridFromCompositeJobExecutor.
     * In tests, call this after compose() to get WHexGrids for visualization/assertions.
     *
     * @param composition The composition with populated central registry
     * @param worldId The world ID
     * @return List of WHexGrids created from the central registry
     */
    public static List<de.mhus.nimbus.world.shared.world.WHexGrid> createWHexGridsFromRegistry(
        HexComposition composition, String worldId) {

        Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
        if (registry == null || registry.isEmpty()) {
            log.warn("No FeatureHexGrids found in central registry");
            return new ArrayList<>();
        }

        List<de.mhus.nimbus.world.shared.world.WHexGrid> result = new ArrayList<>();
        for (FeatureHexGrid featureHexGrid : registry.values()) {
            result.add(convertFeatureHexGridToWHexGrid(featureHexGrid, worldId));
        }

        log.debug("Created {} WHexGrids from central registry", result.size());
        return result;
    }

    /**
     * Creates a WHexGrid from a FeatureHexGrid, preserving all accumulated composition data.
     *
     * @param featureHexGrid The FeatureHexGrid with accumulated data from all features
     * @param worldId The world ID
     * @return WHexGrid with all parameters and data from the FeatureHexGrid
     */
    private static de.mhus.nimbus.world.shared.world.WHexGrid convertFeatureHexGridToWHexGrid(
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
