package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.utils.TypeUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TypeUtilTest {

    @Test
    void testToChunkKeyDouble() {

        // 1:1 -> 1:1
        assertEquals("1:1", TypeUtil.toStringChunkCoord(1.0, 1.0));
        // 0.5:0.5 -> 0:0
        assertEquals("0:0", TypeUtil.toStringChunkCoord(0.5, 0.5));
        // 0.1:0.1 -> 0:0
        assertEquals("0:0", TypeUtil.toStringChunkCoord(0.1, 0.1));
        // 0:0 -> 0:0
        assertEquals("0:0", TypeUtil.toStringChunkCoord(0.0, 0.0));
        // -0.1:-0.1 -> -1:-1
        assertEquals("-1:-1", TypeUtil.toStringChunkCoord(-0.1, -0.1));
        // -0.5:-0.5 -> -1:-1
        assertEquals("-1:-1", TypeUtil.toStringChunkCoord(-0.5, -0.5));
        // -1:-1 -> -1:-1
        assertEquals("-1:-1", TypeUtil.toStringChunkCoord(-1.0, -1.0));
        // -1.1:-1.1 -> -2:-2
        assertEquals("-2:-2", TypeUtil.toStringChunkCoord(-1.1, -1.1));

        // Ganzzahlige Werte
        assertEquals("5:7", TypeUtil.toStringChunkCoord(5.0, 7.0));
        // Werte mit Nachkommastellen, positiv
        assertEquals("5:7", TypeUtil.toStringChunkCoord(5.8, 7.2));
        // Werte mit Nachkommastellen, negativ
        assertEquals("-3:-4", TypeUtil.toStringChunkCoord(-2.1, -3.7));
        // Werte exakt auf Ganzzahlgrenze
        assertEquals("0:0", TypeUtil.toStringChunkCoord(0.0, 0.0));
        // Werte knapp unter Null
        assertEquals("-1:-1", TypeUtil.toStringChunkCoord(-0.1, -0.9));
    }

    @Test
    void testToChunkKeyInt() {
        // 1:1 -> 1:1
        assertEquals("1:1", TypeUtil.toStringChunkCoord(1, 1));
        // 0:0 -> 0:0
        assertEquals("0:0", TypeUtil.toStringChunkCoord(0, 0));
        // -1:-1 -> -1:-1
        assertEquals("-1:-1", TypeUtil.toStringChunkCoord(-1, -1));
        // 5:7 -> 5:7
        assertEquals("5:7", TypeUtil.toStringChunkCoord(5, 7));
        // -2:-4 -> -2:-4
        assertEquals("-2:-4", TypeUtil.toStringChunkCoord(-2, -4));
        // große Werte
        assertEquals("1000:2000", TypeUtil.toStringChunkCoord(1000, 2000));
        // gemischte Vorzeichen
        assertEquals("-3:4", TypeUtil.toStringChunkCoord(-3, 4));
        assertEquals("3:-4", TypeUtil.toStringChunkCoord(3, -4));
    }
}
