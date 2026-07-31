package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing a Reality Instruction Document into a {@link RealityPlan}.
 * Mirrors the translator's {@code CompositionResult}: carries the parsed object, the intermediate
 * JSON (for debugging / persisting as {@code reality_plan}) and any errors.
 */
@Data
@Builder
public class RealityPlanResult {

    /** The parsed plan; null if parsing failed. */
    private RealityPlan plan;

    /** The intermediate JSON returned by the AI (cleaned); null if the AI step failed. */
    private String json;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isSuccessful() {
        return errors.isEmpty() && plan != null;
    }

    public boolean hasFailed() {
        return !isSuccessful();
    }

    public static RealityPlanResult success(RealityPlan plan, String json) {
        return RealityPlanResult.builder()
                .plan(plan)
                .json(json)
                .errors(new ArrayList<>())
                .build();
    }

    public static RealityPlanResult failure(String error) {
        return failure(error, null);
    }

    public static RealityPlanResult failure(String error, String json) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return RealityPlanResult.builder()
                .json(json)
                .errors(errors)
                .build();
    }
}
