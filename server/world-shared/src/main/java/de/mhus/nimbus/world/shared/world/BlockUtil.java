package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.experimental.UtilityClass;

/**
 * Utility methods for Block operations.
 */
@UtilityClass
public class BlockUtil {

    public final static String AIR_BLOCK_TYPE = "n:0"; // Standard AIR block type identifier
    public final static String DEFAULT_STATUS = "default"; // Default block status

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
        return "n:0".equals(blockTypeId) || "0".equals(blockTypeId) || "w:0".equals(blockTypeId) || "w/0".equals(blockTypeId);
    }

    /**
     * Extract position key from Block.
     *
     * @param block Block instance
     * @return Position key string
     */
    public static String positionKey(Block block) {
        return block != null ? TypeUtil.toStringWorldCoord(block.getPosition()) : "0,0,0";
    }

    /**
     * Extract collection from blockId.
     * Format: "{collection}:{key}" (e.g., "core:stone" → "core", "w:123" → "w")
     * If no collection prefix, defaults to "r".
     */
    public static String extractCollectionFromBlockId(String blockId) {
        if (blockId == null || !blockId.contains(":")) {
            return "r";  // default collection
        }
        String[] parts = blockId.split(":", 2);
        String group = parts[0].toLowerCase();
        // Validate group format
        if (group.matches("^[a-z0-9_-]+$")) {
            return group;
        }
        return "r";
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

    public static String toChunkKey(WWorld world, Vector3 position) {
        int cx = world.getChunkX(position.getX());
        int cz = world.getChunkZ(position.getZ());
        return cx + ":" + cz;
    }

    public static String toChunkKey(Vector2Int position) {
        return position.getX() + ":" + position.getZ();
    }

    public static String toChunkKey(int chunkX, int xhunkZ) {
        return chunkX + ":" + xhunkZ;
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

    public static String positionKey(int worldX, int worldZ) {
        return worldX + "," + worldZ;
    }

    public static boolean isStatus(String status) {
        if (status == null) return false;
        return status.matches("^[a-z0-9_-]*$");
    }

    public static boolean isStatusDefault(String status) {
        return status == null || status.isEmpty() || DEFAULT_STATUS.equals(status) || "0".equals(status); // legacy support for "0" as default status
    }
}
