package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WLeaseService;
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

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for player document viewing.
 * Loads a document referenced by a WProgress entry.
 * Accessible by players under /control/player/document.
 */
@RestController
@RequestMapping("/control/player/document")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Document", description = "Player document viewing")
public class PlayerDocumentController extends BaseEditorController {

    private final WLeaseService leaseService;
    private final WDocumentService documentService;

    @GetMapping
    @Operation(summary = "Get document referenced by a progress entry")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<?> getDocument(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);

        log.debug("GET document: progressId={}, worldId={}, userId={}", progressId, worldId, userId);

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

        // Load and validate lease
        var leaseOpt = leaseService.validate(progressId, worldId, userId, null);
        if (leaseOpt.isEmpty()) {
            return notFound("Lease not found or access denied");
        }

        var lease = leaseOpt.get();

        // Extract document reference from leaseData
        Map<String, Object> leaseData = lease.getLeaseData();
        if (leaseData == null || !leaseData.containsKey("document")) {
            return bad("Lease has no document reference");
        }

        String documentRef = String.valueOf(leaseData.get("document"));
        if (Strings.isBlank(documentRef)) {
            return bad("Empty document reference");
        }

        // Resolve document: collection/name or documentId
        WorldId docWorldId = parsedWorldId.toMainWorld();
        if (docWorldId.isInstance()) {
            return bad("Cannot resolve document worldId");
        }

        Optional<WDocument> docOpt;
        if (documentRef.contains("/")) {
            String[] parts = documentRef.split("/", 2);
            String collection = parts[0];
            String name = parts[1];
            docOpt = documentService.findByName(docWorldId, collection, name);
        } else {
            docOpt = documentService.findByDocumentId(docWorldId, documentRef);
        }

        if (docOpt.isEmpty()) {
            return notFound("Document not found: " + documentRef);
        }

        WDocument doc = docOpt.get();

        return ResponseEntity.ok(Map.of(
                "title", doc.getTitle() != null ? doc.getTitle() : "",
                "content", doc.getContent() != null ? doc.getContent() : "",
                "format", doc.getFormat() != null ? doc.getFormat() : "plaintext"
        ));
    }
}
