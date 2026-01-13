package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.Builder;
import lombok.Data;

/**
 * Result of a block manipulation operation.
 * Contains model selector with generated blocks, success status, and message for chat.
 */
@Data
@Builder
public class ManipulatorResult {

    /**
     * Model selector containing the generated/manipulated blocks.
     * This represents the selection that was created or modified by the manipulator.
     */
    private ModelSelector modelSelector;

    /**
     * Whether the manipulation was successful.
     */
    @Builder.Default
    private boolean success = true;

    /**
     * Result message for chat display.
     * Should describe what was done, how many blocks were affected, etc.
     * This message is shown to the user in the chat interface.
     */
    private String message;

    /**
     * Create a success result with message.
     *
     * @param message success message
     * @return success result
     */
    public static ManipulatorResult success(String message) {
        return ManipulatorResult.builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * Create a success result with message and model selector.
     *
     * @param message success message
     * @param modelSelector model selector with generated blocks
     * @return success result
     */
    public static ManipulatorResult success(String message, ModelSelector modelSelector) {
        return ManipulatorResult.builder()
                .success(true)
                .message(message)
                .modelSelector(modelSelector)
                .build();
    }

    /**
     * Create an error result.
     *
     * @param message error message
     * @return error result
     */
    public static ManipulatorResult error(String message) {
        return ManipulatorResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
