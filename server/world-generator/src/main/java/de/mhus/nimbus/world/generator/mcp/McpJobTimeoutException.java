package de.mhus.nimbus.world.generator.mcp;

import lombok.Getter;

/**
 * Exception thrown when a job exceeds its timeout during synchronous execution.
 */
@Getter
public class McpJobTimeoutException extends McpJobException {

    private final String jobId;

    public McpJobTimeoutException(String message, String jobId) {
        super(message);
        this.jobId = jobId;
    }
}
