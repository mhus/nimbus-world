package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for player dialog viewing.
 * Loads a dialog playbook (WAnything) referenced by a WProgress entry.
 * Accessible by players under /control/player/dialog.
 */
@RestController
@RequestMapping("/control/player/dialog")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Dialog", description = "Player dialog viewing")
public class PlayerDialogController extends BaseEditorController {

    private final WProgressService progressService;
    private final WAnythingService anythingService;

    @GetMapping
    @Operation(summary = "Get dialog data referenced by a progress entry")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dialog data found"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<?> getDialog(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);

        log.debug("GET dialog: progressId={}, worldId={}, userId={}", progressId, worldId, userId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }
        if (Strings.isBlank(progressId)) {
            return bad("progressId required");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        // Load progress and verify ownership
        Optional<WProgress> progressOpt = progressService.findByProgressId(progressId);
        if (progressOpt.isEmpty()) {
            return notFound("Progress not found");
        }

        WProgress progress = progressOpt.get();
        if (!worldId.equals(progress.getWorldId())) {
            return bad("Progress does not belong to this world");
        }
        if (!userId.equals(progress.getPlayerId())) {
            return bad("Progress does not belong to this player");
        }

        // Extract playbook reference from progressData
        Map<String, Object> progressData = progress.getProgressData();
        if (progressData == null || !progressData.containsKey("playbook")) {
            return bad("Progress has no playbook reference");
        }

        String playbookRef = String.valueOf(progressData.get("playbook"));
        if (Strings.isBlank(playbookRef) || !playbookRef.contains("/")) {
            return bad("Invalid playbook reference");
        }

        // Resolve playbook from WAnything
        String[] parts = playbookRef.split("/", 2);
        String collection = parts[0];
        String name = parts[1];

        WorldId anythingWorldId = parsedWorldId.isInstance() ? parsedWorldId.toMainWorld() : parsedWorldId;

        Optional<WAnything> playbookOpt = anythingService.findByWorldIdAndCollectionAndName(
                anythingWorldId.getId(), collection, name);

        if (playbookOpt.isEmpty()) {
            return notFound("Playbook not found: " + playbookRef);
        }

        WAnything playbook = playbookOpt.get();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playbook", playbookRef);
        result.put("progressId", progressId);
        result.put("data", playbook.getData());

        return ResponseEntity.ok(result);
    }
}
