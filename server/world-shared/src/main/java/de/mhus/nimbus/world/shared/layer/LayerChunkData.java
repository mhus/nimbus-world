package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.generated.types.HeightData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer-specific chunk data.
 * Similar to ChunkData but uses LayerBlock instead of Block.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayerChunkData {

    /**
     * Chunk X coordinate.
     */
    private int cx;

    /**
     * Chunk Z coordinate.
     */
    private int cz;

    /**
     * Layer blocks in this chunk.
     */
    @Builder.Default
    private List<LayerBlock> blocks = new ArrayList<>();

    /**
     * Height data for terrain generation.
     */
    @Builder.Default
    private List<HeightData> heightData = new ArrayList<>();
}
