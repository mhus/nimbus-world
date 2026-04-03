package de.mhus.nimbus.world.shared.world;

/**
 * Copy-on-Write interface for entities that support instance overlays.
 * Entities implementing this interface can be used with {@link CowUtil}
 * to merge base world data with instance-specific overrides.
 *
 * <p>The COW pattern works as follows:
 * <ul>
 *   <li>Base world entities are the original, shared data</li>
 *   <li>Instance entities override base entities by matching {@link #getCowId()}</li>
 *   <li>Tombstones ({@link #isCowTombstone()} == true) mark deletions in the instance</li>
 *   <li>{@code enabled} is independent of tombstone and controls gameplay visibility (GM enable/disable)</li>
 * </ul>
 */
public interface CowEntity {

    /**
     * Returns the unique identifier within a world context.
     * This is used to match base and instance entities during merge.
     */
    String getCowId();

    /**
     * Returns whether this entity is active (gameplay visibility).
     * Independent of tombstone — a disabled entity is still present, just not active.
     */
    boolean isCowEnabled();

    /**
     * Returns whether this entity is a tombstone (deleted in this instance).
     * Tombstoned entities are removed during COW merge.
     */
    boolean isCowTombstone();

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
