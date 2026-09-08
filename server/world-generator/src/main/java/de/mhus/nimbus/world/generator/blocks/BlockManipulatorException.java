package de.mhus.nimbus.world.generator.blocks;

/**
 * Exception thrown when block manipulation fails.
 */
public class BlockManipulatorException extends Exception {
    private static final long serialVersionUID = 1L;

    public BlockManipulatorException(String message) {
        super(message);
    }

    public BlockManipulatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
