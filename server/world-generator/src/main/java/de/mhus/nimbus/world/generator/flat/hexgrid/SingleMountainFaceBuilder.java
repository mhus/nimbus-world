package de.mhus.nimbus.world.generator.flat.hexgrid;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.point.MountainFacePoint;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.manipulator.SpiderPatternManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * SingleMountainFaceBuilder builds a steep mountain face from MountainFacePoint configuration.
 * Creates a spider-pattern cliff face with branching ridges radiating from center.
 *
 * This builder reads the g_mountain_face parameter and uses SpiderPatternManipulator
 * to create the cliff face with appropriate branching and height.
 */
@Slf4j
public class SingleMountainFaceBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        // Get actual hexGridSize from world context
        int hexGridSize = context.getHexGridSize();

        log.debug("Building mountain face for flat: {} with hexGridSize: {}", flat.getFlatId(), hexGridSize);

        // Get mountain face parameter from hex grid
        String faceParam = hexGrid.getParameters() != null ?
            hexGrid.getParameters().get("g_mountain_face") : null;

        if (faceParam == null || faceParam.isBlank()) {
            log.debug("No mountain face parameter found, skipping");
            return;
        }

        try {
            // Parse mountain face configuration
            MountainFacePoint.MountainFaceConfig config = objectMapper.readValue(
                faceParam, MountainFacePoint.MountainFaceConfig.class);

            log.debug("Parsed mountain face config for '{}': dimension={}, branches={}, height={}",
                config.getFaceName(), config.getDimension(), config.getBranches(), config.getFaceHeight());

            // Build the mountain face
            buildMountainFace(flat, config, hexGridSize);

            log.debug("Mountain face '{}' completed", config.getFaceName());

        } catch (Exception e) {
            log.error("Failed to build mountain face for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Build a mountain face using SpiderPatternManipulator
     */
    private void buildMountainFace(WFlat flat, MountainFacePoint.MountainFaceConfig config, int hexGridSize) {
        // Get material
        int material = getMaterialForType(config.getMaterial());

        // Calculate the region to manipulate (entire flat)
        int regionX = 0;
        int regionZ = 0;
        int regionSizeX = flat.getSizeX();
        int regionSizeZ = flat.getSizeZ();

        // Center of the flat
        int centerX = regionSizeX / 2;
        int centerZ = regionSizeZ / 2;

        log.debug("Building mountain face at center ({}, {}) with branches={}, length={}, height={}",
            centerX, centerZ, config.getBranches(), config.getBranchLength(), config.getFaceHeight());

        // Create parameters map for SpiderPatternManipulator
        Map<String, String> manipulatorParams = new HashMap<>();
        manipulatorParams.put(SpiderPatternManipulator.PARAM_CENTER_X, String.valueOf(centerX));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_CENTER_Z, String.valueOf(centerZ));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_BRANCHES, String.valueOf(config.getBranches()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_LENGTH, String.valueOf(config.getBranchLength()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_HEIGHT_DELTA, String.valueOf(config.getFaceHeight()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_SUB_BRANCHES, String.valueOf(config.getSubBranches()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_RECURSION_DEPTH, String.valueOf(config.getRecursionDepth()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_SEED, String.valueOf(config.getSeed()));
        manipulatorParams.put(SpiderPatternManipulator.PARAM_MATERIAL, String.valueOf(material));

        // Create SpiderPatternManipulator and apply
        SpiderPatternManipulator manipulator = new SpiderPatternManipulator();
        manipulator.manipulate(flat, regionX, regionZ, regionSizeX, regionSizeZ, manipulatorParams);

        log.info("Mountain face built: name='{}', dimension={}, branches={}, length={}, height={}, material={}",
            config.getFaceName(), config.getDimension(), config.getBranches(),
            config.getBranchLength(), config.getFaceHeight(), config.getMaterial());
    }

    /**
     * Get material ID for mountain face type
     */
    private int getMaterialForType(String materialType) {
        if (materialType == null || materialType.isBlank()) {
            return FlatMaterialService.STONE;
        }

        return switch (materialType.toLowerCase()) {
            case "stone" -> FlatMaterialService.STONE;
            case "sandstone", "sand" -> FlatMaterialService.SAND;
            case "granite", "basalt" -> FlatMaterialService.STONE;
            case "snow", "ice" -> FlatMaterialService.SNOW;
            case "grass" -> FlatMaterialService.GRASS;
            default -> FlatMaterialService.STONE;
        };
    }

    @Override
    protected int getDefaultOffset() {
        return 0;
    }

    @Override
    protected int getDefaultAsl() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }
}
