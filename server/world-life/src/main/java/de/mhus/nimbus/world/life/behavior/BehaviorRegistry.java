package de.mhus.nimbus.world.life.behavior;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for entity behavior implementations.
 * Discovers all EntityBehavior beans and provides lookup by behavior type.
 *
 * Uses lazy loading pattern to avoid circular dependencies
 * (similar to CommandService pattern).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorRegistry {

    private final ApplicationContext applicationContext;

    /**
     * Behavior registry: behaviorType → EntityBehavior instance
     * Lazily initialized on first access.
     */
    private volatile Map<String, EntityBehavior> behaviors;

    /**
     * Get all registered behaviors.
     * Lazy-loads behaviors from ApplicationContext on first call.
     *
     * @return Map of behaviorType → EntityBehavior
     */
    private Map<String, EntityBehavior> getBehaviors() {
        if (behaviors == null) {
            synchronized (this) {
                if (behaviors == null) {
                    Map<String, EntityBehavior> behaviorBeans = applicationContext.getBeansOfType(EntityBehavior.class);
                    behaviors = new ConcurrentHashMap<>();

                    for (EntityBehavior behavior : behaviorBeans.values()) {
                        String behaviorType = behavior.getBehaviorType();
                        behaviors.put(behaviorType, behavior);
                        log.info("Registered behavior: {}", behaviorType);
                    }

                    log.info("BehaviorRegistry initialized with {} behaviors", behaviors.size());
                }
            }
        }
        return behaviors;
    }

    /**
     * Get behavior by type.
     *
     * @param behaviorType Behavior type identifier
     * @return EntityBehavior instance, or null if not found
     */
    public EntityBehavior getBehavior(String behaviorType) {
        if (behaviorType == null || behaviorType.isBlank()) {
            log.warn("Behavior type is null or blank");
            return null;
        }

        EntityBehavior behavior = getBehaviors().get(behaviorType);

        if (behavior == null) {
            log.warn("Behavior not found: {}", behaviorType);
        }

        return behavior;
    }

    /**
     * Check if a behavior type is registered.
     *
     * @param behaviorType Behavior type identifier
     * @return True if behavior exists
     */
    public boolean hasBehavior(String behaviorType) {
        return getBehavior(behaviorType) != null;
    }

    /**
     * Get all registered behavior types.
     *
     * @return Set of behavior type identifiers
     */
    public java.util.Set<String> getBehaviorTypes() {
        return getBehaviors().keySet();
    }

    /**
     * Get number of registered behaviors.
     *
     * @return Behavior count
     */
    public int getBehaviorCount() {
        return getBehaviors().size();
    }
}
