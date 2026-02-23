package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.shared.types.BlockDef;

/**
 * Interface for writing blocks to a target storage.
 * Implementations can write to WEditCache (online editing) or directly to LayerChunkData (generation).
 */
public interface BlockWriteTarget {

    /**
     * Write a block at the given world coordinates.
     *
     * @param blockDef block definition
     * @param groupId  group identifier (nullable)
     * @param level    block level override (nullable, if null uses blockDef's level)
     * @param x        world X coordinate
     * @param y        world Y coordinate
     * @param z        world Z coordinate
     */
    void writeBlock(BlockDef blockDef, String groupId, Integer level, int x, int y, int z);

    /**
     * Check if a block exists at the given world coordinates.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return true if a block exists at the position
     */
    boolean hasBlock(int x, int y, int z);
}
