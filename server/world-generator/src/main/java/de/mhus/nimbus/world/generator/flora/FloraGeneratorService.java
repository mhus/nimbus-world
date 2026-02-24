package de.mhus.nimbus.world.generator.flora;

import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.modelbuilder.FloraConstraints;
import de.mhus.nimbus.world.generator.modelbuilder.ModelBuilderContext;
import de.mhus.nimbus.world.generator.modelbuilder.ModelBuilderException;
import de.mhus.nimbus.world.generator.modelbuilder.ModelBuilderService;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service for generating flora on a single hex grid.
 * Reads flora configuration from WHexGrid parameters (set by the composer/biome),
 * loads flora type definitions from WAnything, and builds plants using ModelBuilderService.
 * Supports three flora categories: LAND, WATER (freshwater), and SEA (marine).
 * Plants are selected by weight and can optionally be clustered.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FloraGeneratorService {

    private static final String FLORA_COLLECTION = "flora";
    private static final String FLORA_MODELS_COLLECTION = "flora-models";
    private static final String FLORA_LAYER_NAME = "flora";
    private static final String GROUND_LAYER_NAME = "ground";
    private static final String STACKED_MODEL = "stacked";

    private final WWorldService worldService;
    private final WHexGridRepository hexGridRepository;
    private final WAnythingService anythingService;
    private final WLayerService layerService;
    private final WDirtyChunkService dirtyChunkService;
    private final ModelBuilderService modelBuilderService;

    private record HeightInfo(int groundLevel, int waterLevel) {}

    /**
     * Generate flora for a single hex grid.
     *
     * @param worldId the world identifier
     * @param hexQ    hex axial coordinate Q
     * @param hexR    hex axial coordinate R
     * @return number of blocks placed
     */
    public int generateFlora(String worldId, int hexQ, int hexR) throws ModelBuilderException {
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new ModelBuilderException("World not found: " + worldId));

        String position = hexQ + ";" + hexR;
        WHexGrid hexGrid = hexGridRepository.findByWorldIdAndPosition(worldId, position)
                .orElseThrow(() -> new ModelBuilderException("HexGrid not found: " + position));

        Map<String, String> params = hexGrid.getParameters();
        String landFloraType = params.get("gf_flora");
        double landDensity = parseDouble(params.get("gf_density"), 0.1);
        String waterFloraType = params.get("gf_water_flora");
        double waterDensity = parseDouble(params.get("gf_water_density"), 0.1);
        String seaFloraType = params.get("gf_sea_flora");
        double seaDensity = parseDouble(params.get("gf_sea_density"), 0.1);

        boolean hasLand = landFloraType != null && !landFloraType.isBlank();
        boolean hasWater = waterFloraType != null && !waterFloraType.isBlank();
        boolean hasSea = seaFloraType != null && !seaFloraType.isBlank();

        if (!hasLand && !hasWater && !hasSea) {
            log.info("No flora configured for hex {},{}", hexQ, hexR);
            return 0;
        }

        WorldId regionWorldId = WorldId.of(worldId).orElseThrow()
                .toRegionCollection();

        Map<String, FloraTypeDefinition> floraTypeCache = new HashMap<>();

        WLayer floraLayer = layerService.findByWorldIdAndName(worldId, FLORA_LAYER_NAME)
                .orElseThrow(() -> new ModelBuilderException("Flora layer not found for world: " + worldId));

        WLayer groundLayer = layerService.findByWorldIdAndName(worldId, GROUND_LAYER_NAME)
                .orElse(null);

        int chunkSize = world.getPublicData().getChunkSize();
        int defaultGroundLevel = world.getGroundLevel();
        Integer seaLevel = world.getSeaLevel();
        Random random = new Random();
        Map<String, LayerChunkData> allChunkData = new HashMap<>();
        Map<String, LayerChunkData> groundChunkCache = new HashMap<>();
        int totalBlockCount = 0;

        for (Vector2Int flatPos : hexGrid.getFlatPositionSet(world)) {

            HeightInfo heightInfo = getHeightInfo(
                    worldId, groundLayer,
                    flatPos.getX(), flatPos.getZ(),
                    chunkSize, defaultGroundLevel, groundChunkCache);

            FloraCategory category = FloraCategory.determine(
                    heightInfo.groundLevel(), heightInfo.waterLevel(), seaLevel);

            String floraType;
            double density;
            switch (category) {
                case WATER -> { floraType = waterFloraType; density = waterDensity; }
                case SEA -> { floraType = seaFloraType; density = seaDensity; }
                default -> { floraType = landFloraType; density = landDensity; }
            }

            if (floraType == null || floraType.isBlank()) continue;
            if (random.nextDouble() >= density) continue;

            FloraTypeDefinition floraDef = floraTypeCache.computeIfAbsent(floraType, type ->
                    loadFloraTypeDefinition(regionWorldId.getId(), type));

            if (floraDef == null || floraDef.getPlants() == null || floraDef.getPlants().isEmpty()) continue;

            int waterDepth = heightInfo.waterLevel() - heightInfo.groundLevel();

            FloraPlantDefinition plant = selectPlant(floraDef, waterDepth, category, random);
            if (plant == null) continue;

            Vector3Int startPos = Vector3Int.builder()
                    .x(flatPos.getX())
                    .y(heightInfo.groundLevel() + 1)
                    .z(flatPos.getZ())
                    .build();

            totalBlockCount += buildPlantWithClustering(
                    world, floraLayer, plant, startPos, waterDepth, category, heightInfo,
                    worldId, groundLayer, chunkSize, defaultGroundLevel, groundChunkCache,
                    seaLevel, random, allChunkData);
        }

        for (Map.Entry<String, LayerChunkData> entry : allChunkData.entrySet()) {
            layerService.saveTerrainChunk(worldId, floraLayer.getLayerDataId(),
                    entry.getKey(), entry.getValue());
        }

        if (!allChunkData.isEmpty()) {
            dirtyChunkService.markChunksDirty(worldId,
                    new ArrayList<>(allChunkData.keySet()), "flora_generation");
        }

        log.info("Generated flora for hex {},{}: {} blocks in {} chunks",
                hexQ, hexR, totalBlockCount, allChunkData.size());
        return totalBlockCount;
    }

    private FloraTypeDefinition loadFloraTypeDefinition(String regionWorldId, String floraType) {
        WAnything entry = anythingService.findByWorldIdAndCollectionAndName(
                regionWorldId, FLORA_COLLECTION, floraType).orElse(null);
        if (entry == null) {
            log.warn("Flora type definition not found: {}", floraType);
            return null;
        }
        return entry.getDataAs(FloraTypeDefinition.class).orElse(null);
    }

    /**
     * Select a plant from the flora type definition that fits the position constraints.
     * Uses weight-based random selection among all fitting candidates.
     */
    private FloraPlantDefinition selectPlant(FloraTypeDefinition floraDef, int waterDepth,
                                              FloraCategory category, Random random) {
        List<FloraPlantDefinition> candidates = new ArrayList<>();
        double totalWeight = 0;

        for (FloraPlantDefinition plant : floraDef.getPlants()) {
            FloraConstraints constraints = plant.toConstraints();
            if (!constraints.fitsPosition(waterDepth, category)) continue;
            candidates.add(plant);
            totalWeight += plant.getWeight();
        }

        if (candidates.isEmpty()) return null;

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (FloraPlantDefinition candidate : candidates) {
            cumulative += candidate.getWeight();
            if (roll < cumulative) return candidate;
        }
        return candidates.getLast();
    }

    /**
     * Build a plant at the given position, optionally with clustering.
     * Returns total block count placed.
     */
    private int buildPlantWithClustering(WWorld world, WLayer floraLayer,
                                          FloraPlantDefinition plant, Vector3Int startPos,
                                          int waterDepth, FloraCategory category, HeightInfo heightInfo,
                                          String worldId, WLayer groundLayer, int chunkSize, int defaultGroundLevel,
                                          Map<String, LayerChunkData> groundChunkCache, Integer seaLevel,
                                          Random random, Map<String, LayerChunkData> allChunkData) {
        int totalBlocks = 0;

        // Build the first plant at original position
        totalBlocks += buildSinglePlant(world, floraLayer, plant, startPos, waterDepth, category, allChunkData);

        // Build cluster copies if configured
        if (plant.getClusterCount() != null && plant.getClusterCount() > 1) {
            int spread = plant.getClusterSpread();
            for (int i = 1; i < plant.getClusterCount(); i++) {
                int offsetX = random.nextInt(spread * 2 + 1) - spread;
                int offsetZ = random.nextInt(spread * 2 + 1) - spread;
                int clusterX = startPos.getX() + offsetX;
                int clusterZ = startPos.getZ() + offsetZ;

                HeightInfo clusterHeight = getHeightInfo(
                        worldId, groundLayer, clusterX, clusterZ,
                        chunkSize, defaultGroundLevel, groundChunkCache);

                FloraCategory clusterCategory = FloraCategory.determine(
                        clusterHeight.groundLevel(), clusterHeight.waterLevel(), seaLevel);
                if (clusterCategory != category) continue;

                int clusterWaterDepth = clusterHeight.waterLevel() - clusterHeight.groundLevel();
                FloraConstraints constraints = plant.toConstraints();
                if (!constraints.fitsPosition(clusterWaterDepth, clusterCategory)) continue;

                Vector3Int clusterPos = Vector3Int.builder()
                        .x(clusterX)
                        .y(clusterHeight.groundLevel() + 1)
                        .z(clusterZ)
                        .build();

                totalBlocks += buildSinglePlant(world, floraLayer, plant, clusterPos,
                        clusterWaterDepth, clusterCategory, allChunkData);
            }
        }

        return totalBlocks;
    }

    /**
     * Build a single plant at the given position. Dispatches to block stacking
     * or model building depending on the model name.
     */
    private int buildSinglePlant(WWorld world, WLayer floraLayer, FloraPlantDefinition plant,
                                  Vector3Int startPos, int waterDepth, FloraCategory category,
                                  Map<String, LayerChunkData> allChunkData) {
        try {
            Map<String, String> buildParams = new HashMap<>();
            if (plant.getParameters() != null) {
                buildParams.putAll(plant.getParameters());
            }
            if (category != FloraCategory.LAND) {
                buildParams.put("waterLevel", String.valueOf(startPos.getY() - 1 + waterDepth));
                buildParams.put("waterDepth", String.valueOf(waterDepth));
            }

            ModelBuilderContext ctx;
            if (STACKED_MODEL.equals(plant.getModel())) {
                ctx = buildBlockStack(world, floraLayer, plant.getBlocks(), startPos);
            } else {
                ctx = modelBuilderService.buildModel(world, floraLayer,
                        FLORA_MODELS_COLLECTION, plant.getModel(), startPos, buildParams);
            }

            for (Map.Entry<String, LayerChunkData> entry : ctx.getChunkDataMap().entrySet()) {
                LayerChunkData existing = allChunkData.get(entry.getKey());
                if (existing == null) {
                    allChunkData.put(entry.getKey(), entry.getValue());
                } else {
                    existing.getBlocks().addAll(entry.getValue().getBlocks());
                }
            }
            return ctx.getBlockCount();
        } catch (Exception e) {
            log.warn("Failed to build flora '{}' at ({},{},{}): {}",
                    plant.getName(), startPos.getX(), startPos.getY(), startPos.getZ(), e.getMessage());
            return 0;
        }
    }

    /**
     * Build a vertical block stack from a list of block types.
     */
    private ModelBuilderContext buildBlockStack(WWorld world, WLayer layer,
                                                List<String> blockTypes,
                                                Vector3Int startPos) throws ModelBuilderException {
        if (blockTypes == null || blockTypes.isEmpty()) {
            throw new ModelBuilderException("Block stack has no block types");
        }

        ModelBuilderContext context = ModelBuilderContext.builder()
                .world(world)
                .layer(layer)
                .position(Vector3Int.builder()
                        .x(startPos.getX())
                        .y(startPos.getY())
                        .z(startPos.getZ())
                        .build())
                .random(new Random())
                .blockCount(0)
                .build();

        for (String blockType : blockTypes) {
            context.setBlockType(blockType);
            context.paintAtCursor();
            context.incrementY();
        }

        return context;
    }

    private HeightInfo getHeightInfo(String worldId, WLayer groundLayer,
                                     int worldX, int worldZ,
                                     int chunkSize, int defaultGroundLevel,
                                     Map<String, LayerChunkData> cache) {
        if (groundLayer == null) return new HeightInfo(defaultGroundLevel, defaultGroundLevel);

        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);
        String chunkKey = cx + ":" + cz;

        LayerChunkData chunkData = cache.computeIfAbsent(chunkKey, key ->
                layerService.loadTerrainChunk(worldId,
                        groundLayer.getLayerDataId(), key).orElse(null));

        if (chunkData == null) return new HeightInfo(defaultGroundLevel, defaultGroundLevel);

        int localX = Math.floorMod(worldX, chunkSize);
        int localZ = Math.floorMod(worldZ, chunkSize);
        String heightKey = localX + "," + localZ;
        int[] heightData = chunkData.getHeightData().get(heightKey);
        if (heightData != null && heightData.length > 2) {
            int groundLevel = heightData[2];
            int waterLevel = heightData.length > 3 ? heightData[3] : groundLevel;
            return new HeightInfo(groundLevel, waterLevel);
        }

        // Fallback: find max Y from blocks at this position
        int maxY = Integer.MIN_VALUE;
        for (LayerBlock lb : chunkData.getBlocks()) {
            var blockPos = lb.getBlock().getPosition();
            if (blockPos.getX() == worldX && blockPos.getZ() == worldZ) {
                maxY = Math.max(maxY, blockPos.getY());
            }
        }

        int fallbackLevel = maxY > Integer.MIN_VALUE ? maxY : defaultGroundLevel;
        return new HeightInfo(fallbackLevel, fallbackLevel);
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
