package de.mhus.nimbus.world.control.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for automatic instance cleanup.
 */
@Component
@ConfigurationProperties(prefix = "nimbus.instance.cleanup")
@Getter
@Setter
public class InstanceCleanupProperties {

    /**
     * Enable automatic cleanup of unused instances.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Maximum age of unused instances in hours before they are deleted.
     * Default: 48 hours
     */
    private long maxAgeHours = 48;

    /**
     * Interval in milliseconds between cleanup runs.
     * Default: 3600000 (1 hour)
     */
    private long cleanupIntervalMs = 3600000;

    /**
     * Maximum number of instances to delete in a single cleanup run.
     * Prevents overload if many instances need cleanup.
     * Default: 100
     */
    private int maxDeletesPerRun = 100;

    /**
     * Dry-run mode: Log what would be deleted but don't actually delete.
     * Useful for testing and monitoring.
     * Default: false
     */
    private boolean dryRun = false;
}
