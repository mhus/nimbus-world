package de.mhus.nimbus.world.generator.modelbuilder;

/**
 * Exception thrown when model building fails.
 */
public class ModelBuilderException extends Exception {

    public ModelBuilderException(String message) {
        super(message);
    }

    public ModelBuilderException(String message, Throwable cause) {
        super(message, cause);
    }
}
