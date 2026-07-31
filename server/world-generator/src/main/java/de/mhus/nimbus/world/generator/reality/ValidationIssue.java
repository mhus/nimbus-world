package de.mhus.nimbus.world.generator.reality;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A single finding of the mechanical reality validator (C1). {@link Severity#ERROR} blocks
 * materialization; {@link Severity#WARNING}/{@link Severity#INFO} are tolerated and only reported.
 */
@Data
@AllArgsConstructor
public class ValidationIssue {

    public enum Severity {
        ERROR, WARNING, INFO
    }

    private Severity severity;
    /** Machine-readable code, e.g. "unknown_item_class". */
    private String code;
    private String message;
    /** Item/class slug this issue relates to (nullable). */
    private String ref;
}
