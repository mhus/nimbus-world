package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler for showing a document to a player.
 *
 * Server parameters:
 * - document: document reference, either "collection/name" or "documentId" (no "/" in value)
 *
 * Flow:
 * 1. Load WDocument and verify it exists
 * 2. Create WProgress with the document reference in progressData
 * 3. Send openComponent command to client with the progressId
 */
@Slf4j
public class ShowDocumentAction extends AbstractGamplayAction {

    public ShowDocumentAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String documentRef = serverParameters.get("document");
        if (Strings.isBlank(documentRef)) {
            log.warn("show.document action missing 'document' parameter");
            return false;
        }

        // Optional collection parameter overrides the progress type (default: "document")
        String collection = serverParameters.getOrDefault("collection", "document");

        WorldId worldId = session.getWorldId();
        WorldId docWorldId = worldId.isInstance() ? worldId.toMainWorld() : worldId;

        // Resolve and verify document exists
        Optional<WDocument> docOpt;
        if (documentRef.contains("/")) {
            String[] parts = documentRef.split("/", 2);
            docOpt = basic.getDocumentService().findByName(docWorldId, parts[0], parts[1]);
        } else {
            docOpt = basic.getDocumentService().findByDocumentId(docWorldId, documentRef);
        }

        if (docOpt.isEmpty()) {
            log.warn("Document not found: {} in world {}", documentRef, docWorldId);
            basic.getBasicClientService().sendNotification(session, 0, "", "Document not found", null);
            return false;
        }

        WDocument doc = docOpt.get();

        // Acquire lease for document access
        String playerId = session.getEntityId();
        var lease = basic.getLeaseService().acquire(
                worldId.getId(),
                playerId,
                collection,
                documentRef,
                doc.getTitle(),
                Map.of("document", documentRef)
        );

        // Send openComponent command to client
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("document", lease.getLeaseId()));

        log.debug("Sent show.document to player {}: document={}, collection={}, leaseId={}",
                playerId, documentRef, collection, lease.getLeaseId());
        return true;
    }
}
