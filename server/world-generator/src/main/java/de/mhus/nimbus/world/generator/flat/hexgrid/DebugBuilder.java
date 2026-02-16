package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

/**
 * Debug builder for hex grid flats.
 * Fills the entire flat with a constant level and draws three concentric
 * pointy-top hexagons in the center using configurable materials.
 * Useful for verifying hex creation and export alignment.
 * <p>
 * Parameters (g_ prefix stripped by HexGridBuilderService):
 * - level: Height level for all blocks (default: hexGridAsl)
 * - base: Base material filling the entire area (default: GRASS = 1)
 * - circle1: Outer hexagon material (default: DIRT = 2), radius = hexGridSize/2
 * - circle2: Middle hexagon material (default: STONE = 3), radius = hexGridSize/2 - 1
 * - circle3: Inner hexagon material (default: SAND = 4), radius = hexGridSize/2 - 2
 * - circleOutMaterial: Outer ring material (default: SNOW = 5), radius = hexGridSize/2 + 1
 */
@Slf4j
public class DebugBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        int baseLevel = CastUtil.toint(parameters.get("level"), getHexGridAsl());
        int baseMaterial = CastUtil.toint(parameters.get("base"), FlatMaterialService.GRASS);
        int circle1Material = CastUtil.toint(parameters.get("circle1"), FlatMaterialService.DIRT);
        int circle2Material = CastUtil.toint(parameters.get("circle2"), FlatMaterialService.STONE);
        int circle3Material = CastUtil.toint(parameters.get("circle3"), FlatMaterialService.SAND);
        int circleOutMaterial = CastUtil.toint(parameters.get("circleOut"), FlatMaterialService.SNOW);

        int hexGridSize = context.getHexGridSize();
        // Concentric hexagons as diameters for isPointInHex
        int gridSize3 = hexGridSize - 6;   // innermost
        int gridSize2 = hexGridSize - 4;   // middle
        int gridSize1 = hexGridSize - 2;       // hex boundary
        int gridSizeOut = hexGridSize;  // outer ring

        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;

        log.debug("DebugBuilder: sizeX={}, sizeZ={}, level={}, hexGridSize={}, center=({}, {})",
                sizeX, sizeZ, baseLevel, hexGridSize, centerX, centerZ);
        log.debug("DebugBuilder materials: base={}, circle1={}, circle2={}, circle3={}",
                baseMaterial, circle1Material, circle2Material, circle3Material);

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {

                int material;
                int level;
                if (HexMathUtil.isPointInHex(x, z, centerX, centerZ, gridSize3)) {
                    material = baseMaterial;
                    level = baseLevel;
                } else if (HexMathUtil.isPointInHex(x, z, centerX, centerZ, gridSize2)) {
                    material = circle1Material;
                    level = baseLevel - 20;
                } else if (HexMathUtil.isPointInHex(x, z, centerX, centerZ, gridSize1)) {
                    material = circle2Material;
                    level = baseLevel - 40;
                } else if (HexMathUtil.isPointInHex(x, z, centerX, centerZ, gridSizeOut)) {
                    material = circle3Material;
                    level = baseLevel - 60;
                } else {
                    material = circleOutMaterial;
                    level = baseLevel;
                }

                flat.setLevel(x, z, level);
                flat.setColumn(x, z, material);
            }
        }

        log.debug("DebugBuilder completed: {} x {} blocks filled", sizeX, sizeZ);
    }

    @Override
    protected int getDefaultOffset() {
        return 0;
    }

    @Override
    protected int getDefaultAsl() {
        return 20;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }
}
