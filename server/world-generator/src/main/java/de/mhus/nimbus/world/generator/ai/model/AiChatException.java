package de.mhus.nimbus.world.generator.ai.model;

/**
 * Exception thrown when AI chat operations fail.
 */
public class AiChatException extends Exception {

    public AiChatException(String message) {
        super(message);
    }

    public AiChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
