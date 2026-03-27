package de.mhus.nimbus.world.generator.fauna;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.modelbuilder.ConditionEvaluator;
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
import de.mhus.nimbus.world.shared.dto.HeightDataDto;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
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
    private final FaunaNameService nameService;


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
        WHexGrid hexGrid = hexGridRepository.findAllByWorldIdAndPosition(worldId, position)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("HexGrid not found: " + position));

        Map<String, String> params = hexGrid.getParameters();
        String faunaType = params.get("gf_fauna");

        if (faunaType == null || faunaType.isBlank()) {
            log.info("No fauna configured for hex {},{}", hexQ, hexR);
            return 0;
        }

        // Extract hex grid context parameters (g_* -> builder, gf_* -> fauna, density etc.)
        Map<String, String> hexContext = extractHexContext(params);

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
            HeightDataDto heightInfo = getHeightDataDto(
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
            if (Strings.isNotBlank(animal.getWhen())) {
                Map<String, Object> conditionVars = buildFaunaConditionContext(
                        hexQ, hexR, seaLevel, landPositions.size(),
                        waterPositions.size(), seaPositions.size(), random, hexContext);
                if (!ConditionEvaluator.evaluate(animal.getWhen(), conditionVars)) continue;
            }

            int totalAmount = randomRange(random, animal.getAmountMin(), animal.getAmountMax());
            if (totalAmount <= 0) continue;

            // For LONER each animal is its own group
            int groupCount;
            if (animal.getGroupType() == FaunaGroupType.LONER) {
                groupCount = totalAmount;
            } else {
                groupCount = randomRange(random, animal.getGroupsMin(), animal.getGroupsMax());
            }
            if (groupCount <= 0) continue;

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

                // Generate gender and name assignments for this group
                List<AnimalIdentity> identities = generateGroupIdentities(animal, groupSize, random);

                // Create entities for each individual in the group
                for (int i = 0; i < groupSize; i++) {
                    AnimalIdentity identity = identities.get(i);
                    String shortId = UUID.randomUUID().toString().substring(0, 8);
                    String entityId = "gf_" + hexQ + "_" + hexR + "_" + animal.getName()
                            + "_" + groupIdx + "_" + shortId;

                    Entity publicData = Entity.builder()
                            .id(entityId)
                            .name(identity.displayName())
                            .gender(identity.gender().name())
                            .model(animal.getModel())
                            .solid(true)
                            .interactive(true)
                            .clientPhysics(true)
                            .build();

                    Map<String, String> serverParams = new HashMap<>();
                    serverParams.put("roam_radius", String.valueOf(animal.getRadius()));

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
                            .speed(animal.getSpeed())
                            .behaviorModel(animal.getBehaviorModel())
                            .behaviorConfig(buildBehaviorConfig(animal))
                            .server(serverParams)
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

    private Map<String, Object> buildFaunaConditionContext(
            int hexQ, int hexR, Integer seaLevel,
            int landCount, int waterCount, int seaCount,
            Random random, Map<String, String> hexContext) {
        Map<String, Object> vars = new HashMap<>();
        vars.putAll(hexContext);
        vars.put("hexQ", hexQ);
        vars.put("hexR", hexR);
        if (seaLevel != null) vars.put("seaLevel", seaLevel);
        vars.put("landCount", landCount);
        vars.put("waterCount", waterCount);
        vars.put("seaCount", seaCount);
        vars.put("random", random.nextDouble());
        return vars;
    }

    /**
     * Extract hex grid parameters as context map.
     * Strips known prefixes: g_ and gf_ (e.g. g_builder -> builder, gf_fauna -> fauna).
     */
    private static Map<String, String> extractHexContext(Map<String, String> params) {
        Map<String, String> context = new HashMap<>();
        if (params == null) return context;
        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null) continue;
            if (key.startsWith("gf_")) {
                context.put(key.substring(3), value);
            } else if (key.startsWith("g_")) {
                context.put(key.substring(2), value);
            }
        }
        return context;
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

    private HeightDataDto getHeightDataDto(String worldId, WLayer groundLayer,
                                     int worldX, int worldZ,
                                     int chunkSize, int defaultGroundLevel,
                                     Map<String, LayerChunkData> cache) {
        if (groundLayer == null) return new HeightDataDto(defaultGroundLevel, -1, null);

        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);
        String chunkKey = cx + ":" + cz;

        LayerChunkData chunkData = cache.computeIfAbsent(chunkKey, key ->
                layerService.loadTerrainChunk(worldId,
                        groundLayer.getLayerDataId(), key).orElse(null));

        if (chunkData == null) return new HeightDataDto(defaultGroundLevel, -1, null);

        // heightData keys are world coordinates "worldX,worldZ"
        String heightKey = worldX + "," + worldZ;
        int[] heightData = chunkData.getHeightData().get(heightKey);
        if (heightData != null && heightData.length >= 2) {
            int groundLevel = heightData[0];
            int waterLevel = heightData[1]; // -1 = no water
            Integer maxHeight = heightData.length > 2 ? heightData[2] : null;
            return new HeightDataDto(groundLevel, waterLevel, maxHeight);
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
        return new HeightDataDto(fallbackLevel, -1, null);
    }

    private record AnimalIdentity(FaunaGender gender, String displayName) {}

    /**
     * Generate gender and display name assignments for all animals in a group,
     * based on the animal definition's group type.
     */
    private List<AnimalIdentity> generateGroupIdentities(
            FaunaAnimalDefinition animal, int groupSize, Random random) {

        FaunaGroupType groupType = animal.getGroupType();
        List<AnimalIdentity> identities = new ArrayList<>();

        switch (groupType) {
            case LONER, MIXED -> {
                for (int i = 0; i < groupSize; i++) {
                    FaunaGender gender = pickRandomGender(animal, random);
                    String firstName = nameService.randomNameForGender(gender, random);
                    String lastName = nameService.differentNameForGender(gender, firstName, random);
                    identities.add(new AnimalIdentity(gender, firstName + " " + lastName));
                }
            }
            case HERD -> {
                FaunaGender herdGender = pickRandomGender(animal, random);
                for (int i = 0; i < groupSize; i++) {
                    String firstName = nameService.randomNameForGender(herdGender, random);
                    String lastName = nameService.differentNameForGender(herdGender, firstName, random);
                    identities.add(new AnimalIdentity(herdGender, firstName + " " + lastName));
                }
            }
            case HAREM -> {
                // Leader is always male, rest female
                String maleFirstName = nameService.randomMasculineName(random);
                String maleSurname = nameService.differentNameForGender(FaunaGender.M, maleFirstName, random);
                identities.add(new AnimalIdentity(FaunaGender.M, maleFirstName + " " + maleSurname));

                for (int i = 1; i < groupSize; i++) {
                    String femFirstName = nameService.randomFeminineName(random);
                    identities.add(new AnimalIdentity(FaunaGender.W, femFirstName + " " + maleSurname));
                }
            }
        }

        return identities;
    }

    /**
     * Pick a random gender from the allowed genders using configured weights.
     */
    private FaunaGender pickRandomGender(FaunaAnimalDefinition animal, Random random) {
        List<FaunaGender> allowed = animal.getGenders();
        if (allowed == null || allowed.isEmpty()) return FaunaGender.D;
        if (allowed.size() == 1) return allowed.getFirst();

        double totalWeight = 0;
        double[] weights = new double[allowed.size()];
        for (int i = 0; i < allowed.size(); i++) {
            double w = switch (allowed.get(i)) {
                case M -> animal.getWeightM();
                case W -> animal.getWeightW();
                case D -> animal.getWeightD();
            };
            weights[i] = w;
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < allowed.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return allowed.get(i);
        }
        return allowed.getLast();
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

    private record PositionInfo(int x, int z, HeightDataDto heightInfo, FaunaCategory category) {}
}
