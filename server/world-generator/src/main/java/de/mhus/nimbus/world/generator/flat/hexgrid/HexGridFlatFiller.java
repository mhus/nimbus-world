package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WWorld;

public class HexGridFlatFiller {
    private final WFlat flat;
    private final BuilderContext context;

    public HexGridFlatFiller(WFlat flat, BuilderContext context) {
        this.flat = flat;
        this.context = context;
    }

    public void fillFlat() {
        WWorld world = context.getWorld();
        int groundLevel = world.getGroundLevel();
        for (int x = 0; x < flat.getSizeX(); x++) {
            for (int z = 0; z < flat.getSizeZ(); z++) {
                var material = flat.getColumn(x, z);
                if (material == WFlat.MATERIAL_NOT_SET) {
                    continue;
                }
                var level = flat.getLevel(x, z);
                if (level == WFlat.LEVEL_NOT_SET) {
                    int average = getAverageAround(x,z);
                    if (average > 0) {
                        flat.setLevel(x, z, average);
                    } else {
                        // Fallback to world ground level
                        flat.setLevel(x, z, groundLevel);
                    }
                }
            }
        }
    }

    private int getAverageAround(int x, int z) {
        int sum = 0;
        int count = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                int nx = x + i;
                int nz = z + j;
                if (nx < 0 || nz < 0 || nx >= flat.getSizeX() || nz >= flat.getSizeZ()) continue;
                var level = flat.getLevel(nx, nz);
                if (level != WFlat.LEVEL_NOT_SET) {
                    sum += level;
                    count++;
                }
            }
        }
        if (count > 0) {
            return sum / count;
        } else
            return -1;
    }
}
