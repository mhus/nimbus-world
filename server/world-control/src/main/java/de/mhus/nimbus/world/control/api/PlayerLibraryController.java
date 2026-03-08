package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for player library.
 * Lists all WProgress entries with type 'library' for the current player.
 * Accessible by players under /control/player/library.
 */
@RestController
@RequestMapping("/control/player/library")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Library", description = "Player library management")
public class PlayerLibraryController extends BaseEditorController {

    private final WProgressService progressService;

    @GetMapping
    @Operation(summary = "Get library entries for current player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Library entries found"),
            @ApiResponse(responseCode = "400", description = "Not authenticated")
    })
    public ResponseEntity<?> getLibrary(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);

        log.debug("GET library: worldId={}, userId={}", worldId, userId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        List<WProgress> entries = progressService.findByWorldIdAndPlayerIdAndType(worldId, userId, "library");

        var items = entries.stream()
                .sorted(Comparator.comparing(
                        p -> p.getTitle() != null ? p.getTitle() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .map(p -> Map.of(
                        "progressId", p.getProgressId() != null ? p.getProgressId() : "",
                        "title", p.getTitle() != null ? p.getTitle() : "",
                        "document", p.getProgressData() != null && p.getProgressData().containsKey("document")
                                ? String.valueOf(p.getProgressData().get("document")) : "",
                        "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                ))
                .toList();

        return ResponseEntity.ok(Map.of("items", items));
    }
}
