package de.mhus.nimbus.world.generator.structures;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of a StructurePlacer execution.
 */
@Data
@Builder
public class StructurePlacerResult {

    /**
     * Names of successfully placed structure models.
     */
    private final List<String> placed;

    /**
     * Error messages for failed placements.
     */
    private final List<String> errors;

    /**
     * Number of building places that were skipped (no buildingId or not found in index).
     */
    private final int skipped;

    public boolean isSuccess() {
        return errors == null || errors.isEmpty();
    }

    public int getPlacedCount() {
        return placed != null ? placed.size() : 0;
    }
}
