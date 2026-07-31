package de.mhus.nimbus.world.generator.reality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the AI balance judge (C2). The AI fills {@code acceptable}, {@code score},
 * {@code summary} and {@code findings}; {@code errors} is set by {@link RealityJudge} on an
 * infrastructure failure (AI unavailable / unparsable) so callers can tell "balance rejected"
 * (conclusive) apart from "could not judge" (inconclusive).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeVerdict {

    /** Whether the plan is balanced enough to proceed (within tolerance). */
    private boolean acceptable;
    /** Overall balance score 0..100 (higher = better). */
    private Integer score;
    private String summary;
    private List<JudgeFinding> findings;

    /** Infrastructure errors (not part of the AI contract); non-empty = inconclusive verdict. */
    @JsonIgnore
    private List<String> errors = new ArrayList<>();

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /** True if the judge actually produced a verdict (no infra error). */
    public boolean isConclusive() {
        return !hasErrors();
    }

    /** Major-severity findings — the ones that should trigger a refine loop. */
    public List<JudgeFinding> majorFindings() {
        List<JudgeFinding> out = new ArrayList<>();
        if (findings != null) {
            for (JudgeFinding f : findings) {
                if (f != null && f.getSeverity() != null && f.getSeverity().trim().equalsIgnoreCase("major")) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    public static JudgeVerdict failure(String error) {
        JudgeVerdict v = new JudgeVerdict();
        v.setAcceptable(false);
        v.getErrors().add(error);
        return v;
    }
}
