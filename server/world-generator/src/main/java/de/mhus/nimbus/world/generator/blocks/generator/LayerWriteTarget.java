package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * BlockWriteTarget implementation that writes blocks directly into LayerChunkData maps.
 * Used by the ModelBuilder to generate terrain without going through WEditCacheService.
 */
@RequiredArgsConstructor
public class LayerWriteTarget implements BlockWriteTarget {

    private final WWorld world;

    @Getter
    private final Map<String, LayerChunkData> chunkDataMap;

    @Override
    public void writeBlock(BlockDef blockDef, String groupId, Integer level, int x, int y, int z) {
        Block block = Block.builder()
                .position(Vector3Int.builder().x(x).y(y).z(z).build())
                .build();
        blockDef.fillBlock(block);
        if (level != null) {
            block.setLevel(level);
        }

        String chunkKey = world.getChunkKey(x, z);
        LayerChunkData chunkData = chunkDataMap.computeIfAbsent(chunkKey, k ->
                LayerChunkData.builder()
                        .cx(world.getChunkX(x))
                        .cz(world.getChunkZ(z))
                        .build()
        );

        LayerBlock layerBlock = LayerBlock.builder()
                .block(block)
                .group(groupId)
                .build();
        chunkData.getBlocks().add(layerBlock);
    }

    @Override
    public boolean hasBlock(int x, int y, int z) {
        String chunkKey = world.getChunkKey(x, z);
        LayerChunkData chunkData = chunkDataMap.get(chunkKey);
        if (chunkData == null) return false;
        return chunkData.getBlocks().stream().anyMatch(lb -> {
            var pos = lb.getBlock().getPosition();
            return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
        });
    }
}
