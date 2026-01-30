package de.mhus.nimbus.world.generator.mcp;

/**
 * Base exception for MCP job execution errors.
 */
public class McpJobException extends Exception {

    public McpJobException(String message) {
        super(message);
    }

    public McpJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
