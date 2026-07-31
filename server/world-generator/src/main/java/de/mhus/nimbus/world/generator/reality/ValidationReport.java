package de.mhus.nimbus.world.generator.reality;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the mechanical validation (C1) of a {@link RealityPlan}. Collects {@link ValidationIssue}s.
 * The plan is considered materializable ({@link #isValid()}) when there are no {@code ERROR}s —
 * warnings are tolerated (the tolerance model) and surfaced later in the reality manifest.
 */
@Getter
public class ValidationReport {

    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(ValidationIssue.Severity severity, String code, String message, String ref) {
        issues.add(new ValidationIssue(severity, code, message, ref));
    }

    public void error(String code, String message, String ref) {
        add(ValidationIssue.Severity.ERROR, code, message, ref);
    }

    public void warning(String code, String message, String ref) {
        add(ValidationIssue.Severity.WARNING, code, message, ref);
    }

    public List<ValidationIssue> errors() {
        return bySeverity(ValidationIssue.Severity.ERROR);
    }

    public List<ValidationIssue> warnings() {
        return bySeverity(ValidationIssue.Severity.WARNING);
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }

    /** True if the plan may be materialized (no ERROR-level issues). */
    public boolean isValid() {
        return !hasErrors();
    }

    /** Compact multi-line summary for logs / the reality manifest. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(errors().size()).append(" error(s), ").append(warnings().size()).append(" warning(s)");
        for (ValidationIssue i : issues) {
            sb.append("\n  [").append(i.getSeverity()).append("] ").append(i.getCode());
            if (i.getRef() != null) {
                sb.append(" (").append(i.getRef()).append(")");
            }
            sb.append(": ").append(i.getMessage());
        }
        return sb.toString();
    }

    private List<ValidationIssue> bySeverity(ValidationIssue.Severity severity) {
        List<ValidationIssue> out = new ArrayList<>();
        for (ValidationIssue i : issues) {
            if (i.getSeverity() == severity) {
                out.add(i);
            }
        }
        return out;
    }
}
