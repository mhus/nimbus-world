package de.mhus.nimbus.world.generator.mcp;

/**
 * Runtime exception for MCP tool errors.
 * Spring AI MCP catches these and sends them as MCP Error-Response.
 */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
