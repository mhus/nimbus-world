package de.mhus.nimbus.world.generator.translator;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of translating textual instructions to Composer Model JSON.
 */
@Data
@Builder
public class TranslationResult {

    /**
     * The generated Composer Model JSON string.
     * Null if translation failed with errors.
     */
    private String composerModelJson;

    /**
     * List of errors encountered during translation.
     * Empty if translation was successful.
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Check if the translation was successful (no errors).
     *
     * @return true if no errors occurred
     */
    public boolean isSuccessful() {
        return errors.isEmpty();
    }

    /**
     * Check if the translation failed (has errors).
     *
     * @return true if errors occurred
     */
    public boolean hasFailed() {
        return !errors.isEmpty();
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
     * Create a successful result with JSON.
     *
     * @param json Composer Model JSON
     * @return Successful translation result
     */
    public static TranslationResult success(String json) {
        return TranslationResult.builder()
                .composerModelJson(json)
                .errors(new ArrayList<>())
                .build();
    }

    /**
     * Create a failed result with errors.
     *
     * @param errors List of error messages
     * @return Failed translation result
     */
    public static TranslationResult failure(List<String> errors) {
        return TranslationResult.builder()
                .errors(errors)
                .build();
    }

    /**
     * Create a failed result with a single error.
     *
     * @param error Error message
     * @return Failed translation result
     */
    public static TranslationResult failure(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return TranslationResult.builder()
                .errors(errors)
                .build();
    }
}
