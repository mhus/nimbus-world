package de.mhus.nimbus.world.generator.reality;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Small summary for a Stage-D materializer (how many entities written, plus per-entry errors). */
@Data
@Builder
public class MaterializeResult {

    @Builder.Default
    private int created = 0;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public void inc() {
        created++;
    }

    public void addError(String error) {
        errors.add(error);
    }
}
