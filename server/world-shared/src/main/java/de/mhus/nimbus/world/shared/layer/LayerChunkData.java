package de.mhus.nimbus.world.shared.layer;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Height data for terrain generation. Key format: "x,z" -> [maxHeight, minHeight, groundLevel, waterLevel?]
     * Ignore deserialization errors for backward compatibility with old array format.
     */
    @Builder.Default
    private Map<String, int[]> heightData = new HashMap<>();

    /**
     * Custom setter to handle both old and new heightData formats.
     * Ignores deserialization errors from old array format.
     */
    @JsonSetter("heightData")
    public void setHeightDataSafe(Object value) {
        if (value instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, int[]> mapValue = (Map<String, int[]>) value;
                this.heightData = mapValue;
            } catch (Exception e) {
                // Ignore - use empty map
                this.heightData = new HashMap<>();
            }
        } else {
            // Old array format or other - ignore and use empty map
            this.heightData = new HashMap<>();
        }
    }
}
