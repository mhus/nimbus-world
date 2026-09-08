package de.mhus.nimbus.world.life.logic;

/**
 * Thrown when a SpEL expression evaluation fails in the Logic Machine.
 */
public class LogicEvaluationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LogicEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
