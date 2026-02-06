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

    public JobExecutionException(WJob job, String simulatedError) {
        super(String.format("Error executing job %s: %s", job.getId(), simulatedError));
    }

    public JobExecutionException(WJob job, String jobInterrupted, InterruptedException e) {
        super(String.format("Job %s was interrupted: %s", job.getId(), jobInterrupted), e);
    }
}
