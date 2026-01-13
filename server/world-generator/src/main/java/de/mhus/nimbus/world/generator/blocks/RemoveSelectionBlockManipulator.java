package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;

/**
 * RemoveSelectionBlockManipulator - Removes the current ModelSelector from WSession.
 *
 * This manipulator:
 * - Clears the ModelSelector in WSession (Redis)
 * - Sends an empty ModelSelector to clients via Redis
 * - Returns no ModelSelector in the result
 *
 * Parameters: None required
 *
 * Example:
 * <pre>
 * {
 *   "remove-selection": {}
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemoveSelectionBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;

    @Override
    public String getName() {
        return "remove-selection";
    }

    @Override
    public String getTitle() {
        return "Remove Selection";
    }

    @Override
    public String getDescription() {
        return "Removes the current ModelSelector from WSession. " +
                "Example: {\"remove-selection\": {}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        String sessionId = context.getSessionId();

        if (sessionId == null || sessionId.isBlank()) {
            throw new BlockManipulatorException("SessionId is required to remove selection");
        }

        // Clear ModelSelector in WSession (Redis)
        Optional<WSession> updatedSession = wSessionService.updateModelSelector(sessionId, new ArrayList<>());

        if (updatedSession.isEmpty()) {
            throw new BlockManipulatorException("Session not found: " + sessionId);
        }

        log.debug("Removed selection for session: {}", sessionId);

        // Return result with empty ModelSelector (sends clear command to client)
        ModelSelector emptySelector = ModelSelector.builder()
                .defaultColor("#00ff00")
                .autoSelectName("cleared")
                .build();

        return ManipulatorResult.builder()
                .success(true)
                .message("Selection removed successfully")
                .modelSelector(emptySelector)
                .build();
    }
}
