package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlatPainterTest {

    private WFlat createFlat(int sizeX, int sizeZ) {
        WFlat flat = WFlat.builder()
                .sizeX(sizeX)
                .sizeZ(sizeZ)
                .oceanLevel(0)
                .oceanBlockId("water")
                .unknownProtected(false)
                .build();
        // Felder initialisieren
        try {
            java.lang.reflect.Field levels = WFlat.class.getDeclaredField("levels");
            java.lang.reflect.Field columns = WFlat.class.getDeclaredField("columns");
            levels.setAccessible(true);
            columns.setAccessible(true);
            levels.set(flat, new byte[sizeX * sizeZ]);
            columns.set(flat, new byte[sizeX * sizeZ]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return flat;
    }

    @Test
    @Disabled
    void testPaintAndLine() {
        WFlat flat = createFlat(10, 10);
        FlatPainter painter = new FlatPainter(flat);
        painter.paint(2, 3, 7);
        assertEquals(7, flat.getLevel(2, 3));
        painter.line(0, 0, 0, 4, 5);
        for (int z = 0; z <= 4; z++) {
            assertEquals(5, flat.getLevel(0, z));
        }
    }

    @Test
    void testFillCircle() {
        WFlat flat = createFlat(10, 10);
        FlatPainter painter = new FlatPainter(flat);
        painter.fillCircle(5, 5, 2, 9);
        int count = 0;
        for (int x = 3; x <= 7; x++)
            for (int z = 3; z <= 7; z++)
                if (flat.getLevel(x, z) == 9) count++;
        assertTrue(count > 0); // Es wurden Felder gesetzt
    }

    @Test
    void testFillRectangleAndOutline() {
        WFlat flat = createFlat(10, 10);
        FlatPainter painter = new FlatPainter(flat);
        painter.fillRectangle(1, 1, 3, 3, 4);
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                assertEquals(4, flat.getLevel(x, z));
        painter.rectangleOutline(5, 5, 7, 7, 8);
        for (int x = 5; x <= 7; x++) {
            assertEquals(8, flat.getLevel(x, 5));
            assertEquals(8, flat.getLevel(x, 7));
        }
        for (int z = 6; z < 7; z++) {
            assertEquals(8, flat.getLevel(5, z));
            assertEquals(8, flat.getLevel(7, z));
        }
    }

    @Test
    void testPainterAndColumnPainter() {
        WFlat flat = createFlat(5, 5);
        FlatPainter painter = new FlatPainter(flat);
        painter.setPainter((f, x, z, l) -> l + 10);
        painter.setColumnPainter((f, x, z, l, d) -> (byte)(d + 2));
        painter.paint(1, 1, 3);
        assertEquals(13, flat.getLevel(1, 1));
        // Column wird nur gesetzt, wenn definition > DO_NOT_SET, daher Test ggf. anpassen
    }

    @Test
    @Disabled
    void testSoftenAndSharpen() {
        WFlat flat = createFlat(3, 3);
        FlatPainter painter = new FlatPainter(flat);
        // Setze ein Muster
        flat.setLevel(0, 0, 1); flat.setLevel(1, 0, 2); flat.setLevel(2, 0, 3);
        flat.setLevel(0, 1, 4); flat.setLevel(1, 1, 5); flat.setLevel(2, 1, 6);
        flat.setLevel(0, 2, 7); flat.setLevel(1, 2, 8); flat.setLevel(2, 2, 9);
        painter.soften(0, 0, 2, 2, 1.0);
        int mean = (1+2+3+4+5+6+7+8+9)/9;
        assertEquals(mean, flat.getLevel(1, 1));
        painter.sharpen(0, 0, 2, 2, 1.0);
        assertNotEquals(mean, flat.getLevel(1, 1));
    }

    @Test
    void testPixelFlip() {
        WFlat flat = createFlat(3, 3);
        FlatPainter painter = new FlatPainter(flat);
        int v = 1;
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                flat.setLevel(x, z, v++);
        painter.pixelFlip(0, 0, 2, 2, 1.0);
        boolean changed = false;
        v = 1;
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                if (flat.getLevel(x, z) != v++) changed = true;
        assertTrue(changed);
    }
}
