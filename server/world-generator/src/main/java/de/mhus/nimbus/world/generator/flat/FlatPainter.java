package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.Getter;

public class FlatPainter {

    private static final int DO_NOT_SET = -1;

    @Getter
    private final WFlat flat;
    private int definition = DO_NOT_SET;

    private Painter painter = DEFAULT_PAINTER;

    public FlatPainter(WFlat flat) {
        this.flat = flat;
    }

    public interface Painter {
        int getLevel(WFlat flat, int x, int z, int level);
    }

    public static class ChainPainter implements Painter {
        private final Painter[] painters;

        public ChainPainter(Painter... painters) {
            this.painters = painters;
        }

        @Override
        public int getLevel(WFlat flat, int x, int z, int level) {
            int currentLevel = level;
            for (Painter painter : painters) {
                currentLevel = painter.getLevel(flat, x, z, currentLevel);
            }
            return currentLevel;
        }
    }

    public static final Painter DEFAULT_PAINTER = (flat, x, z, level) -> level;
    public static final Painter ADDITIVE = (flat, x, z, level) -> flat.getLevel(x,z) + level;
    public static final Painter RANDOM_MODIFIER = (flat, x, z, level) -> flat.getLevel(x,z) + (int)(Math.random() * level) - level / 2;
    public static final Painter RANDOM_ADDITIVE = (flat, x, z, level) -> flat.getLevel(x,z) + (int)(Math.random() * level);
    public static final Painter RANDOM_DIFFUSE_2 = (flat, x, z, level) -> level + (int)(Math.random() * 10) % 3;
    public static final Painter RANDOM_DIFFUSE_1 = (flat, x, z, level) -> level + (int)(Math.random() * 10) % 2;
    public static final Painter HIGHER = (flat, x, z, level) -> {
        var current = flat.getLevel(x,z);
        return current < level ? level : current;
    };
    public static final Painter LOWER = (flat, x, z, level) -> {
        var current = flat.getLevel(x,z);
        return current > level ? level : current;
    };

    public void setPainter(Painter painter) {
        if (painter == null) this.painter = DEFAULT_PAINTER;
        else this.painter = painter;
    }

    public interface ColumnPainter {
        byte getColumn(WFlat flat, int x, int z, int level, int definition);
    }

    public static final ColumnPainter DEFAULT_COLUMN_PAINTER = (flat, x, z, level, definition) -> (byte)definition;

    private ColumnPainter columnPainter = DEFAULT_COLUMN_PAINTER;

    public ColumnPainter getColumnPainter() {
        return columnPainter;
    }

    public void setColumnPainter(ColumnPainter columnPainter) {
        if (columnPainter == null) this.columnPainter = DEFAULT_COLUMN_PAINTER;
        else this.columnPainter = columnPainter;
    }

    public void line(int x1, int z1, int x2, int z2, int level) {
        line(x1, z1, x2, z2, level, painter);
    }
    public void line(int x1, int z1, int x2, int z2, int level, Painter painter) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) {
            // Check bounds for single point
            if (isInBounds(x1, z1)) {
                flat.setLevel(x1, z1, painter.getLevel(flat, x1, z1, level));
                if (definition > DO_NOT_SET) {
                    flat.setColumn(x1, z1, columnPainter.getColumn(flat, x1, z1, level, definition));
                }
            }
            return;
        }
        double x = x1;
        double z = z1;
        double xInc = dx / (double) steps;
        double zInc = dz / (double) steps;
        for (int i = 0; i <= steps; i++) {
            int xi = (int) Math.round(x);
            int zi = (int) Math.round(z);
            // Skip points outside bounds
            if (isInBounds(xi, zi)) {
                flat.setLevel(xi, zi, painter.getLevel(flat, xi, zi, level));
                if (definition > DO_NOT_SET) {
                    flat.setColumn(xi, zi, columnPainter.getColumn(flat, xi, zi, level, definition));
                }
            }
            x += xInc;
            z += zInc;
        }
    }

    /**
     * Check if coordinates are within flat bounds.
     */
    private boolean isInBounds(int x, int z) {
        return x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ();
    }

    public void fillCircle(int x, int z, int radius, int level) {
        fillCircle(x, z, radius, level, painter);
    }
    public void fillCircle(int x, int z, int radius, int level, Painter painter) {
        if (radius < 1) return;
        int r2 = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= r2) {
                    int xi = x + dx;
                    int zi = z + dz;
                    if (isInBounds(xi, zi)) {
                        flat.setLevel(xi, zi, painter.getLevel(flat, xi, zi, level));
                        if (definition > DO_NOT_SET) {
                            flat.setColumn(xi, zi, columnPainter.getColumn(flat, xi, zi, level, definition));
                        }
                    }
                }
            }
        }
    }

    public void circleOutline(int x, int z, int radius, int level) {
        circleOutline(x, z, radius, level, painter);
    }
    public void circleOutline(int x, int z, int radius, int level, Painter painter) {
        if (radius < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int xi = x + (int) Math.round(Math.cos(angle) * radius);
            int zi = z + (int) Math.round(Math.sin(angle) * radius);
            if (isInBounds(xi, zi)) {
                flat.setLevel(xi, zi, painter.getLevel(flat, xi, zi, level));
                if (definition > DO_NOT_SET) {
                    flat.setColumn(xi, zi, columnPainter.getColumn(flat, xi, zi, level, definition));
                }
            }
        }
    }

    public void fillRectangle(int x1, int z1, int x2, int z2, int level) {
        fillRectangle(x1, z1, x2, z2, level, painter);
    }
    public void fillRectangle(int x1, int z1, int x2, int z2, int level, Painter painter) {
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        for (int z = zmin; z <= zmax; z++) {
            for (int x = xmin; x <= xmax; x++) {
                if (isInBounds(x, z)) {
                    flat.setLevel(x, z, painter.getLevel(flat, x, z, level));
                    if (definition > DO_NOT_SET) {
                        flat.setColumn(x, z, columnPainter.getColumn(flat, x, z, level, definition));
                    }
                }
            }
        }
    }

    public void rectangleOutline(int x1, int z1, int x2, int z2, int level) {
        rectangleOutline(x1, z1, x2, z2, level, painter);
    }
    public void rectangleOutline(int x1, int z1, int x2, int z2, int level, Painter painter) {
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        // obere und untere Kante
        for (int x = xmin; x <= xmax; x++) {
            if (isInBounds(x, zmin)) {
                flat.setLevel(x, zmin, painter.getLevel(flat, x, zmin, level));
                if (definition > DO_NOT_SET) flat.setColumn(x, zmin, columnPainter.getColumn(flat, x, zmin, level, definition));
            }
            if (zmin != zmax && isInBounds(x, zmax)) {
                flat.setLevel(x, zmax, painter.getLevel(flat, x, zmax, level));
                if (definition > DO_NOT_SET) flat.setColumn(x, zmax, columnPainter.getColumn(flat, x, zmax, level, definition));
            }
        }
        // linke und rechte Kante (ohne Ecken, da schon gesetzt)
        for (int z = zmin + 1; z < zmax; z++) {
            if (isInBounds(xmin, z)) {
                flat.setLevel(xmin, z, painter.getLevel(flat, xmin, z, level));
                if (definition > DO_NOT_SET) flat.setColumn(xmin, z, columnPainter.getColumn(flat, xmin, z, level, definition));
            }
            if (xmin != xmax && isInBounds(xmax, z)) {
                flat.setLevel(xmax, z, painter.getLevel(flat, xmax, z, level));
                if (definition > DO_NOT_SET) flat.setColumn(xmax, z, columnPainter.getColumn(flat, xmax, z, level, definition));
            }
        }
    }

    public void soften(int x1, int z1, int x2, int z2, int radius, double factor) {
        if (factor < 0) factor = 0;
        if (factor > 1) factor = 1;
        int r = Math.max(1, radius);
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        int width = xmax - xmin + 1;
        int height = zmax - zmin + 1;

        // Step 1: Calculate new levels based on current flat levels (no side effects)
        int[][] newLevels = new int[width][height];
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                double sum = 0.0;
                int count = 0;

                // Sum neighbors from flat (originals, not yet modified)
                for (int ndz = -r; ndz <= r; ndz++) {
                    for (int ndx = -r; ndx <= r; ndx++) {
                        int nx = dx + ndx;
                        int nz = dz + ndz;
                        if (nx >= 0 && nx < width && nz >= 0 && nz < height) {
                            sum += flat.getLevel(xmin + nx, zmin + nz);
                            count++;
                        }
                    }
                }

                int original = flat.getLevel(xmin + dx, zmin + dz);
                double mean = count > 0 ? sum / count : original;
                double newLevelDouble = factor * mean + (1.0 - factor) * original;
                newLevels[dx][dz] = (int) Math.round(newLevelDouble);
            }
        }

        // Step 2: Write all new levels back to flat
        int changedCount = 0;
        int attemptedCount = 0;
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int x = xmin + dx;
                int z = zmin + dz;
                int oldLevel = flat.getLevel(x, z);
                int newLevel = newLevels[dx][dz];

                boolean success = flat.setLevel(x, z, newLevel);
                attemptedCount++;

                if (success && newLevel != oldLevel) {
                    changedCount++;
                }
            }
        }

        // Log statistics
        if (attemptedCount > 0) {
            System.out.println(String.format("[FlatPainter.soften] Attempted: %d, Changed: %d (%.1f%%)",
                attemptedCount, changedCount, 100.0 * changedCount / attemptedCount));
        }
    }

    public void sharpen(int x1, int z1, int x2, int z2, double factor) {
        if (factor < 0) factor = 0;
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        int width = xmax - xmin + 1;
        int height = zmax - zmin + 1;

        // Step 1: Calculate new levels based on current flat levels
        int[][] newLevels = new int[width][height];
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                double sum = 0.0;
                int count = 0;

                // Sum neighbors from flat
                for (int ndz = -1; ndz <= 1; ndz++) {
                    for (int ndx = -1; ndx <= 1; ndx++) {
                        int nx = dx + ndx;
                        int nz = dz + ndz;
                        if (nx >= 0 && nx < width && nz >= 0 && nz < height) {
                            sum += flat.getLevel(xmin + nx, zmin + nz);
                            count++;
                        }
                    }
                }

                int original = flat.getLevel(xmin + dx, zmin + dz);
                double mean = count > 0 ? sum / count : original;
                double sharpenedDouble = (1.0 + factor) * original - factor * mean;
                newLevels[dx][dz] = (int) Math.round(sharpenedDouble);
            }
        }

        // Step 2: Write all new levels back
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int x = xmin + dx;
                int z = zmin + dz;
                flat.setLevel(x, z, newLevels[dx][dz]);
            }
        }
    }

    public void soften5x5(int x1, int z1, int x2, int z2, double factor) {
        if (factor < 0) factor = 0;
        if (factor > 1) factor = 1;
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        int width = xmax - xmin + 1;
        int height = zmax - zmin + 1;

        // Step 1: Calculate new levels based on current flat levels (5x5 kernel)
        int[][] newLevels = new int[width][height];
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                double sum = 0.0;
                int count = 0;

                // Sum neighbors from flat (5x5 kernel)
                for (int ndz = -2; ndz <= 2; ndz++) {
                    for (int ndx = -2; ndx <= 2; ndx++) {
                        int nx = dx + ndx;
                        int nz = dz + ndz;
                        if (nx >= 0 && nx < width && nz >= 0 && nz < height) {
                            sum += flat.getLevel(xmin + nx, zmin + nz);
                            count++;
                        }
                    }
                }

                int original = flat.getLevel(xmin + dx, zmin + dz);
                double mean = count > 0 ? sum / count : original;
                double newLevelDouble = factor * mean + (1.0 - factor) * original;
                newLevels[dx][dz] = (int) Math.round(newLevelDouble);
            }
        }

        // Step 2: Write all new levels back
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int x = xmin + dx;
                int z = zmin + dz;
                flat.setLevel(x, z, newLevels[dx][dz]);
            }
        }
    }

    public void paint(int x, int z, int level) {
        paint(x, z, level, painter);
    }
    public void paint(int x, int z, int level, Painter painter) {
        if (isInBounds(x, z)) {
            flat.setLevel(x, z, painter.getLevel(flat, x, z, level));
            if (definition > DO_NOT_SET) {
                flat.setColumn(x, z, columnPainter.getColumn(flat, x, z, level, definition));
            }
        }
    }

    public void pixelFlip(int x1, int z1, int x2, int z2, double factor) {
        if (factor <= 0) return;
        if (factor > 1) factor = 1;
        int xmin = Math.min(x1, x2);
        int xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2);
        int zmax = Math.max(z1, z2);
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dz = {-1, -1, -1, 0, 0, 1, 1, 1};
        java.util.Random rnd = new java.util.Random();
        for (int z = zmin; z <= zmax; z++) {
            for (int x = xmin; x <= xmax; x++) {
                if (isInBounds(x, z) && rnd.nextDouble() < factor) {
                    int n = rnd.nextInt(dx.length);
                    int nx = x + dx[n];
                    int nz = z + dz[n];
                    // Check both positions are in bounds
                    if (isInBounds(nx, nz)) {
                        int l1 = flat.getLevel(x, z);
                        int l2 = flat.getLevel(nx, nz);
                        flat.setLevel(x, z, l2);
                        flat.setLevel(nx, nz, l1);
                        if (definition > DO_NOT_SET) {
                            int c1 = flat.getColumn(x, z);
                            int c2 = flat.getColumn(nx, nz);
                            flat.setColumn(x, z, (byte)c2);
                            flat.setColumn(nx, nz, (byte)c1);
                        }
                    }
                }
            }
        }
    }

}
