package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.control.dialog.DialogDtos.DialogNodeResponse;
import de.mhus.nimbus.world.control.dialog.DialogDtos.DialogRequest;
import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogFreeTextService;
import de.mhus.nimbus.world.control.dialog.DialogService;
import de.mhus.nimbus.world.control.dialog.DialogService.DialogException;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for player dialog interaction.
 * Loads dialog nodes with evaluated conditions, handles option selection and free text.
 * Accessible by players under /control/player/dialog.
 */
@RestController
@RequestMapping("/control/player/dialog")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Dialog", description = "Player NPC dialog interaction")
public class PlayerDialogController extends BaseEditorController {

    private final DialogService dialogService;
    private final DialogFreeTextService freeTextService;

    @GetMapping
    @Operation(summary = "Get current dialog node with evaluated options")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dialog node returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<?> getDialog(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (Strings.isBlank(progressId)) {
            return bad("progressId required");
        }

        try {
            DialogContext ctx = dialogService.loadDialogContext(progressId, worldId, userId, characterId);

            if (dialogService.isContinuing(ctx)) {
                // Restore existing situation and node
                dialogService.restoreSituation(ctx);
                String nodeId = dialogService.getCurrentNodeId(ctx);
                DialogNodeResponse response = dialogService.evaluateNode(ctx, nodeId);
                return ResponseEntity.ok(response);
            } else {
                // New dialog: select situation, start at greeting
                dialogService.selectSituation(ctx);
                DialogNodeResponse response = dialogService.evaluateNode(ctx, "greeting");
                return ResponseEntity.ok(response);
            }
        } catch (DialogException e) {
            log.warn("Dialog error: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected dialog error", e);
            return bad("Internal error");
        }
    }

    @PostMapping
    @Operation(summary = "Select a dialog option or send free text")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Next dialog node returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable for free text")
    })
    public ResponseEntity<?> postDialog(
            @RequestBody DialogRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId())) {
            return bad("progressId required");
        }

        try {
            DialogContext ctx = dialogService.loadDialogContext(body.progressId(), worldId, userId, characterId);

            // Restore situation (POST always continues existing dialog)
            dialogService.restoreSituation(ctx);

            if (body.freeText() != null) {
                String currentNodeId = dialogService.getCurrentNodeId(ctx);
                try {
                    DialogNodeResponse response = freeTextService.processInput(ctx, body.freeText(), currentNodeId);
                    return ResponseEntity.ok(response);
                } catch (DialogException e) {
                    if ("AI not available".equals(e.getMessage())) {
                        return ResponseEntity.status(503).body(
                                java.util.Map.of("error", "AI service unavailable"));
                    }
                    throw e;
                }
            }

            if (body.optionIndex() == null) {
                return bad("Either optionIndex or freeText required");
            }

            DialogNodeResponse response = dialogService.advanceDialog(ctx, body.optionIndex());
            return ResponseEntity.ok(response);

        } catch (DialogException e) {
            log.warn("Dialog error: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected dialog error", e);
            return bad("Internal error");
        }
    }

    @PostMapping("/close")
    @Operation(summary = "Close dialog and resume NPC movement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dialog closed"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> closeDialog(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        try {
            DialogContext ctx = dialogService.loadDialogContext(progressId, worldId, userId,
                    characterId != null ? characterId : "");
            dialogService.closeDialog(ctx);
            return ResponseEntity.ok(java.util.Map.of("closed", true));
        } catch (DialogException e) {
            log.warn("Close dialog error: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected close dialog error", e);
            return bad("Internal error");
        }
    }
}
