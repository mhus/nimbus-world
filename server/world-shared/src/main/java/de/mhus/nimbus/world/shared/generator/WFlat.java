package de.mhus.nimbus.world.shared.generator;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB Entity for flat terrain data used in world generation.
 * Stores height maps, column definitions, and extra blocks for world layers.
 */
@Document(collection = "w_flats")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_layerDataId_flatId_idx", def = "{ 'worldId': 1, 'layerDataId': 1, 'flatId': 1 }", unique = true)
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WFlat implements Identifiable {

    public static final int NOT_SET = 0;
    private static final int MAX_SIZE = 800;

    @Id
    @Getter
    private String id;

    @Indexed
    @Getter
    private String worldId;

    @Indexed
    @Getter
    private String layerDataId;

    @Getter
    private String flatId;

    @Getter @Setter
    private String title;

    @Getter @Setter
    private String description;

    @Getter
    private int mountX;
    @Getter
    private int mountZ;
    @Getter
    private int oceanLevel;
    @Getter @Setter
    private String oceanBlockId;
    /**
     * If true, unknown/not set columns are protected from modification
     * Set this after initial setting up block you want to modify. Leave others untouched at 0.
     */
    @Builder.Default
    @Getter @Setter
    private boolean unknownProtected = false;
    @Builder.Default
    @Getter @Setter
    private boolean borderProtected = true;

    @Getter
    private int sizeX;
    @Getter
    private int sizeZ;
    @Getter
    private byte[] levels;
    @Getter
    private byte[] columns;
    @Builder.Default
    private HashMap<String, String> extraBlocks = new HashMap<>(); // for water and ocean ...

    @Builder.Default
    private HashMap<Byte, MaterialDefinition> materials = new HashMap<>();

    public void initWithSize(int sizeX, int sizeZ) {
        if (sizeX <= 0 || sizeZ <= 0 || sizeX > MAX_SIZE || sizeZ > MAX_SIZE)
            throw new IllegalArgumentException("Size out of range");
        if (this.sizeX != 0)
            throw  new IllegalStateException("Already initialized");
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.levels = new byte[sizeX * sizeZ];
        this.columns = new byte[sizeX * sizeZ];
    }

    public boolean setLevel(int x, int z, int level) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ)
            throw new IllegalArgumentException("Coordinates out of range");
        if (level < 0) level = 0;
        if (level > 255) level = 255;
        if (borderProtected) {
            if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1)
                return false;
        }
        if (unknownProtected && !isColumnSet(x, z)) {
            return false;
        }
        levels[x + z * sizeX] = (byte)level;
        return true;
    }

    public int getLevel(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ)
            throw new IllegalArgumentException("Coordinates out of range");
        return Byte.toUnsignedInt(levels[x + z * sizeX]);
    }

    public boolean setColumn(int x, int z, int definition) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ)
            throw new IllegalArgumentException("Coordinates out of range");
        if (definition < 0 || definition > 255)
            throw new IllegalArgumentException("Size out of range");
        if (borderProtected) {
            if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1)
                return false;
        }
        if (unknownProtected && !isColumnSet(x, z)) {
            return false;
        }
        columns[x + z * sizeX] = (byte)definition;
        return true;
    }

    public int getColumn(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ)
            throw new IllegalArgumentException("Coordinates out of range");
        return Byte.toUnsignedInt(columns[x + z * sizeX]);
    }

    public boolean isColumnSet(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ)
            throw new IllegalArgumentException("Coordinates out of range");
        return columns[x + z * sizeX] == NOT_SET ? false : true;
    }

    public MaterialDefinition getColumnMaterial(int x, int z) {
        int definition = getColumn(x, z);
        return getMaterial(definition);
    }

    public void setExtraBlock(int x, int y, int z, String blockId) {
        String name = x + ":" + z + ":" + y;
        if (blockId == null)
            extraBlocks.remove(name);
        else
            extraBlocks.put(name, blockId);
    }

    public String getExtraBlock(int x, int y, int z) {
        String name = x + ":" + z + ":" + y;
        return extraBlocks.get(name);
    }

    public String[] getExtraBlocksForColumn(int x, int z) {
        String prefix = x + ":" + z + ":";
        String[] res = new String[256];
        for (String key : extraBlocks.keySet()) {
            if (key.startsWith(prefix)) {
                String yStr = key.substring(prefix.length());
                int y = Integer.parseInt(yStr);
                if (y >= 0 && y < 256)
                    res[y] = extraBlocks.get(key);
            }
        }
        return res;
    }

    public void setMaterial(int id, MaterialDefinition definition) {
        if (id < 1 || id > 254) // 0 = UNKNOWN_PROTECTED, 255 = UNKNOWN_NOT_PROTECTED
            throw new IllegalArgumentException("Definition id out of range");
        if (id == NOT_SET)
            return;
        materials.put((byte)id, definition);
    }

    public MaterialDefinition getMaterial(int id) {
        if (id < 0 || id > 255)
            throw new IllegalArgumentException("Definition id out of range");
        if (id == NOT_SET)
            return null;
        return materials.get((byte)id);
    }

    public HashMap<Byte, MaterialDefinition> getMaterials() {
        return materials;
    }

    public void setMaterials(HashMap<Byte, MaterialDefinition> materials) {
        this.materials = materials != null ? materials : new HashMap<>();
    }

    public void setLevels(byte[] levels) {
        if (levels == null)
            throw new IllegalArgumentException("Levels cannot be null");
        if (levels.length != sizeX * sizeZ)
            throw new IllegalArgumentException("Levels array size mismatch");
        this.levels = levels;
    }

    public void setColumns(byte[] columns) {
        if (columns == null)
            throw new IllegalArgumentException("Columns cannot be null");
        if (columns.length != sizeX * sizeZ)
            throw new IllegalArgumentException("Columns array size mismatch");
        this.columns = columns;
    }

    @Getter
    private Instant createdAt;

    @Getter
    private Instant updatedAt;

    /**
     * Initialize timestamps for new flat.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialDefinition {
        private String blockDef; // id + "@s:" + state e.g. n:s@s:100, n:s@s:101 - siehe BlockDef
        private String nextBlockDef; // id + "@" + state
        private boolean hasOcean;
        @Builder.Default
        private boolean isBlockMapDelta = true;
        @Builder.Default
        private Map<Integer, String> blockAtLevels = new HashMap<>(); // y -> block id

        /**
         * Returns the blockId for the y - starts at level
         * @param level - value from levels or ocean level
         * @param y - y coordinate
         * @return null or block id
         */
        public String getBlockAt(WFlat flat, int level, int y, String[] extraBlocks) {
            if (y < 0 || y > 255)
                return null;
            // first: my own block
            if (y == level)
                return blockDef;
            // second: extra block
            if (extraBlocks != null && extraBlocks[y] != null)
                return extraBlocks[y];
            // third: next block (fill below level - no ocean check here!)
            if (y < level) {
                if (isBlockMapDelta) {
                    String blockDefAtLevel = blockAtLevels.get(level-y);
                    if (blockDefAtLevel != null)
                        return blockDefAtLevel;
                } else {
                    String blockDefAtLevel = blockAtLevels.get(level-y);
                    if (blockDefAtLevel != null)
                        return blockDefAtLevel;
                }
                return nextBlockDef != null ? nextBlockDef : blockDef;
            }
            // finally: ocean block (only above terrain level!)
            if (hasOcean && y > level && y == flat.getOceanLevel())
                return flat.getOceanBlockId();
            // or air
            return null;
        }
    }
}
