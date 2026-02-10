package de.mhus.nimbus.world.generator.composer.structure;

import de.mhus.nimbus.world.generator.composer.town.PlacedStructure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of structure placement operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructurePlacementResult {

    /**
     * List of successfully placed structures
     */
    @Builder.Default
    private List<PlacedStructure> placedStructures = new ArrayList<>();

    /**
     * Total number of structures attempted
     */
    private int totalStructures;

    /**
     * Number of successfully placed structures
     */
    private int placedCount;

    /**
     * Number of failed placements
     */
    private int failedCount;

    /**
     * List of error messages
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Whether the composition was successful overall
     */
    private boolean success;

    /**
     * Error message if composition failed
     */
    private String errorMessage;
}
