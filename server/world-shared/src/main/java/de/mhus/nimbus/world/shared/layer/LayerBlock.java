package de.mhus.nimbus.world.shared.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.generated.types.Block;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Layer block wrapper.
 * Contains a block with additional layer-specific properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayerBlock {

    /**
     * The actual block data.
     */
    private Block block;

    /**
     * Layer-specific metadata.
     * Can be used for layer-specific behavior or description.
     */
    private String metadata;

    /**
     * Group identifier for this block.
     * Default is 0 (no group).
     * Can be used to organize and manage blocks in groups.
     */
    @Builder.Default
    private int group = 0;
}
