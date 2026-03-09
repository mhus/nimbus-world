package de.mhus.nimbus.world.shared.redis;

import de.mhus.nimbus.generated.types.ItemBlockRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message format for item block update broadcasts via Redis.
 * Used to broadcast item add/remove changes to all world-player pods.
 *
 * Channel: world:{worldId}:b.iu
 *
 * Items are sent as ItemBlockRef[]. For removals, the item has texture='__deleted__'.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemBlockUpdateBroadcastMessage {

    private String worldId;

    /** Chunk X coordinate */
    private int cx;

    /** Chunk Z coordinate */
    private int cz;

    /**
     * Item updates to broadcast.
     * For adds/updates: full ItemBlockRef with texture.
     * For removals: ItemBlockRef with name, position, and texture='__deleted__'.
     */
    private List<ItemBlockRef> items;
}
