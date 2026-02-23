package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.shared.types.BlockDef;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for block painting with geometric methods.
 * Delegates actual block writing to a {@link BlockWriteTarget}.
 * Subclasses can override {@link #paint(int, int, int)} to add decorator chains.
 */
public class EditBlockPainter {

    @Getter @Setter
    private BlockWriteTarget writeTarget;

    @Getter @Setter
    private BlockDef blockDef;

    @Getter @Setter
    private String groupId;

    @Getter @Setter
    private Integer level;

    /**
     * Paint a single block at the given position.
     * Can be overridden by subclasses (e.g., for decorator chains).
     */
    public void paint(int x, int y, int z) {
        writeTarget.writeBlock(blockDef, groupId, level, x, y, z);
    }

    // --- Geometric methods ---

    public void cube(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    paint(x + dx, y + dy, z + dz);
                }
            }
        }
    }

    public void cubeOutline(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        int xMax = x + sizeX - 1;
        int yMax = y + sizeY - 1;
        int zMax = z + sizeZ - 1;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                paint(x + dx, y, z + dz);
                if (y != yMax) paint(x + dx, yMax, z + dz);
            }
        }
        for (int dy = 1; dy < sizeY - 1; dy++) {
            for (int dx = 0; dx < sizeX; dx++) {
                paint(x + dx, y + dy, z);
                if (z != zMax) paint(x + dx, y + dy, zMax);
            }
            for (int dz = 1; dz < sizeZ - 1; dz++) {
                paint(x, y + dy, z + dz);
                if (x != xMax) paint(xMax, y + dy, z + dz);
            }
        }
    }

    public void line(int x1, int y1, int z1, int x2, int y2, int z2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) {
            paint(x1, y1, z1);
            return;
        }
        double x = x1;
        double y = y1;
        double z = z1;
        double xInc = dx / (double) steps;
        double yInc = dy / (double) steps;
        double zInc = dz / (double) steps;
        for (int i = 0; i <= steps; i++) {
            int xi = (int) Math.round(x);
            int yi = (int) Math.round(y);
            int zi = (int) Math.round(z);
            paint(xi, yi, zi);
            x += xInc;
            y += yInc;
            z += zInc;
        }
    }

    public void rectangleOutlineY(int x, int y, int z, int sizeX, int sizeZ) {
        int xMax = x + sizeX - 1;
        int zMax = z + sizeZ - 1;
        for (int xi = x; xi <= xMax; xi++) {
            paint(xi, y, z);
            if (z != zMax) paint(xi, y, zMax);
        }
        for (int zi = z + 1; zi < zMax; zi++) {
            paint(x, y, zi);
            if (x != xMax) paint(xMax, y, zi);
        }
    }

    public void rectangleOutlineX(int x, int y, int z, int sizeY, int sizeZ) {
        int yMax = y + sizeY - 1;
        int zMax = z + sizeZ - 1;
        for (int yi = y; yi <= yMax; yi++) {
            paint(x, yi, z);
            if (z != zMax) paint(x, yi, zMax);
        }
        for (int zi = z + 1; zi < zMax; zi++) {
            paint(x, y, zi);
            if (y != yMax) paint(x, yMax, zi);
        }
    }

    public void rectangleOutlineZ(int x, int y, int z, int sizeX, int sizeY) {
        int xMax = x + sizeX - 1;
        int yMax = y + sizeY - 1;
        for (int xi = x; xi <= xMax; xi++) {
            paint(xi, y, z);
            if (y != yMax) paint(xi, yMax, z);
        }
        for (int yi = y + 1; yi < yMax; yi++) {
            paint(x, yi, z);
            if (x != xMax) paint(xMax, yi, z);
        }
    }

    public void rectangleY(int x, int y, int z, int sizeX, int sizeZ) {
        cube(x, y, z, sizeX, 1, sizeZ);
    }

    public void rectangleX(int x, int y, int z, int sizeY, int sizeZ) {
        cube(x, y, z, 1, sizeY, sizeZ);
    }

    public void rectangleZ(int x, int y, int z, int sizeX, int sizeY) {
        cube(x, y, z, sizeX, sizeY, 1);
    }

    public void circleY(int x, int y, int z, int radius) {
        int r2 = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= r2) {
                    paint(x + dx, y, z + dz);
                }
            }
        }
    }

    public void circleX(int x, int y, int z, int radius) {
        int r2 = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dy * dy + dz * dz <= r2) {
                    paint(x, y + dy, z + dz);
                }
            }
        }
    }

    public void circleZ(int x, int y, int z, int radius) {
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    paint(x + dx, y + dy, z);
                }
            }
        }
    }

    public void circleOutlineY(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int xi = x + (int) Math.round(Math.cos(angle) * radius);
            int zi = z + (int) Math.round(Math.sin(angle) * radius);
            paint(xi, y, zi);
        }
    }

    public void circleOutlineX(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int yi = y + (int) Math.round(Math.cos(angle) * radius);
            int zi = z + (int) Math.round(Math.sin(angle) * radius);
            paint(x, yi, zi);
        }
    }

    public void circleOutlineZ(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int xi = x + (int) Math.round(Math.cos(angle) * radius);
            int yi = y + (int) Math.round(Math.sin(angle) * radius);
            paint(xi, yi, z);
        }
    }

    public void fillTriangle(int x1, int y1, int z1,
                              int x2, int y2, int z2,
                              int x3, int y3, int z3) {
        int dx = Math.max(Math.max(Math.abs(x1 - x2), Math.abs(x2 - x3)), Math.abs(x3 - x1));
        int dy = Math.max(Math.max(Math.abs(y1 - y2), Math.abs(y2 - y3)), Math.abs(y3 - y1));
        int dz = Math.max(Math.max(Math.abs(z1 - z2), Math.abs(z2 - z3)), Math.abs(z3 - z1));
        if (dx >= dy && dx >= dz) {
            fillTriangle2D(y1, z1, y2, z2, y3, z3, x1, 1, 2);
        } else if (dy >= dx && dy >= dz) {
            fillTriangle2D(x1, z1, x2, z2, x3, z3, y1, 0, 2);
        } else {
            fillTriangle2D(x1, y1, x2, y2, x3, y3, z1, 0, 1);
        }
    }

    private void fillTriangle2D(int a1, int a2, int b1, int b2, int c1, int c2,
                                 int fixed, int dim1, int dim2) {
        int minA = Math.min(a1, Math.min(b1, c1));
        int maxA = Math.max(a1, Math.max(b1, c1));
        int minB = Math.min(a2, Math.min(b2, c2));
        int maxB = Math.max(a2, Math.max(b2, c2));
        for (int i = minA; i <= maxA; i++) {
            for (int j = minB; j <= maxB; j++) {
                if (pointInTriangle(i, j, a1, a2, b1, b2, c1, c2)) {
                    int[] pos = new int[3];
                    pos[dim1] = i;
                    pos[dim2] = j;
                    pos[3 - dim1 - dim2] = fixed;
                    paint(pos[0], pos[1], pos[2]);
                }
            }
        }
    }

    private boolean pointInTriangle(int px, int py, int ax, int ay, int bx, int by, int cx, int cy) {
        int d1 = sign(px, py, ax, ay, bx, by);
        int d2 = sign(px, py, bx, by, cx, cy);
        int d3 = sign(px, py, cx, cy, ax, ay);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private int sign(int x1, int y1, int x2, int y2, int x3, int y3) {
        return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3);
    }

    public void sphereOutline(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2 && d2 >= r2 - 2 * radius + 1) {
                        paint(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
    }

    public void domeOutline(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2 && d2 >= r2 - 2 * radius + 1) {
                        paint(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
        int yBase = y;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2 && dx * dx + dz * dz >= r2 - 2 * radius + 1) {
                    paint(x + dx, yBase, z + dz);
                }
            }
        }
    }

    public void pyramidOutline(int x, int y, int z, int size, int height) {
        if (size < 2 || height < 1) return;
        int half = size / 2;
        int x0 = x - half;
        int z0 = z - half;
        rectangleOutlineY(x0, y, z0, size, size);
        int topX = x;
        int topY = y + height;
        int topZ = z;
        for (int dx = 0; dx <= size; dx += size) {
            for (int dz = 0; dz <= size; dz += size) {
                int bx = x0 + dx;
                int bz = z0 + dz;
                line(bx, y, bz, topX, topY, topZ);
            }
        }
    }

    public void cylinderOutline(int x, int y, int z, int radius, int height) {
        if (radius < 1 || height < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            for (int dy = 0; dy < height; dy++) {
                paint(x + dx, y + dy, z + dz);
            }
        }
        circleOutlineY(x, y, z, radius);
        if (height > 1) circleOutlineY(x, y + height - 1, z, radius);
    }

    public void coneOutline(int x, int y, int z, int radius, int height) {
        if (radius < 1 || height < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        int topY = y + height;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            line(x + dx, y, z + dz, x, topY, z);
        }
        circleOutlineY(x, y, z, radius);
    }

    public void stairs(int x, int y, int z, int steps, int dirX, int dirZ, int stepWidth) {
        if (steps < 1 || stepWidth < 1) return;
        int perpX = dirZ;
        int perpZ = -dirX;
        for (int i = 0; i < steps; i++) {
            int baseX = x + i * dirX;
            int baseY = y + i;
            int baseZ = z + i * dirZ;
            for (int w = 0; w < stepWidth; w++) {
                paint(baseX + w * perpX, baseY, baseZ + w * perpZ);
            }
        }
    }

    public void spiral(int x, int y, int z, int radius, int height, double rotations) {
        if (radius < 1 || height < 1 || rotations <= 0) return;
        int totalSteps = Math.max(height, (int) (rotations * 2 * Math.PI * radius));
        double angleStep = (rotations * 2 * Math.PI) / totalSteps;
        double heightStep = (double) height / totalSteps;
        for (int i = 0; i < totalSteps; i++) {
            double angle = i * angleStep;
            int xi = x + (int) Math.round(Math.cos(angle) * radius);
            int yi = y + (int) Math.round(i * heightStep);
            int zi = z + (int) Math.round(Math.sin(angle) * radius);
            paint(xi, yi, zi);
            paint(xi + 1, yi, zi);
            paint(xi, yi, zi + 1);
            paint(xi + 1, yi, zi + 1);
        }
    }

    public void arch(int x, int y, int z, int width, int height, int depth) {
        if (width < 3 || height < 2 || depth < 1) return;
        int radius = width / 2;
        int centerX = x + radius;
        for (int d = 0; d < depth; d++) {
            for (int h = 0; h < height; h++) {
                paint(x, y + h, z + d);
                paint(x + width - 1, y + h, z + d);
            }
        }
        int archY = y + height;
        for (int d = 0; d < depth; d++) {
            for (int i = 0; i <= width; i++) {
                int dx = i - radius;
                double archHeight = Math.sqrt(Math.max(0, radius * radius - dx * dx));
                if (archHeight > 0) {
                    paint(x + i, archY + (int) archHeight, z + d);
                }
            }
        }
    }

    public void tunnel(int x1, int y1, int z1, int x2, int y2, int z2, int width, int height) {
        if (width < 1 || height < 1) return;
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) return;
        double xInc = dx / (double) steps;
        double yInc = dy / (double) steps;
        double zInc = dz / (double) steps;
        for (int i = 0; i <= steps; i++) {
            int xi = x1 + (int) Math.round(i * xInc);
            int yi = y1 + (int) Math.round(i * yInc);
            int zi = z1 + (int) Math.round(i * zInc);
            for (int w = -width / 2; w <= width / 2; w++) {
                for (int h = 0; h < height; h++) {
                    paint(xi + w, yi + h, zi);
                }
            }
        }
    }

    public void bridge(int x1, int y1, int z1, int x2, int y2, int z2, int width, int pillarSpacing) {
        if (width < 1) return;
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int length = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (length == 0) return;
        double xInc = dx / (double) length;
        double yInc = dy / (double) length;
        double zInc = dz / (double) length;
        for (int i = 0; i <= length; i++) {
            int xi = x1 + (int) Math.round(i * xInc);
            int yi = y1 + (int) Math.round(i * yInc);
            int zi = z1 + (int) Math.round(i * zInc);
            for (int w = -width / 2; w <= width / 2; w++) {
                paint(xi + w, yi, zi);
                if (w == -width / 2 || w == width / 2) {
                    paint(xi + w, yi + 1, zi);
                }
            }
            if (pillarSpacing > 0 && i % pillarSpacing == 0 && yi > 0) {
                for (int py = 0; py < yi; py++) {
                    paint(xi, py, zi);
                }
            }
        }
    }

    public void tower(int x, int y, int z, int radius, int bodyHeight, int roofHeight) {
        if (radius < 1 || bodyHeight < 1 || roofHeight < 1) return;
        cylinderOutline(x, y, z, radius, bodyHeight);
        coneOutline(x, y + bodyHeight, z, radius, roofHeight);
    }

    public void house(int x, int y, int z, int width, int length, int wallHeight, int roofHeight) {
        if (width < 3 || length < 3 || wallHeight < 2 || roofHeight < 1) return;
        cubeOutline(x, y, z, width, wallHeight, length);
        rectangleY(x, y, z, width, length);
        int roofLayers = roofHeight;
        for (int layer = 0; layer < roofLayers; layer++) {
            int offset = layer;
            int layerWidth = width - 2 * offset;
            int layerLength = length - 2 * offset;
            if (layerWidth > 0 && layerLength > 0) {
                rectangleOutlineY(x + offset, y + wallHeight + layer, z + offset, layerWidth, layerLength);
            }
        }
        int doorX = x + width / 2;
        paint(doorX, y + 1, z);
        paint(doorX, y + 2, z);
        if (width > 5) {
            paint(x + 2, y + wallHeight / 2, z);
            paint(x + width - 3, y + wallHeight / 2, z);
        }
    }

    public void tree(int x, int y, int z, int trunkHeight, int crownRadius) {
        if (trunkHeight < 1 || crownRadius < 1) return;
        for (int h = 0; h < trunkHeight; h++) {
            paint(x, y + h, z);
        }
        int crownY = y + trunkHeight;
        for (int dx = -crownRadius; dx <= crownRadius; dx++) {
            for (int dy = 0; dy <= crownRadius; dy++) {
                for (int dz = -crownRadius; dz <= crownRadius; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= crownRadius * crownRadius) {
                        paint(x + dx, crownY + dy, z + dz);
                    }
                }
            }
        }
    }

    public void pyramid(int x, int y, int z, int size, int height) {
        if (size < 1 || height < 1) return;
        for (int h = 0; h < height; h++) {
            double ratio = 1.0 - (double) h / height;
            int layerSize = (int) Math.round(size * ratio);
            if (layerSize < 1) layerSize = 1;
            int layerHalf = layerSize / 2;
            for (int dx = -layerHalf; dx <= layerHalf; dx++) {
                for (int dz = -layerHalf; dz <= layerHalf; dz++) {
                    paint(x + dx, y + h, z + dz);
                }
            }
        }
    }
}
