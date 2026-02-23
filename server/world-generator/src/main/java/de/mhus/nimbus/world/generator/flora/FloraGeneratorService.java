package de.mhus.nimbus.world.generator.flora;

import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.WorldId;
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
 * loads flora descriptors from WAnything, and builds them using ModelBuilderService.
 * Supports three flora categories: LAND, WATER (freshwater), and SEA (marine).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FloraGeneratorService {

    private static final String FLORA_COLLECTION = "flora";
    private static final String FLORA_MODELS_COLLECTION = "flora_models";
    private static final String FLORA_LAYER_NAME = "flora";
    private static final String GROUND_LAYER_NAME = "ground";

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

        Map<String, List<WAnything>> floraEntriesCache = new HashMap<>();

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

            List<WAnything> floraEntries = floraEntriesCache.computeIfAbsent(floraType, type ->
                    anythingService.findByWorldIdAndCollectionAndType(
                            regionWorldId.getId(), FLORA_COLLECTION, type));

            if (floraEntries.isEmpty()) continue;

            WAnything floraEntry = floraEntries.get(random.nextInt(floraEntries.size()));
            String descriptor = floraEntry.getName();

            Vector3Int startPos = Vector3Int.builder()
                    .x(flatPos.getX())
                    .y(heightInfo.groundLevel() + 1)
                    .z(flatPos.getZ())
                    .build();

            try {
                ModelBuilderContext ctx = modelBuilderService.buildFromDescriptor(
                        world, floraLayer, descriptor, FLORA_MODELS_COLLECTION, startPos);

                for (Map.Entry<String, LayerChunkData> entry : ctx.getChunkDataMap().entrySet()) {
                    LayerChunkData existing = allChunkData.get(entry.getKey());
                    if (existing == null) {
                        allChunkData.put(entry.getKey(), entry.getValue());
                    } else {
                        existing.getBlocks().addAll(entry.getValue().getBlocks());
                    }
                }
                totalBlockCount += ctx.getBlockCount();
            } catch (Exception e) {
                log.warn("Failed to build flora '{}' at ({},{},{}): {}",
                        descriptor, flatPos.getX(), heightInfo.groundLevel() + 1, flatPos.getZ(), e.getMessage());
            }
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
