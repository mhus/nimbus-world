package de.mhus.nimbus.world.generator.translator;

import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of translating textual instructions to HexComposition object.
 */
@Data
@Builder
public class CompositionResult {

    /**
     * The generated HexComposition object.
     * Null if translation or parsing failed.
     */
    private HexComposition composition;

    /**
     * The intermediate JSON string (for debugging).
     * Null if translation failed.
     */
    private String composerModelJson;

    /**
     * List of errors encountered during translation or parsing.
     * Empty if translation and parsing were successful.
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Check if the translation was successful (no errors).
     *
     * @return true if no errors occurred
     */
    public boolean isSuccessful() {
        return errors.isEmpty() && composition != null;
    }

    /**
     * Check if the translation failed (has errors).
     *
     * @return true if errors occurred
     */
    public boolean hasFailed() {
        return !errors.isEmpty() || composition == null;
    }

    /**
     * Add an error to the result.
     *
     * @param error Error message
     */
    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }

    /**
     * Create a successful result with HexComposition.
     *
     * @param composition HexComposition object
     * @param json Intermediate JSON string
     * @return Successful composition result
     */
    public static CompositionResult success(HexComposition composition, String json) {
        return CompositionResult.builder()
                .composition(composition)
                .composerModelJson(json)
                .errors(new ArrayList<>())
                .build();
    }

    /**
     * Create a failed result with errors.
     *
     * @param errors List of error messages
     * @return Failed composition result
     */
    public static CompositionResult failure(List<String> errors) {
        return CompositionResult.builder()
                .errors(errors)
                .build();
    }

    /**
     * Create a failed result with a single error.
     *
     * @param error Error message
     * @return Failed composition result
     */
    public static CompositionResult failure(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return CompositionResult.builder()
                .errors(errors)
                .build();
    }

    /**
     * Create a failed result with a single error and intermediate JSON.
     *
     * @param error Error message
     * @param json Intermediate JSON that failed to parse
     * @return Failed composition result
     */
    public static CompositionResult failure(String error, String json) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return CompositionResult.builder()
                .composerModelJson(json)
                .errors(errors)
                .build();
    }

    /**
     * Create a failed result with errors and intermediate JSON.
     *
     * @param errors List of error messages
     * @param json Intermediate JSON that failed to parse
     * @return Failed composition result
     */
    public static CompositionResult failure(List<String> errors, String json) {
        return CompositionResult.builder()
                .composerModelJson(json)
                .errors(errors)
                .build();
    }
}
