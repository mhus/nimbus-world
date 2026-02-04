package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for hexagonal slot position generation.
 */
class HexagonalSlotPositionsTest {

    @Test
    void testDivider1_OnlyCenter() {
        List<HexVector2> positions = getHexagonalSlotPositions(1);

        System.out.println("\n=== Divider 1 (BIG) - Expected: 1 slot ===");
        printPositions(positions);

        assertEquals(1, positions.size(), "Divider 1 should have 1 slot");
        assertTrue(containsPosition(positions, 0, 0), "Should contain center <0;0>");
    }

    @Test
    void testDivider3_CenterPlusRing1() {
        List<HexVector2> positions = getHexagonalSlotPositions(3);

        System.out.println("\n=== Divider 3 (MEDIUM) - Expected: 7 slots ===");
        printPositions(positions);

        assertEquals(7, positions.size(), "Divider 3 should have 7 slots");
        assertTrue(containsPosition(positions, 0, 0), "Should contain center <0;0>");

        // Ring 1 positions
        assertTrue(containsPosition(positions, 0, 1), "Should contain <0;1>");
        assertTrue(containsPosition(positions, 1, 0), "Should contain <1;0>");
        assertTrue(containsPosition(positions, 1, -1), "Should contain <1;-1>");
        assertTrue(containsPosition(positions, 0, -1), "Should contain <0;-1>");
        assertTrue(containsPosition(positions, -1, 0), "Should contain <-1;0>");
        assertTrue(containsPosition(positions, -1, 1), "Should contain <-1;1>");
    }

    @Test
    void testDivider5_CenterPlusRing1And2() {
        List<HexVector2> positions = getHexagonalSlotPositions(5);

        System.out.println("\n=== Divider 5 (SMALL) - Expected: 19 slots ===");
        printPositions(positions);

        assertEquals(19, positions.size(), "Divider 5 should have 19 slots");
        assertTrue(containsPosition(positions, 0, 0), "Should contain center <0;0>");
    }

    @Test
    void testDivider7_CenterPlusRing1And2And3() {
        List<HexVector2> positions = getHexagonalSlotPositions(7);

        System.out.println("\n=== Divider 7 (TINY) - Expected: 37 slots ===");
        printPositions(positions);

        assertEquals(37, positions.size(), "Divider 7 should have 37 slots");
        assertTrue(containsPosition(positions, 0, 0), "Should contain center <0;0>");
    }

    @Test
    void testRing1Has6Positions() {
        List<HexVector2> ring = getHexRing(0, 0, 1);

        System.out.println("\n=== Ring 1 - Expected: 6 positions ===");
        printPositions(ring);

        assertEquals(6, ring.size(), "Ring 1 should have 6 positions");
    }

    @Test
    void testRing2Has12Positions() {
        List<HexVector2> ring = getHexRing(0, 0, 2);

        System.out.println("\n=== Ring 2 - Expected: 12 positions ===");
        printPositions(ring);

        assertEquals(12, ring.size(), "Ring 2 should have 12 positions");
    }

    // Helper methods (copied from VillageDesigner for testing)

    private List<HexVector2> getHexagonalSlotPositions(int divider) {
        List<HexVector2> positions = new ArrayList<>();

        // Center position
        positions.add(TypeUtil.hexVector2(0, 0));

        // Calculate number of rings based on divider
        int rings;
        switch (divider) {
            case 1:
                rings = 0; // Only center
                break;
            case 3:
                rings = 1; // Center + ring 1 = 7 slots
                break;
            case 5:
                rings = 2; // Center + ring 1 + ring 2 = 19 slots
                break;
            case 7:
                rings = 3; // Center + ring 1 + ring 2 + ring 3 = 37 slots
                break;
            default:
                rings = 0;
        }

        // Add hexagonal rings around center
        for (int ring = 1; ring <= rings; ring++) {
            positions.addAll(getHexRing(0, 0, ring));
        }

        return positions;
    }

    private List<HexVector2> getHexRing(int centerQ, int centerR, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        // Start at position directly north of center
        int q = centerQ;
        int r = centerR - radius;

        // Direction vectors for hex neighbors (in order: E, SE, S, SW, W, NW)
        int[][] directions = {
            {1, 0},    // E
            {0, 1},    // SE
            {-1, 1},   // S
            {-1, 0},   // SW
            {0, -1},   // W
            {1, -1}    // NW
        };

        // Walk around the ring
        for (int side = 0; side < 6; side++) {
            for (int step = 0; step < radius; step++) {
                ring.add(TypeUtil.hexVector2(q, r));
                q += directions[side][0];
                r += directions[side][1];
            }
        }

        return ring;
    }

    private boolean containsPosition(List<HexVector2> positions, int q, int r) {
        return positions.stream()
            .anyMatch(pos -> pos.getQ() == q && pos.getR() == r);
    }

    private void printPositions(List<HexVector2> positions) {
        System.out.println("Total positions: " + positions.size());
        for (int i = 0; i < positions.size(); i++) {
            HexVector2 pos = positions.get(i);
            System.out.printf("  [%2d] <%d;%d>%n", i, pos.getQ(), pos.getR());
        }
    }
}
