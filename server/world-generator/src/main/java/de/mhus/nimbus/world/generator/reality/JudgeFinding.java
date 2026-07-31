package de.mhus.nimbus.world.generator.reality;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single balance finding from the AI judge (C2): a concrete, actionable issue B2 can act on.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeFinding {
    /** Item slug, category, class name or "economy" this finding relates to. */
    private String ref;
    /** "minor" | "major" — only major findings should block acceptance. */
    private String severity;
    private String issue;
    private String suggestion;
}
