package de.mhus.nimbus.world.ai.model;

/**
 * Exception thrown when AI chat operations fail.
 */
public class AiChatException extends Exception {
    private static final long serialVersionUID = 1L;

    public AiChatException(String message) {
        super(message);
    }

    public AiChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
