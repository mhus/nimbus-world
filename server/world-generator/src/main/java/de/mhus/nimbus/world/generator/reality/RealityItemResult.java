package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a Phase 4 (item) generation run: which items were created and how many icons were
 * generated, plus any per-item errors (a failed icon does not abort the whole run).
 */
@Data
@Builder
public class RealityItemResult {

    @Builder.Default
    private List<String> createdItemIds = new ArrayList<>();

    @Builder.Default
    private int iconsGenerated = 0;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public int getItemsCreated() {
        return createdItemIds.size();
    }

    public void addError(String error) {
        errors.add(error);
    }
}
