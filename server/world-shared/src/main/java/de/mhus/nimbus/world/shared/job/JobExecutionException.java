package de.mhus.nimbus.world.shared.job;

/**
 * Exception thrown during job execution.
 */
public class JobExecutionException extends Exception {

    public JobExecutionException(String message) {
        super(message);
    }

    public JobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
