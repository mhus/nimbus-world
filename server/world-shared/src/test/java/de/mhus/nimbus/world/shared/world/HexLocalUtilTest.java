package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalEdgeVector;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HexLocalUtilTest {

    @Test
    void testParseHexLocalEdgeVector_and_toString() {
        String edgeStr = "<NE2/4>";
        HexLocalEdgeVector edge = HexLocalUtil.parseHexLocalEdgeVector(edgeStr);
        assertThat(edge.side()).isEqualTo(WHexGrid.EDGE.NORTH_EAST);
        assertThat(edge.numerator()).isEqualTo(2);
        assertThat(edge.denominator()).isEqualTo(4);
        // denominator 4 is the default, so toString omits it
        assertThat(HexLocalUtil.toString(edge)).isEqualTo("<NE2>");

        // Non-default denominator: full format preserved
        HexLocalEdgeVector edge3 = HexLocalUtil.parseHexLocalEdgeVector("<SW1/3>");
        assertThat(HexLocalUtil.toString(edge3)).isEqualTo("<SW1/3>");
    }

    @Test
    void testParseHexLocalEdgeVector_invalid() {
        assertThatThrownBy(() -> HexLocalUtil.parseHexLocalEdgeVector("<INVALID2/4>"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HexLocalUtil.parseHexLocalEdgeVector("<NE2/>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HexLocalUtil.parseHexLocalEdgeVector(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseHexLocalPosition_and_toString() {
        HexLocalUtil util = new HexLocalUtil();
        String posStr = "<10;-5/5>";
        HexLocalPosition pos = util.parseHexLocalPosition(posStr, 100);
        assertThat(pos.position().getQ()).isEqualTo(10);
        assertThat(pos.position().getR()).isEqualTo(-5);
        assertThat(pos.divider()).isEqualTo(5);
        // divider 5 is the default, so toString uses size format instead
        assertThat(HexLocalUtil.toString(pos)).isEqualTo("<10;-5#20>");
    }

    @Test
    void testParseHexLocalPosition_invalid() {
        HexLocalUtil util = new HexLocalUtil();
        assertThatThrownBy(() -> util.parseHexLocalPosition("<10;INVALID/5>", 100)).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> util.parseHexLocalPosition(null, 100)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetType() {
        assertThat(HexLocalUtil.getType("<NE2/4>")).isEqualTo(HexLocalUtil.LOCAL_TYPE.EDGE);
        assertThat(HexLocalUtil.getType("<10;-5/5>")).isEqualTo(HexLocalUtil.LOCAL_TYPE.POSITION);
        assertThat(HexLocalUtil.getType("invalid")).isEqualTo(HexLocalUtil.LOCAL_TYPE.UNKNOWN);
    }

    @Test
    void testToHexgridLocalCenter_edge() {
        HexLocalEdgeVector edge = new HexLocalEdgeVector(WHexGrid.EDGE.NORTH_EAST, 2, 4);
        Vector2Int center = HexLocalUtil.toHexgridLocalCenter(edge, 100);
        // Prüfe, dass die Koordinate im erwarteten Bereich liegt
        assertThat(center.getX()).isBetween(0, 100);
        assertThat(center.getZ()).isBetween(0, 100);
    }

    @Test
    void testToHexgridLocalCenter_position() {
        HexVector2 hex = HexVector2.builder().q(1).r(-1).build();
        HexLocalPosition pos = new HexLocalPosition(hex, 5, 100);
        Vector2Int center = HexLocalUtil.toHexGridLocalCenter(pos);
        // Prüfe, dass die Koordinate im erwarteten Bereich liegt
        assertThat(center.getX()).isBetween(-100, 100);
        assertThat(center.getZ()).isBetween(-100, 100);
    }

    @Test
    void testToHexgridLocalCenter_string() {
        Vector2Int edgeCenter = HexLocalUtil.toHexgridLocalCenter("<NE2/4>", 100);
        assertThat(edgeCenter.getX()).isBetween(0, 100);
        assertThat(edgeCenter.getZ()).isBetween(0, 100);
        Vector2Int posCenter = HexLocalUtil.toHexgridLocalCenter("<1;-1/5>", 100);
        assertThat(posCenter.getX()).isBetween(-100, 100);
        assertThat(posCenter.getZ()).isBetween(-100, 100);
    }
}

