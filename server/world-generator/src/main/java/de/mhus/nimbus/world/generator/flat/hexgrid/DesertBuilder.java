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
 * Desert scenario builder.
 * Creates sandy desert terrain with dunes and occasional rocky outcrops.
 * Uses HillyTerrainManipulator for base terrain with moderate to high variation.
 * Cacti and desert vegetation are added separately via flora system.
 * <p>
 * Optional parameters:
 * - stoneOffset: Height offset from ocean level where stone outcrops appear (default: 25)
 * - snowOffset: Height offset from ocean level where snow starts (default: 70)
 * - sandMaterial: Material for areas at/below ocean level (default: SAND or 4)
 * - desertSandMaterial: Material for desert surface (default: DESERT_SAND or 10)
 * - dirtMaterial: Material for occasional dirt patches (default: DIRT or 2)
 * - stoneMaterial: Material for rocky outcrops (default: STONE or 3)
 * - snowMaterial: Material for very high elevations (default: SNOW or 7)
 * - dirtRatio: Ratio of DIRT patches in desert (0.0-1.0, default: 0.05) - 5% dirt
 * - stoneRatio: Ratio of exposed STONE in rocky areas (0.0-1.0, default: 0.3) - 30% stone
 */
@Slf4j
public class DesertBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.debug("Building desert scenario for flat: {}", flat.getFlatId());

        int seaLevel = flat.getSeaLevel();

        // Use getHexGridLevel() as baseHeight and getLandOffset() as hillHeight
        int hillHeight = getOffset();
        int baseHeight = getHexGridAsl();

        long seed = context.getWorld().getNoiseSeed();
        double frequency = CastUtil.todouble(parameters.getOrDefault(HillyTerrainManipulator.PARAM_FREQUENCY, "0.7"), 0.7d);

        log.debug("Desert terrain generation: baseHeight={}, hillHeight={}, seaLevel={}, seed={}, frequency={}",
                baseHeight, hillHeight, seaLevel, seed, frequency);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));
        hillyParams.put(HillyTerrainManipulator.PARAM_FREQUENCY, String.valueOf(frequency));

        // Use HillyTerrainManipulator to generate base desert terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Set materials based on height
        setDesertMaterials(flat, seaLevel);

        log.debug("Desert scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, seaLevel);
    }

    /**
     * Set materials based on height.
     * Uses DESERT_SAND as primary surface material, with occasional DIRT and STONE.
     * Optional groundType parameter can override material settings.
     */
    private void setDesertMaterials(WFlat flat, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Apply ground type if specified (overrides individual material settings)
        applyGroundTypeIfPresent();

        // Get material thresholds from parameters (with defaults)
        int stoneOffset = parseIntParameter(parameters, "stoneOffset", 25);
        int snowOffset = parseIntParameter(parameters, "snowOffset", 70);

        // Get materials from parameters (with defaults)
        int sandMaterial = parseMaterialParameter(parameters, "sandMaterial", FlatMaterialService.SAND);
        int desertSandMaterial = parseMaterialParameter(parameters, "desertSandMaterial", FlatMaterialService.DESERT_SAND);
        int dirtMaterial = parseMaterialParameter(parameters, "dirtMaterial", FlatMaterialService.DIRT);
        int stoneMaterial = parseMaterialParameter(parameters, "stoneMaterial", FlatMaterialService.STONE);
        int snowMaterial = parseMaterialParameter(parameters, "snowMaterial", FlatMaterialService.SNOW);

        // Material ratios
        double dirtRatio = parseDoubleParameter(parameters, "dirtRatio", 0.05);  // 5% dirt patches
        double stoneRatio = parseDoubleParameter(parameters, "stoneRatio", 0.3);  // 30% stone in rocky areas

        int desertToStoneThreshold = oceanLevel + stoneOffset;
        int snowThreshold = oceanLevel + snowOffset;

        log.debug("Material thresholds: stone={}, snow={} (oceanLevel={})",
                desertToStoneThreshold, snowThreshold, oceanLevel);
        log.debug("Materials: sand={}, desertSand={}, dirt={}, stone={}, snow={}, dirtRatio={}, stoneRatio={}",
                sandMaterial, desertSandMaterial, dirtMaterial, stoneMaterial, snowMaterial, dirtRatio, stoneRatio);

        // Use seed-based random for consistent material distribution
        long seed = context.getWorld().getNoiseSeed();
        java.util.Random random = new java.util.Random(seed);

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = flat.getLevel(x, z);

                int material;
                if (level >= snowThreshold) {
                    material = snowMaterial;
                } else if (level >= desertToStoneThreshold) {
                    // Rocky area: mix of stone and desert sand
                    random.setSeed(seed + x * 1000L + z);
                    if (random.nextDouble() < stoneRatio) {
                        material = stoneMaterial;
                    } else {
                        material = desertSandMaterial;
                    }
                } else if (level <= oceanLevel) {
                    material = sandMaterial;
                } else {
                    // Desert surface: mostly desert sand with occasional dirt patches
                    random.setSeed(seed + x * 1000L + z);
                    if (random.nextDouble() < dirtRatio) {
                        material = dirtMaterial;
                    } else {
                        material = desertSandMaterial;
                    }
                }

                flat.setColumn(x, z, material);
            }
        }
    }

    @Override
    protected int getDefaultOffset() {
        return 15;  // DESERT: moderate to high dunes and hills
    }

    @Override
    protected int getDefaultAsl() {
        return 30;  // DESERT: elevated, dry terrain
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
