package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import lombok.experimental.UtilityClass;

/**
 * Utility methods for Block operations.
 */
@UtilityClass
public class BlockUtil {

    /**
     * Check if block type represents AIR (empty space).
     * AIR types: "0", "w/0", null, empty string
     *
     * @param blockTypeId Block type identifier
     * @return true if block type is AIR
     */
    public static boolean isAirType(String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return true;
        }
        return "0".equals(blockTypeId) || "w/0".equals(blockTypeId) || "air".equals(blockTypeId) || "w/air".equals(blockTypeId);
    }

    /**
     * Generate position key for Redis hash storage.
     * Format: "x:y:z" (e.g., "15:64:23")
     *
     * @param position Vector3 position
     * @return Position key string
     */
    public static String positionKey(Vector3Int position) {
        if (position == null) {
            return "0:0:0";
        }
        return String.format("%d:%d:%d",
                position.getX(),
                position.getY(),
                position.getZ());
    }

    /**
     * Generate position key from coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Position key string
     */
    public static String positionKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    /**
     * Extract position key from Block.
     *
     * @param block Block instance
     * @return Position key string
     */
    public static String positionKey(Block block) {
        return block != null ? positionKey(block.getPosition()) : "0:0:0";
    }

    /**
     * Extract group from blockId.
     * Format: "{group}/{key}" (e.g., "core:stone" → "core", "w/123" → "w")
     * If no group prefix, defaults to "w".
     */
    public static String extractGroupFromBlockId(String blockId) {
        if (blockId == null || !blockId.contains("/")) {
            return "w";  // default group
        }
        String[] parts = blockId.split("/", 2);
        String group = parts[0].toLowerCase();
        // Validate group format
        if (group.matches("^[a-z0-9_-]+$")) {
            return group;
        }
        return "w";
    }

    /**
     * Normalize blockId to always include group prefix.
     * Format: "{group}/{key}"
     * If no group prefix exists, prepends "w/" as default.
     *
     * Examples:
     * - "310" → "w/310"
     * - "w/310" → "w/310"
     * - "core/stone" → "core/stone"
     *
     * @param blockId Block identifier (may be with or without group prefix)
     * @return Normalized block identifier with group prefix
     */
    public static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return blockId;
        }
        // If already has group prefix, return as-is
        if (blockId.contains("/")) {
            return blockId;
        }
        // Add default "w/" prefix
        return "w/" + blockId;
    }

    /**
     * Clone a block with all its properties.
     * Creates a deep copy of the block, excluding the position.
     * Position must be set separately after cloning.
     *
     * @param originalBlock Block to clone
     * @return Cloned block without position
     */
    public static Block cloneBlock(Block originalBlock) {
        if (originalBlock == null) {
            return null;
        }
       // TDOO also cloen modifiers ?
        return Block.builder()
                .blockTypeId(originalBlock.getBlockTypeId())
                .offsets(originalBlock.getOffsets())
                .rotation(originalBlock.getRotation())
                .faceVisibility(originalBlock.getFaceVisibility())
                .status(originalBlock.getStatus())
                .modifiers(originalBlock.getModifiers())
                .metadata(originalBlock.getMetadata())
                .build();
    }

    public static String toChunkKey(int cx, int cz) {
        return cx + ":" + cz;
    }

    public static String toChunkKey(double cx, double cz) {
        return (int)Math.floor(cx) + ":" + (int)Math.floor(cz);
    }

    public static String toChunkKey(WWorld world, Vector3 position) {
        int cx = world.getChunkX(position.getX());
        int cz = world.getChunkZ(position.getZ());
        return (int)cx + ":" + (int)cz;
    }

    public static Block createAirBlock(int x, int y, int z) {
        return Block.builder().blockTypeId("0")
                .position(
                        Vector3Int.builder()
                                .x(x)
                                .y(y)
                                .z(z)
                                .build()
                )
                .build();
    }
}
