package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;

import java.util.*;

/**
 * Console visualization for ModelBuilder results.
 * Renders Y-layer slices as XZ grids with blockType and level.
 */
public final class ModelBuilderDump {

    private ModelBuilderDump() {}

    /**
     * Dump all blocks from a ModelBuilderContext as Y-layer ASCII slices.
     */
    public static String dump(ModelBuilderContext context) {
        return dump(context.getChunkDataMap());
    }

    /**
     * Dump all blocks from a chunkDataMap as Y-layer ASCII slices.
     */
    public static String dump(Map<String, LayerChunkData> chunkDataMap) {
        // Collect all blocks with positions
        record BlockEntry(int x, int y, int z, String type, Integer level, String group) {}

        List<BlockEntry> entries = new ArrayList<>();
        for (LayerChunkData chunk : chunkDataMap.values()) {
            for (LayerBlock lb : chunk.getBlocks()) {
                Block b = lb.getBlock();
                var pos = b.getPosition();
                entries.add(new BlockEntry(
                        pos.getX(), pos.getY(), pos.getZ(),
                        b.getBlockTypeId(), b.getLevel(), lb.getGroup()
                ));
            }
        }

        if (entries.isEmpty()) {
            return "(empty - no blocks)";
        }

        // Find bounds
        int minX = entries.stream().mapToInt(BlockEntry::x).min().orElse(0);
        int maxX = entries.stream().mapToInt(BlockEntry::x).max().orElse(0);
        int minY = entries.stream().mapToInt(BlockEntry::y).min().orElse(0);
        int maxY = entries.stream().mapToInt(BlockEntry::y).max().orElse(0);
        int minZ = entries.stream().mapToInt(BlockEntry::z).min().orElse(0);
        int maxZ = entries.stream().mapToInt(BlockEntry::z).max().orElse(0);

        // Index: y -> (x,z) -> entry
        Map<Integer, Map<String, BlockEntry>> byY = new HashMap<>();
        for (BlockEntry e : entries) {
            byY.computeIfAbsent(e.y, k -> new HashMap<>())
                    .put(e.x + "," + e.z, e);
        }

        // Calculate cell width
        int cellWidth = 4; // minimum
        for (BlockEntry e : entries) {
            int len = formatCell(e.type, e.level).length();
            if (len + 1 > cellWidth) cellWidth = len + 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Blocks: %d, Y-range: [%d..%d], X: [%d..%d], Z: [%d..%d]%n",
                entries.size(), minY, maxY, minX, maxX, minZ, maxZ));

        // Render bottom-to-top (Y ascending)
        for (int y = maxY; y >= minY; y--) {
            Map<String, BlockEntry> layer = byY.get(y);
            if (layer == null) continue;

            sb.append(String.format("%n=== Y=%d === (%d blocks)%n", y, layer.size()));

            // Header: X axis
            sb.append(String.format("%" + cellWidth + "s", "z\\x"));
            for (int x = minX; x <= maxX; x++) {
                sb.append(String.format("%" + cellWidth + "d", x));
            }
            sb.append('\n');

            // Rows: Z axis
            for (int z = minZ; z <= maxZ; z++) {
                sb.append(String.format("%" + cellWidth + "d", z));
                for (int x = minX; x <= maxX; x++) {
                    BlockEntry e = layer.get(x + "," + z);
                    String cell = e != null ? formatCell(e.type, e.level) : ".";
                    sb.append(String.format("%" + cellWidth + "s", cell));
                }
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Format a single cell: abbreviated type + level.
     * "n:g" level 3 -> "g:3", "n:w" no level -> "w"
     */
    static String formatCell(String blockTypeId, Integer level) {
        String abbr = blockTypeId;
        if (abbr != null && abbr.contains(":")) {
            abbr = abbr.substring(abbr.indexOf(':') + 1);
        }
        if (abbr == null) abbr = "?";
        if (level != null) {
            return abbr + ":" + level;
        }
        return abbr;
    }
}
