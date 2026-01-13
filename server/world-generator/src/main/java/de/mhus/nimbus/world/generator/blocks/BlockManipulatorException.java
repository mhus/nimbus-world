package de.mhus.nimbus.world.generator.blocks;

/**
 * Exception thrown when block manipulation fails.
 */
public class BlockManipulatorException extends Exception {

    public BlockManipulatorException(String message) {
        super(message);
    }

    public BlockManipulatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
