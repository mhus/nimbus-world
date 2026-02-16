package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
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

    private static final double SQRT3 = Math.sqrt(3);
    private static final double SQRT3_HALF = SQRT3 / 2.0;

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
        double radius1 = hexGridSize / 2.0;
        double radius2 = radius1 - 1;
        double radius3 = radius1 - 2;
        double radiusOut = radius1 + 1;

        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;

        log.debug("DebugBuilder: sizeX={}, sizeZ={}, level={}, hexGridSize={}, radius1={}, center=({}, {})",
                sizeX, sizeZ, baseLevel, hexGridSize, radius1, centerX, centerZ);
        log.debug("DebugBuilder materials: base={}, circle1={}, circle2={}, circle3={}",
                baseMaterial, circle1Material, circle2Material, circle3Material);

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {

                double dx = x - centerX;
                double dz = z - centerZ;

                int material;
                int level;
                if (isInsidePointyTopHex(dx, dz, radius3)) {
                    material = baseMaterial;
                    level = baseLevel;
                } else if (isInsidePointyTopHex(dx, dz, radius2)) {
                    material = circle1Material;
                    level = baseLevel - 20; // Raise outer hex by 3 level for better visibility
                } else if (isInsidePointyTopHex(dx, dz, radius1)) {
                    material = circle2Material;
                    level = baseLevel - 40; // Raise middle hex by 2 level for better visibility
                } else if (isInsidePointyTopHex(dx, dz, radiusOut)) {
                    material = circle3Material;
                    level = baseLevel - 60;
                } else {
                    material = circleOutMaterial;
                    level = baseLevel; // Raise inner hex by 1 level for better visibility
                }

                flat.setLevel(x, z, level);
                flat.setColumn(x, z, material);
            }
        }

        log.debug("DebugBuilder completed: {} x {} blocks filled", sizeX, sizeZ);
    }

    /**
     * Check if a point (dx, dz) relative to hex center is inside a pointy-top hexagon
     * with circumradius R (center to vertex distance).
     * <p>
     * Pointy-top hex: vertices at top/bottom (pointy), flat edges at left/right.
     * Vertices: (0,R), (√3/2·R, R/2), (√3/2·R, -R/2), (0,-R), (-√3/2·R, -R/2), (-√3/2·R, R/2)
     * <p>
     * Three edge-pair constraints, simplified with absolute values:
     * 1. |dx| <= √3/2 * R (left/right flat edges, inradius constraint)
     * 2. |dx| + √3 * |dz| <= √3 * R (diagonal edges)
     */
    private boolean isInsidePointyTopHex(double dx, double dz, double radius) {
        double adx = Math.abs(dx);
        double adz = Math.abs(dz);
        return adx <= SQRT3_HALF * radius && adx + SQRT3 * adz <= SQRT3 * radius;
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
