package de.mhus.nimbus.world.life.logic;

/**
 * Thrown when a SpEL expression evaluation fails in the Logic Machine.
 */
public class LogicEvaluationException extends RuntimeException {

    public LogicEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
