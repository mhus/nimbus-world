package de.mhus.nimbus.world.shared.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.generated.types.Block;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Block Register - stores marked block information for copy/paste operations.
 *
 * This data is stored per session in Redis with 24-hour TTL and is used to:
 * - Store block data when marking a block in the world (MARK_BLOCK action)
 * - Store block data when selecting from palette
 * - Retrieve block data for pasting (PASTE_BLOCK action)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockRegister {

    /**
     * The block data (required).
     */
    private Block block;

    /**
     * Layer name where the block was marked (optional).
     */
    private String layer;

    /**
     * Group ID where the block was marked (optional).
     * Can be a numeric string like "1", "2" or a descriptive string like "river-1234".
     */
    private String group;

    /**
     * Group name where the block was marked (optional).
     */
    private String groupName;

    /**
     * Whether the block is read-only (optional).
     */
    private Boolean readOnly;
}
