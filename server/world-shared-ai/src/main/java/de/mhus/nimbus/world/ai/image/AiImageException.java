package de.mhus.nimbus.world.ai.image;

/**
 * Exception thrown when AI image generation fails.
 */
public class AiImageException extends Exception {

    public AiImageException(String message) {
        super(message);
    }

    public AiImageException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiImageException(Throwable cause) {
        super(cause);
    }
}
