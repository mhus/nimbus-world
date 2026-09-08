package de.mhus.nimbus.world.generator.mcp;

/**
 * Base exception for MCP job execution errors.
 */
public class McpJobException extends Exception {
    private static final long serialVersionUID = 1L;

    public McpJobException(String message) {
        super(message);
    }

    public McpJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
