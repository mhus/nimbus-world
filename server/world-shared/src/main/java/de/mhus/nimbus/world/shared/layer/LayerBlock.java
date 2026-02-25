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
     * Default is null (no group).
     * Can be used to organize and manage blocks in groups.
     * This is a serve side group and not for the client. There is another group in block.metadata.group which can be used for the client.
     * This group is mostly used to group blocks to structures while creation. e.g. building, road, river, tree, etc.
     * The client group in block.metadata.group is currently not used but can be used to select and highlight a specific structure at once.
     */
    private String group;
}
