package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditBlockPainter;
import de.mhus.nimbus.world.generator.blocks.generator.LayerWriteTarget;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Context for model building operations.
 * Provides cursor management, block painting, and random number generation.
 * Writes blocks directly into LayerChunkData maps via LayerWriteTarget.
 */
@Data
@Builder
public class ModelBuilderContext {

    private WWorld world;
    private WLayer layer;
    private Vector3Int position;
    private EditBlockPainter painter;
    private Random random;
    private int blockCount;

    @Builder.Default
    private Integer level = null;

    @Builder.Default
    private Map<String, LayerChunkData> chunkDataMap = new HashMap<>();

    // --- Cursor movement methods ---

    public void incrementY() {
        position.setY(position.getY() + 1);
    }

    public void decrementY() {
        position.setY(position.getY() - 1);
    }

    public void incrementX() {
        position.setX(position.getX() + 1);
    }

    public void decrementX() {
        position.setX(position.getX() - 1);
    }

    public void incrementZ() {
        position.setZ(position.getZ() + 1);
    }

    public void decrementZ() {
        position.setZ(position.getZ() - 1);
    }

    public void moveY(int delta) {
        position.setY(position.getY() + delta);
    }

    public void moveX(int delta) {
        position.setX(position.getX() + delta);
    }

    public void moveZ(int delta) {
        position.setZ(position.getZ() + delta);
    }

    // --- Level ---

    /**
     * Set the block level. Updates the current painter if present.
     */
    public void setLevel(Integer level) {
        this.level = level;
        if (painter != null) {
            painter.setLevel(level);
        }
    }

    // --- Painter methods ---

    /**
     * Set the current block type by creating a new painter.
     */
    public void setBlockType(String blockType) throws ModelBuilderException {
        this.painter = createPainter(blockType);
    }

    /**
     * Create a separate painter for the given block type.
     * Writes directly into this context's chunkDataMap via LayerWriteTarget.
     */
    public EditBlockPainter createPainter(String blockType) throws ModelBuilderException {
        BlockDef blockDef = BlockDef.of(blockType).orElse(null);
        if (blockDef == null) {
            throw new ModelBuilderException("Invalid blockType: " + blockType);
        }
        LayerWriteTarget writeTarget = new LayerWriteTarget(world, chunkDataMap);
        EditBlockPainter p = new EditBlockPainter();
        p.setBlockDef(blockDef);
        p.setLevel(level);
        p.setWriteTarget(writeTarget);
        return p;
    }

    /**
     * Paint a block at the current cursor position using the current painter.
     */
    public void paintAtCursor() {
        if (painter != null) {
            painter.paint(position.getX(), position.getY(), position.getZ());
            blockCount++;
        }
    }

    /**
     * Paint a block at the given position using the current painter.
     */
    public void paintAt(int x, int y, int z) {
        if (painter != null) {
            painter.paint(x, y, z);
            blockCount++;
        }
    }

    /**
     * Paint a block at the current cursor position using a specific painter.
     */
    public void paintAtCursor(EditBlockPainter p) {
        if (p != null) {
            p.paint(position.getX(), position.getY(), position.getZ());
            blockCount++;
        }
    }

    /**
     * Paint a block at the given position using a specific painter.
     */
    public void paintAt(EditBlockPainter p, int x, int y, int z) {
        if (p != null) {
            p.paint(x, y, z);
            blockCount++;
        }
    }

    // --- Helper methods ---

    /**
     * Generate a random integer in the range [min, max] (inclusive).
     */
    public int randomInt(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }
}
