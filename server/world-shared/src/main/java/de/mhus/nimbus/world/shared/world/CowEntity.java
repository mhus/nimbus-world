package de.mhus.nimbus.world.shared.world;

/**
 * Copy-on-Write interface for entities that support instance overlays.
 * Entities implementing this interface can be used with {@link CowMerger}
 * to merge base world data with instance-specific overrides.
 *
 * <p>The COW pattern works as follows:
 * <ul>
 *   <li>Base world entities are the original, shared data</li>
 *   <li>Instance entities override base entities by matching {@link #getCowId()}</li>
 *   <li>Tombstones ({@link #isCowEnabled()} == false) mark deletions in the instance</li>
 * </ul>
 */
public interface CowEntity {

    /**
     * Returns the unique identifier within a world context.
     * This is used to match base and instance entities during merge.
     * E.g., itemId for WItemPosition, entityId for WEntity, name for WChest.
     */
    String getCowId();

    /**
     * Returns whether this entity is active.
     * In COW context, enabled=false serves as a tombstone marker,
     * indicating the base entity was deleted in this instance.
     */
    boolean isCowEnabled();

    /**
     * Returns the worldId this entity belongs to.
     */
    String getWorldId();

    /**
     * Sets the worldId on this entity.
     * Used when copying a base entity into an instance.
     */
    void setWorldId(String worldId);

}
