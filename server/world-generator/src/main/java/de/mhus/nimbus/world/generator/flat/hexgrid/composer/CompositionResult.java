package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a composition build operation.
 * Used by BuildFeature implementations to return their build results.
 */
@Data
@Builder
public class CompositionResult {
    private boolean success;
    private String errorMessage;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // Step results
    private BiomePlacementResult biomePlacementResult;
    private HexGridFillResult fillResult;
    private FlowComposer.FlowCompositionResult flowCompositionResult;
    private HexGridGenerator.GenerationResult generationResult;

    // Summary statistics
    private int totalBiomes;
    private int totalStructures;
    private int totalFlows;
    private int totalGrids;
    private int filledGrids;
    private int generatedWHexGrids;

    /**
     * Returns all FilledHexGrids from the fill result (if filling was enabled)
     */
    public List<FilledHexGrid> getAllGrids() {
        if (fillResult != null) {
            return fillResult.getAllGrids();
        }
        return new ArrayList<>();
    }

    /**
     * Returns all WHexGrids from placement result
     */
    public List<de.mhus.nimbus.world.shared.world.WHexGrid> getWHexGrids() {
        if (biomePlacementResult != null) {
            return biomePlacementResult.getHexGrids();
        }
        return new ArrayList<>();
    }

    /**
     * Creates a failed result with error message
     */
    public static CompositionResult failed(String errorMessage) {
        return CompositionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Creates a successful result
     */
    public static CompositionResult successful() {
        return CompositionResult.builder()
            .success(true)
            .build();
    }
}
