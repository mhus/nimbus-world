package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.manipulator.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Forest scenario builder.
 * Creates gently rolling forest terrain with grass and dirt ground.
 * Uses HillyTerrainManipulator for base terrain with low variation.
 * Trees and vegetation are added separately via flora system.
 * <p>
 * Optional parameters:
 * - stoneOffset: Height offset from ocean level where stone starts (default: 30)
 * - snowOffset: Height offset from ocean level where snow starts (default: 60)
 * - sandMaterial: Material for areas at/below ocean level (default: SAND or 4)
 * - grassMaterial: Material for low elevations (default: GRASS or 1)
 * - dirtMaterial: Material for medium elevations (default: DIRT or 2)
 * - stoneMaterial: Material for high elevations (default: STONE or 3)
 * - snowMaterial: Material for very high elevations (default: SNOW or 7)
 * - dirtRatio: Ratio of DIRT vs GRASS (0.0-1.0, default: 0.3) - 30% dirt, 70% grass
 */
@Slf4j
public class ForestBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.debug("Building forest scenario for flat: {}", flat.getFlatId());

        int seaLevel = flat.getSeaLevel();

        // Use getHexGridLevel() as baseHeight and getLandOffset() as hillHeight
        int hillHeight = getOffset();
        int baseHeight = getHexGridAsl();

        long seed = context.getWorld().getNoiseSeed();
        double frequency = CastUtil.todouble(parameters.getOrDefault(HillyTerrainManipulator.PARAM_FREQUENCY, "0.6"), 0.6d);

        log.debug("Forest terrain generation: baseHeight={}, hillHeight={}, seaLevel={}, seed={}, frequency={}",
                baseHeight, hillHeight, seaLevel, seed, frequency);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));
        hillyParams.put(HillyTerrainManipulator.PARAM_FREQUENCY, String.valueOf(frequency));

        // Use HillyTerrainManipulator to generate base forest terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Set materials based on height
        setForestMaterials(flat, seaLevel);

        log.debug("Forest scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, seaLevel);
    }

    /**
     * Set materials based on height.
     * Uses GRASS and DIRT as primary ground materials, with some variation.
     * Optional groundType parameter can override material settings.
     */
    private void setForestMaterials(WFlat flat, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Apply ground type if specified (overrides individual material settings)
        applyGroundTypeIfPresent();

        // Get material thresholds from parameters (with defaults)
        int stoneOffset = parseIntParameter(parameters, "stoneOffset", 30);
        int snowOffset = parseIntParameter(parameters, "snowOffset", 60);

        // Get materials from parameters (with defaults)
        int sandMaterial = parseMaterialParameter(parameters, "sandMaterial", FlatMaterialService.SAND);
        int grassMaterial = parseMaterialParameter(parameters, "grassMaterial", FlatMaterialService.GRASS);
        int dirtMaterial = parseMaterialParameter(parameters, "dirtMaterial", FlatMaterialService.DIRT);
        int stoneMaterial = parseMaterialParameter(parameters, "stoneMaterial", FlatMaterialService.STONE);
        int snowMaterial = parseMaterialParameter(parameters, "snowMaterial", FlatMaterialService.SNOW);

        // Dirt ratio: how much of the grass areas should be dirt (0.0-1.0)
        double dirtRatio = parseDoubleParameter(parameters, "dirtRatio", 0.3);

        int grassToStoneThreshold = oceanLevel + stoneOffset;
        int snowThreshold = oceanLevel + snowOffset;

        log.debug("Material thresholds: stone={}, snow={} (oceanLevel={})",
                grassToStoneThreshold, snowThreshold, oceanLevel);
        log.debug("Materials: sand={}, grass={}, dirt={}, stone={}, snow={}, dirtRatio={}",
                sandMaterial, grassMaterial, dirtMaterial, stoneMaterial, snowMaterial, dirtRatio);

        // Use seed-based random for consistent dirt/grass distribution
        long seed = context.getWorld().getNoiseSeed();
        java.util.Random random = new java.util.Random(seed);

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = flat.getLevel(x, z);

                int material;
                if (level >= snowThreshold) {
                    material = snowMaterial;
                } else if (level >= grassToStoneThreshold) {
                    material = stoneMaterial;
                } else if (level <= oceanLevel) {
                    material = sandMaterial;
                } else {
                    // Forest floor: mix of grass and dirt
                    // Use position-based seed for consistent pattern
                    random.setSeed(seed + x * 1000L + z);
                    if (random.nextDouble() < dirtRatio) {
                        material = dirtMaterial;
                    } else {
                        material = grassMaterial;
                    }
                }

                flat.setColumn(x, z, material);
            }
        }
    }

    @Override
    protected int getDefaultOffset() {
        return 5;  // FOREST: gentle rolling hills
    }

    @Override
    protected int getDefaultAsl() {
        return 20;  // FOREST: moderate elevation
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid int parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    private double parseDoubleParameter(Map<String, String> parameters, String name, double defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid double parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse material parameter. Accepts either material name (e.g. "SNOW", "GRASS") or material ID (e.g. "7", "1").
     */
    private int parseMaterialParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        // Try to parse as integer first
        try {
            int materialId = Integer.parseInt(value);
            if (materialId >= 0 && materialId <= 255) {
                return materialId;
            }
            log.warn("Material ID out of range (0-255) for '{}': {}, using default: {}", name, value, defaultValue);
            return defaultValue;
        } catch (NumberFormatException e) {
            // Not a number, try to parse as material name
            return parseMaterialName(value, name, defaultValue);
        }
    }

    /**
     * Parse material name to material ID.
     */
    private int parseMaterialName(String name, String paramName, int defaultValue) {
        switch (name.toUpperCase().trim()) {
            case "GRASS":
                return FlatMaterialService.GRASS;
            case "DIRT":
                return FlatMaterialService.DIRT;
            case "STONE":
                return FlatMaterialService.STONE;
            case "SAND":
                return FlatMaterialService.SAND;
            case "WATER":
                return FlatMaterialService.WATER;
            case "BEDROCK":
                return FlatMaterialService.BEDROCK;
            case "SNOW":
                return FlatMaterialService.SNOW;
            case "INVISIBLE":
                return FlatMaterialService.INVISIBLE;
            case "INVISIBLE_SOLID":
                return FlatMaterialService.INVISIBLE_SOLID;
            case "DESERT_SAND":
                return FlatMaterialService.DESERT_SAND;
            case "SWAMP":
                return FlatMaterialService.SWAMP;
            case "ICE":
                return FlatMaterialService.ICE;
            default:
                log.warn("Unknown material name for '{}': {}, using default: {}", paramName, name, defaultValue);
                return defaultValue;
        }
    }

    /**
     * Apply ground type materials if groundType parameter is present.
     * This allows direct specification of ground type in builder parameters.
     */
    private void applyGroundTypeIfPresent() {
        if (parameters == null || !parameters.containsKey("groundType")) {
            return;
        }

        String groundTypeStr = parameters.get("groundType");
        if (groundTypeStr == null || groundTypeStr.isBlank()) {
            return;
        }

        try {
            de.mhus.nimbus.world.generator.composer.biome.GroundType groundType =
                de.mhus.nimbus.world.generator.composer.biome.GroundType.valueOf(groundTypeStr.toUpperCase());
            groundType.applyToParameters(parameters);
            log.debug("Applied ground type: {}", groundType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ground type '{}', using defaults", groundTypeStr);
        }
    }
}
