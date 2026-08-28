package de.mhus.nimbus.world.shared.world;

/**
 * A pending block cooldown: one collected element that world-life has to reset once it expired.
 *
 * Stored in WProgress (playerId="world", type="block-cooldown", quest=chunkKey) as
 * progressData[blockKey] = { expiresAt: &lt;epoch millis&gt;, status: &lt;block status&gt; }.
 *
 * @param chunkKey  Chunk key (e.g. "1:2")
 * @param blockKey  Block identifier (block id or world coordinates "x,y,z")
 * @param expiresAt Epoch millis after which the cooldown is over
 * @param status    Block status that was set when the element was collected, or null if the
 *                  entry does not carry one (legacy entries written before the status was stored).
 *                  A null status means the reset must not verify the current status.
 */
public record WBlockCooldown(String chunkKey, String blockKey, long expiresAt, String status) {

    /**
     * @return true if the reset may verify that the block still carries the expected status
     */
    public boolean hasStatus() {
        return status != null;
    }
}
