package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.world.WWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditCachePainterTest {

    private EditCachePainter painter;
    private Set<String> paintedBlocks;

    @BeforeEach
    void setUp() {
        WEditCacheService editService = mock(WEditCacheService.class);
        WWorld world = mock(WWorld.class);
        BlockDef blockDef = mock(BlockDef.class);
        paintedBlocks = new HashSet<>();
        // Custom Painter: markiere alle gesetzten Blöcke
        doAnswer(invocation -> {
            Object block = invocation.getArgument(3);
            if (block instanceof Block) paintedBlocks.add(block.toString());
            return null;
        }).when(editService).doSetAndSendBlock(any(), any(), any(), any(Block.class), anyInt());
        painter = new EditCachePainter(editService);
        painter.setContext(world, "layer", "model", 0, blockDef);
    }

    @Test
    void testCube() {
        paintedBlocks.clear();
        painter.cube(0, 0, 0, 2, 2, 2);
        assertEquals(8, paintedBlocks.size(), "Cube sollte 8 Blöcke setzen");
    }

    @Test
    void testLine() {
        paintedBlocks.clear();
        painter.line(0, 0, 0, 0, 0, 3);
        assertEquals(4, paintedBlocks.size(), "Line sollte 4 Blöcke setzen");
    }

    @Test
    void testCircleY() {
        paintedBlocks.clear();
        painter.circleY(0, 0, 0, 2);
        assertFalse(paintedBlocks.isEmpty(), "CircleY sollte Blöcke setzen");
    }

    @Test
    void testSphereOutline() {
        paintedBlocks.clear();
        painter.sphereOutline(0, 0, 0, 2);
        assertFalse(paintedBlocks.isEmpty(), "SphereOutline sollte Blöcke setzen");
    }

    @Test
    void testCustomPainterEffect() {
        paintedBlocks.clear();
        painter.setPainter(EditCachePainter.RASTER_PAINTER);
        painter.cube(0, 0, 0, 2, 1, 1);
        assertTrue(paintedBlocks.size() < 2, "Custom Painter sollte weniger Blöcke setzen");
    }
}
