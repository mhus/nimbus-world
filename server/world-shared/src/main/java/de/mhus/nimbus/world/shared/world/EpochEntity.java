package de.mhus.nimbus.world.shared.world;

import java.util.List;

/**
 * Interface for entities that are epoch-dependent.
 * Entities implementing this interface are only visible in specific epochs.
 * An empty epoches list means the entity is NOT visible in any epoch.
 */
public interface EpochEntity {

    List<Integer> getEpoches();

    void setEpoches(List<Integer> epoches);
}
