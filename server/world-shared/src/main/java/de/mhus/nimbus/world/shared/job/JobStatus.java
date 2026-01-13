package de.mhus.nimbus.world.shared.job;

/**
 * Job execution status.
 * Represents the lifecycle state of an async job.
 */
public enum JobStatus {
    /**
     * Job is waiting to be picked up by scheduler.
     */
    PENDING,

    /**
     * Job is currently being executed.
     */
    RUNNING,

    /**
     * Job completed successfully.
     */
    COMPLETED,

    /**
     * Job failed with error.
     */
    FAILED
}
