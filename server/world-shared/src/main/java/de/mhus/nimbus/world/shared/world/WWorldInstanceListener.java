package de.mhus.nimbus.world.shared.world;

/**
 * Listener interface for world instance lifecycle events.
 * Implementations can react to instance creation, deletion, and other events.
 *
 * Listeners are automatically discovered and registered via Spring's component scanning.
 * Simply implement this interface and annotate the class with @Component or @Service.
 */
public interface WWorldInstanceListener {

    /**
     * Called when a new world instance is created.
     *
     * @param event The event containing the created instance
     */
    void worldInstanceCreated(WorldInstanceEvent event);

    /**
     * Called when a world instance is deleted.
     *
     * @param event The event containing the deleted instance
     */
    void worldInstanceDeleted(WorldInstanceEvent event);
}
