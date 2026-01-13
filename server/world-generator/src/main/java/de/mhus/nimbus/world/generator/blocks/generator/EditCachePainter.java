package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class EditCachePainter {

    public static final BlockPainter DEFAULT_PAINTER = new BlockPainter();
    public static final BlockPainter RASTER_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            // Raster: nur jedes zweite Block setzen
            if ((x + y + z) % 2 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };
    public static final BlockPainter GRID_5_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            // Grid: nur Kanten setzen
            if (x % 5 == 0 || y % 5 == 0 || z % 5 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };
    public static final BlockPainter GRID_2_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            // Grid: nur Kanten setzen
            if (x % 2 == 0 || y % 2 == 0 || z % 2 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };

    @Getter
    private final WEditCacheService editService;
    @Getter
    private BlockDef blockDef;
    @Getter
    private WWorld world;
    @Getter
    private String layerDataId;
    @Getter
    private String modelName;
    @Getter @Setter
    private BlockPainter painter = DEFAULT_PAINTER;
    @Getter @Setter
    private int groupId;
    @Getter
    private ManipulatorContext context;



    public void setContext(WWorld world, String layerDataId, String modelName, int groupId, BlockDef blockDef) {
        this.world = world;
        this.layerDataId = layerDataId;
        this.modelName = modelName;
        this.blockDef = blockDef;
        this.groupId = groupId;
    }

    public void setManipulatorContext(ManipulatorContext context) {
        this.context = context;
    }

    public void paint(int x, int y, int z) {
        painter.paint(this, x, y, z);
    }

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
        // Flächen unten und oben
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                paint(x + dx, y, z + dz); // unten
                if (y != yMax) paint(x + dx, yMax, z + dz); // oben
            }
        }
        // Flächen vorne und hinten (ohne Ecken, da schon gesetzt)
        for (int dy = 1; dy < sizeY - 1; dy++) {
            for (int dx = 0; dx < sizeX; dx++) {
                paint(x + dx, y + dy, z); // vorne
                if (z != zMax) paint(x + dx, y + dy, zMax); // hinten
            }
            for (int dz = 1; dz < sizeZ - 1; dz++) {
                paint(x, y + dy, z + dz); // links
                if (x != xMax) paint(xMax, y + dy, z + dz); // rechts
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
    /**
     * Zeichnet ein Rechteck-Outline in der XZ-Ebene auf Höhe y.
     */
    public void rectangleOutlineY(int x, int y, int z, int sizeX, int sizeZ) {
        int xMax = x + sizeX - 1;
        int zMax = z + sizeZ - 1;
        for (int xi = x; xi <= xMax; xi++) {
            paint(xi, y, z); // oben
            if (z != zMax) paint(xi, y, zMax); // unten
        }
        for (int zi = z + 1; zi < zMax; zi++) {
            paint(x, y, zi); // links
            if (x != xMax) paint(xMax, y, zi); // rechts
        }
    }

    /**
     * Zeichnet ein Rechteck-Outline in der YZ-Ebene auf X-Position x.
     */
    public void rectangleOutlineX(int x, int y, int z, int sizeY, int sizeZ) {
        int yMax = y + sizeY - 1;
        int zMax = z + sizeZ - 1;
        for (int yi = y; yi <= yMax; yi++) {
            paint(x, yi, z); // vorne
            if (z != zMax) paint(x, yi, zMax); // hinten
        }
        for (int zi = z + 1; zi < zMax; zi++) {
            paint(x, y, zi); // oben
            if (y != yMax) paint(x, yMax, zi); // unten
        }
    }

    /**
     * Zeichnet ein Rechteck-Outline in der XY-Ebene auf Z-Position z.
     */
    public void rectangleOutlineZ(int x, int y, int z, int sizeX, int sizeY) {
        int xMax = x + sizeX - 1;
        int yMax = y + sizeY - 1;
        for (int xi = x; xi <= xMax; xi++) {
            paint(xi, y, z); // oben
            if (y != yMax) paint(xi, yMax, z); // unten
        }
        for (int yi = y + 1; yi < yMax; yi++) {
            paint(x, yi, z); // links
            if (x != xMax) paint(xMax, yi, z); // rechts
        }
    }

    /**
     * Zeichnet ein gefülltes Rechteck in der XZ-Ebene auf Höhe y.
     */
    public void rectangleY(int x, int y, int z, int sizeX, int sizeZ) {
        cube(x, y, z, sizeX, 1, sizeZ);
    }

    /**
     * Zeichnet ein gefülltes Rechteck in der YZ-Ebene auf X-Position x.
     */
    public void rectangleX(int x, int y, int z, int sizeY, int sizeZ) {
        cube(x, y, z, 1, sizeY, sizeZ);
    }

    /**
     * Zeichnet ein gefülltes Rechteck in der XY-Ebene auf Z-Position z.
     */
    public void rectangleZ(int x, int y, int z, int sizeX, int sizeY) {
        cube(x, y, z, sizeX, sizeY, 1);
    }

    /**
     * Zeichnet einen ausgefüllten Kreis in der XZ-Ebene auf Höhe y.
     */
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

    /**
     * Zeichnet einen ausgefüllten Kreis in der YZ-Ebene auf X-Position x.
     */
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

    /**
     * Zeichnet einen ausgefüllten Kreis in der XY-Ebene auf Z-Position z.
     */
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

    /**
     * Zeichnet einen nicht ausgefüllten Kreis (Outline) in der XZ-Ebene auf Höhe y.
     */
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

    /**
     * Zeichnet einen nicht ausgefüllten Kreis (Outline) in der YZ-Ebene auf X-Position x.
     */
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

    /**
     * Zeichnet einen nicht ausgefüllten Kreis (Outline) in der XY-Ebene auf Z-Position z.
     */
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

    /**
     * Zeichnet ein gefülltes Dreieck im 3D-Raum zwischen drei Punkten.
     * Die Füllung erfolgt per Projektion auf die Hauptebene mit größter Ausdehnung.
     */
    public void fillTriangle(WWorld world, String layerDataId, String modelName,
                            int x1, int y1, int z1,
                            int x2, int y2, int z2,
                            int x3, int y3, int z3,
                            BlockDef blockDef) {
        // Bestimme Hauptebene (größte Ausdehnung)
        int dx = Math.max(Math.max(Math.abs(x1 - x2), Math.abs(x2 - x3)), Math.abs(x3 - x1));
        int dy = Math.max(Math.max(Math.abs(y1 - y2), Math.abs(y2 - y3)), Math.abs(y3 - y1));
        int dz = Math.max(Math.max(Math.abs(z1 - z2), Math.abs(z2 - z3)), Math.abs(z3 - z1));
        // Projektion auf Hauptebene
        if (dx >= dy && dx >= dz) {
            // Projektion auf YZ-Ebene (x konstant)
            fillTriangle2D(world, layerDataId, modelName, y1, z1, y2, z2, y3, z3, x1, 1, 2, blockDef);
        } else if (dy >= dx && dy >= dz) {
            // Projektion auf XZ-Ebene (y konstant)
            fillTriangle2D(world, layerDataId, modelName, x1, z1, x2, z2, x3, z3, y1, 0, 2, blockDef);
        } else {
            // Projektion auf XY-Ebene (z konstant)
            fillTriangle2D(world, layerDataId, modelName, x1, y1, x2, y2, x3, y3, z1, 0, 1, blockDef);
        }
    }

    // Hilfsmethode für 2D-Triangle-Füllung auf einer Ebene
    private void fillTriangle2D(WWorld world, String layerDataId, String modelName,
                                int a1, int a2, int b1, int b2, int c1, int c2,
                                int fixed, int dim1, int dim2, BlockDef blockDef) {
        // Bounding Box
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

    // Prüft, ob Punkt (px,py) im 2D-Dreieck (ax,ay)-(bx,by)-(cx,cy) liegt
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

    /**
     * Zeichnet eine Kugel-Outline (nur Oberfläche) mit Mittelpunkt (x,y,z) und gegebenem Radius.
     */
    public void sphereOutline(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 <= r2 && d2 >= r2 - 2*radius + 1) {
                        paint(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
    }

    /**
     * Zeichnet eine Dome-Outline (halbe Kugel, untere Hälfte geschlossen) mit Mittelpunkt (x,y,z) und gegebenem Radius.
     */
    public void domeOutline(int x, int y, int z, int radius) {
        if (radius < 1) return;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) { // nur obere Hälfte
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 <= r2 && d2 >= r2 - 2*radius + 1) {
                        paint(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
        // Untere Kreisfläche (Basis)
        int yBase = y;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx*dx + dz*dz <= r2 && dx*dx + dz*dz >= r2 - 2*radius + 1) {
                    paint(x + dx, yBase, z + dz);
                }
            }
        }
    }

    /**
     * Zeichnet eine Pyramiden-Outline mit quadratischer Basis (x,z) und Höhe h.
     */
    public void pyramidOutline(int x, int y, int z, int size, int height) {
        if (size < 2 || height < 1) return;
        int half = size / 2;
        int x0 = x - half;
        int z0 = z - half;
        int x1 = x + half;
        int z1 = z + half;
        // Basis-Outline
        rectangleOutlineY(x0, y, z0, size, size);
        // Kanten zu Spitze
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

    /**
     * Zeichnet eine Zylinder-Outline (Mantel und Deckel) mit Mittelpunkt (x,y,z), Radius und Höhe.
     */
    public void cylinderOutline(int x, int y, int z, int radius, int height) {
        if (radius < 1 || height < 1) return;
        // Mantel
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            for (int dy = 0; dy < height; dy++) {
                paint(x + dx, y + dy, z + dz);
            }
        }
        // Deckel oben und unten
        circleOutlineY(x, y, z, radius);
        if (height > 1) circleOutlineY(x, y + height - 1, z, radius);
    }

    /**
     * Zeichnet eine Kegel-Outline (Mantel und Basis) mit Mittelpunkt (x,y,z), Radius und Höhe.
     */
    public void coneOutline(int x, int y, int z, int radius, int height) {
        if (radius < 1 || height < 1) return;
        int steps = Math.max(12, (int) (2 * Math.PI * radius));
        int topY = y + height;
        // Mantel (Kanten von Basis zum Apex)
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            line(x + dx, y, z + dz, x, topY, z);
        }
        // Basis-Outline
        circleOutlineY(x, y, z, radius);
    }

    public static class BlockPainter {
        public void paint(EditCachePainter painter,  int x, int y, int z) {
            Block block = Block.builder()
                    .position(
                            de.mhus.nimbus.generated.types.Vector3Int.builder()
                                    .x(x)
                                    .y(y)
                                    .z(z)
                                    .build()
                    ).build();
            painter.blockDef.fillBlock(block);
            painter.editService.doSetAndSendBlock(painter.world, painter.layerDataId, painter.modelName, block, painter.groupId);

            // Add block to ModelSelector if context is available
            if (painter.context != null && painter.context.getModelSelector() != null) {
                String color = painter.context.getModelSelector().getDefaultColor();
                if (color == null) {
                    color = "#00ff00"; // Default green
                }
                painter.context.getModelSelector().addBlock(x, y, z, color);
            }
        }
    }

}
