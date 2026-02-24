package de.mhus.nimbus.world.generator.fauna;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Service for generating fauna (animals) on a single hex grid.
 * Reads fauna configuration from WHexGrid parameters (gf_fauna),
 * loads fauna type definitions from WAnything, and creates WEntity instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaunaGeneratorService {

    private static final String FAUNA_COLLECTION = "fauna";
    private static final String GROUND_LAYER_NAME = "ground";
    private static final int MAX_POSITION_ATTEMPTS = 50;
    public static final String SOURCE = "fauna-generator";

    private final WWorldService worldService;
    private final WHexGridRepository hexGridRepository;
    private final WAnythingService anythingService;
    private final WLayerService layerService;
    private final WEntityService entityService;

    private record HeightInfo(int groundLevel, int waterLevel) {}

    /**
     * Generate fauna for a single hex grid.
     *
     * @param worldId the world identifier
     * @param hexQ    hex axial coordinate Q
     * @param hexR    hex axial coordinate R
     * @return number of entities created
     */
    public int generateFauna(String worldId, int hexQ, int hexR) {
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new RuntimeException("World not found: " + worldId));

        String position = hexQ + ";" + hexR;
        WHexGrid hexGrid = hexGridRepository.findByWorldIdAndPosition(worldId, position)
                .orElseThrow(() -> new RuntimeException("HexGrid not found: " + position));

        Map<String, String> params = hexGrid.getParameters();
        String faunaType = params.get("gf_fauna");

        if (faunaType == null || faunaType.isBlank()) {
            log.info("No fauna configured for hex {},{}", hexQ, hexR);
            return 0;
        }

        WorldId wid = WorldId.of(worldId).orElseThrow();
        WorldId regionWorldId = wid.toRegionCollection();

        FaunaTypeDefinition faunaDef = loadFaunaTypeDefinition(regionWorldId.getId(), faunaType);
        if (faunaDef == null || faunaDef.getAnimals() == null || faunaDef.getAnimals().isEmpty()) {
            log.info("Fauna type '{}' has no animals for hex {},{}", faunaType, hexQ, hexR);
            return 0;
        }

        // Delete existing generated fauna entities in this hex grid's chunks
        deleteEntitiesInHexGrid(worldId, hexGrid, world);

        // Load ground layer for height lookup
        WLayer groundLayer = layerService.findByWorldIdAndName(worldId, GROUND_LAYER_NAME)
                .orElse(null);

        int chunkSize = world.getPublicData().getChunkSize();
        int defaultGroundLevel = world.getGroundLevel();
        Integer seaLevel = world.getSeaLevel();
        Random random = new Random();
        Map<String, LayerChunkData> groundChunkCache = new HashMap<>();

        // Collect all flat positions and categorize them
        List<PositionInfo> landPositions = new ArrayList<>();
        List<PositionInfo> waterPositions = new ArrayList<>();
        List<PositionInfo> seaPositions = new ArrayList<>();

        for (Vector2Int flatPos : hexGrid.getFlatPositionSet(world)) {
            HeightInfo heightInfo = getHeightInfo(
                    worldId, groundLayer,
                    flatPos.getX(), flatPos.getZ(),
                    chunkSize, defaultGroundLevel, groundChunkCache);

            FaunaCategory category = FaunaCategory.determine(
                    heightInfo.groundLevel(), heightInfo.waterLevel(), seaLevel);

            PositionInfo posInfo = new PositionInfo(flatPos.getX(), flatPos.getZ(), heightInfo, category);
            switch (category) {
                case LAND -> landPositions.add(posInfo);
                case WATER -> waterPositions.add(posInfo);
                case SEA -> seaPositions.add(posInfo);
                default -> {} // AERIAL not determined by position
            }
        }

        // Shuffle for random selection
        Collections.shuffle(landPositions, random);
        Collections.shuffle(waterPositions, random);
        Collections.shuffle(seaPositions, random);

        List<WEntity> allEntities = new ArrayList<>();

        for (FaunaAnimalDefinition animal : faunaDef.getAnimals()) {
            int totalAmount = randomRange(random, animal.getAmountMin(), animal.getAmountMax());
            int groupCount = randomRange(random, animal.getGroupsMin(), animal.getGroupsMax());

            if (totalAmount <= 0 || groupCount <= 0) continue;

            // Distribute individuals across groups
            int basePerGroup = totalAmount / groupCount;
            int remainder = totalAmount % groupCount;

            for (int groupIdx = 0; groupIdx < groupCount; groupIdx++) {
                int groupSize = basePerGroup + (groupIdx < remainder ? 1 : 0);
                if (groupSize <= 0) continue;

                // Find a suitable group position
                PositionInfo groupPosition = findGroupPosition(animal, landPositions, waterPositions,
                        seaPositions, random);
                if (groupPosition == null) {
                    log.debug("No suitable position found for animal '{}' group {}", animal.getName(), groupIdx);
                    continue;
                }

                // Calculate Y based on category
                double y;
                if (animal.isAerial()) {
                    y = groupPosition.heightInfo.groundLevel() + 1 + animal.getHeight();
                } else if (groupPosition.category == FaunaCategory.LAND) {
                    y = groupPosition.heightInfo.groundLevel() + 1;
                } else {
                    // Water/Sea: position at water level
                    y = groupPosition.heightInfo.waterLevel();
                }

                Vector3 middlePoint = Vector3.builder()
                        .x(groupPosition.x)
                        .y(y)
                        .z(groupPosition.z)
                        .build();

                // Create entities for each individual in the group
                for (int i = 0; i < groupSize; i++) {
                    String shortId = UUID.randomUUID().toString().substring(0, 8);
                    String entityId = "gf-" + hexQ + ";" + hexR + "-" + animal.getName()
                            + "-" + groupIdx + "-" + shortId;

                    Entity publicData = Entity.builder()
                            .id(entityId)
                            .name(entityId)
                            .model(animal.getModel())
                            .solid(true)
                            .interactive(true)
                            .clientPhysics(true)
                            .build();

                    WEntity entity = WEntity.builder()
                            .worldId(worldId)
                            .entityId(entityId)
                            .publicData(publicData)
                            .modelId(animal.getModel())
                            .position(Vector3.builder()
                                    .x(middlePoint.getX())
                                    .y(middlePoint.getY())
                                    .z(middlePoint.getZ())
                                    .build())
                            .middlePoint(middlePoint)
                            .radius(animal.getRadius())
                            .speed(animal.getSpeed())
                            .behaviorModel(animal.getBehaviorModel())
                            .behaviorConfig(buildBehaviorConfig(animal))
                            .source(SOURCE)
                            .enabled(true)
                            .build();

                    allEntities.add(entity);
                }
            }
        }

        if (!allEntities.isEmpty()) {
            entityService.saveAll(allEntities);
        }

        log.info("Generated fauna for hex {},{}: {} entities", hexQ, hexR, allEntities.size());
        return allEntities.size();
    }

    /**
     * Delete all fauna-generator entities whose affectedChunks overlap with the given hex grid.
     * Can be called independently to clean up fauna in a hex grid without regenerating.
     *
     * @param worldId the world identifier string
     * @param hexGrid the hex grid whose chunk area should be cleared
     * @param world   the world entity (for chunk size calculation)
     * @return number of deleted entities
     */
    public int deleteEntitiesInHexGrid(String worldId, WHexGrid hexGrid, WWorld world) {
        WorldId wid = WorldId.of(worldId).orElseThrow();
        var chunkKeys = hexGrid.getAffectedChunkKeys(world);
        if (chunkKeys.isEmpty()) return 0;
        int deleted = entityService.deleteBySourceAndAffectedChunks(wid, SOURCE, chunkKeys);
        if (deleted > 0) {
            log.info("Deleted {} fauna entities in hex grid {}", deleted, hexGrid.getPosition());
        }
        return deleted;
    }

    private FaunaTypeDefinition loadFaunaTypeDefinition(String regionWorldId, String faunaType) {
        WAnything entry = anythingService.findByWorldIdAndCollectionAndName(
                regionWorldId, FAUNA_COLLECTION, faunaType).orElse(null);
        if (entry == null) {
            log.warn("Fauna type definition not found: {}", faunaType);
            return null;
        }
        return entry.getDataAs(FaunaTypeDefinition.class).orElse(null);
    }

    /**
     * Find a suitable group position for an animal based on its category flags.
     * Tries positions from pre-categorized lists, with a maximum attempt limit.
     */
    private PositionInfo findGroupPosition(FaunaAnimalDefinition animal,
                                            List<PositionInfo> landPositions,
                                            List<PositionInfo> waterPositions,
                                            List<PositionInfo> seaPositions,
                                            Random random) {
        // Collect candidate lists based on animal flags
        List<List<PositionInfo>> candidateLists = new ArrayList<>();
        if (animal.isLand() || animal.isAerial()) candidateLists.add(landPositions);
        if (animal.isWater()) candidateLists.add(waterPositions);
        if (animal.isSea()) candidateLists.add(seaPositions);

        if (candidateLists.isEmpty()) return null;

        // Try random positions from the candidate lists
        for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS; attempt++) {
            List<PositionInfo> list = candidateLists.get(random.nextInt(candidateLists.size()));
            if (list.isEmpty()) continue;
            return list.get(random.nextInt(list.size()));
        }

        // Fallback: return first available from any matching list
        for (List<PositionInfo> list : candidateLists) {
            if (!list.isEmpty()) return list.getFirst();
        }

        return null;
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

    private Map<String, Object> buildBehaviorConfig(FaunaAnimalDefinition animal) {
        Map<String, Object> config = new HashMap<>();
        // Default movement parameters
        config.put("minStepDistance", 2);
        config.put("maxStepDistance", 5);
        config.put("waypointsPerPath", 6);
        config.put("minIdleDuration", 1000);
        config.put("maxIdleDuration", 3000);
        config.put("pathwayInterval", 5000);
        // Override with animal-specific config
        if (animal.getBehaviorConfig() != null) {
            config.putAll(animal.getBehaviorConfig());
        }
        return config;
    }

    private static int randomRange(Random random, int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private record PositionInfo(int x, int z, HeightInfo heightInfo, FaunaCategory category) {}
}
