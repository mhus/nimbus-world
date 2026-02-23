package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Getter;
import lombok.Setter;

/**
 * EditCache-based painter that writes blocks through WEditCacheService.
 * Extends EditBlockPainter for geometric methods, implements BlockWriteTarget for edit cache writes.
 * Supports a decorator chain via BlockPainter inner class.
 */
public class EditCachePainter extends EditBlockPainter implements BlockWriteTarget {

    public static final BlockPainter DEFAULT_PAINTER = new BlockPainter();
    public static final BlockPainter RASTER_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            if ((x + y + z) % 2 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };
    public static final BlockPainter GRID_5_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            if (x % 5 == 0 || y % 5 == 0 || z % 5 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };
    public static final BlockPainter GRID_2_PAINTER = new BlockPainter() {
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            if (x % 2 == 0 || y % 2 == 0 || z % 2 == 0) {
                super.paint(painter, x, y, z);
            }
        }
    };

    public static class ConcatinatingPainter extends BlockPainter {
        private final BlockPainter[] painters;
        public ConcatinatingPainter(BlockPainter... painters) {
            this.painters = painters;
        }
        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            for (BlockPainter p : painters) {
                p.paint(painter, x, y, z);
            }
        }
    }

    /**
     * NoOverwritePainter - Painter flavor that does not overwrite existing blocks.
     */
    public static class NoOverwritePainter extends BlockPainter {
        private final BlockPainter wrappedPainter;

        public NoOverwritePainter(BlockPainter wrappedPainter) {
            this.wrappedPainter = wrappedPainter;
        }

        @Override
        public void paint(EditCachePainter painter, int x, int y, int z) {
            boolean blockExists = painter.editService.findByCoordinates(
                    painter.world.getWorldId(),
                    painter.layerDataId,
                    painter.modelName,
                    x, y, z
            ).isPresent();

            if (!blockExists) {
                wrappedPainter.paint(painter, x, y, z);
            }
        }
    }

    @Getter
    final WEditCacheService editService;
    @Getter
    private WWorld world;
    @Getter
    private String layerDataId;
    @Getter
    private String modelName;
    @Getter @Setter
    private BlockPainter painter = DEFAULT_PAINTER;
    @Getter
    private ManipulatorContext context;

    public EditCachePainter(WEditCacheService editService) {
        this.editService = editService;
        setWriteTarget(this);
    }

    public void setContext(WWorld world, String layerDataId, String modelName, String groupId, BlockDef blockDef) {
        this.world = world;
        this.layerDataId = layerDataId;
        this.modelName = modelName;
        setBlockDef(blockDef);
        setGroupId(groupId);
    }

    public void setManipulatorContext(ManipulatorContext context) {
        this.context = context;
    }

    /**
     * Override paint to use the decorator chain.
     */
    @Override
    public void paint(int x, int y, int z) {
        painter.paint(this, x, y, z);
    }

    @Override
    public void writeBlock(BlockDef blockDef, String groupId, Integer level, int x, int y, int z) {
        Block block = Block.builder()
                .position(Vector3Int.builder().x(x).y(y).z(z).build())
                .build();
        blockDef.fillBlock(block);
        if (level != null) {
            block.setLevel(level);
        }
        editService.doSetAndSendBlock(world, layerDataId, modelName, block, groupId);

        if (context != null && context.getModelSelector() != null) {
            String color = context.getModelSelector().getDefaultColor();
            if (color == null) {
                color = "#00ff00";
            }
            context.getModelSelector().addBlock(x, y, z, color);
        }
    }

    @Override
    public boolean hasBlock(int x, int y, int z) {
        return editService.findByCoordinates(
                world.getWorldId(), layerDataId, modelName, x, y, z
        ).isPresent();
    }

    public static class BlockPainter {
        public void paint(EditCachePainter painter, int x, int y, int z) {
            painter.writeBlock(painter.getBlockDef(), painter.getGroupId(), painter.getLevel(), x, y, z);
        }
    }
}
