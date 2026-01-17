package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentMetadata;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Document CRUD operations.
 * Base path: /control/worlds/{worldId}/documents
 * <p>
 * Documents are text content items that can be organized in collections.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document management")
public class WDocumentController extends BaseEditorController {

    private final WDocumentService documentService;

    // DTOs
    public record DocumentDto(
            String documentId,
            String name,
            String title,
            String collection,
            String language,
            String format,
            String content,
            String summary,
            Map<String, String> metadata,
            String parentDocumentId,
            boolean isMain,
            boolean readOnly,
            String hash,
            String type,
            String childType,
            String worldId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DocumentMetadataDto(
            String documentId,
            String name,
            String title,
            String collection,
            String language,
            String format,
            String summary,
            Map<String, String> metadata,
            String parentDocumentId,
            boolean isMain,
            boolean readOnly,
            String hash,
            String type,
            String childType,
            String worldId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateDocumentRequest(
            String name,
            String title,
            String collection,
            String language,
            String format,
            String content,
            String summary,
            Map<String, String> metadata,
            String parentDocumentId,
            Boolean isMain,
            String type,
            String childType
    ) {
    }

    public record UpdateDocumentRequest(
            String name,
            String title,
            String language,
            String format,
            String content,
            String summary,
            Map<String, String> metadata,
            String parentDocumentId,
            Boolean isMain,
            String hash,
            String type,
            String childType
    ) {
    }

    /**
     * Get single Document by ID.
     * GET /control/worlds/{worldId}/documents/{collection}/{documentId}
     */
    @GetMapping("/{collection}/{documentId}")
    @Operation(summary = "Get Document by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Collection title") @PathVariable String collection,
            @Parameter(description = "Document identifier") @PathVariable String documentId) {

        log.debug("GET document: worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(collection, "collection");
        if (validation != null) return validation;
        validation = validateId(documentId, "documentId");
        if (validation != null) return validation;

        Optional<WDocument> opt = documentService.findByDocumentId(wid, collection, documentId);
        if (opt.isEmpty()) {
            log.warn("Document not found: collection={}, documentId={}", collection, documentId);
            return notFound("document not found");
        }

        log.debug("Returning document: documentId={}", documentId);
        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * List all Documents for a world and collection.
     * GET /control/worlds/{worldId}/documents?collection=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all Documents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Collection title") @RequestParam(required = false) String collection,
            @Parameter(description = "Document type") @RequestParam(required = false) String type,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST documents: worldId={}, collection={}, type={}, offset={}, limit={}",
                worldId, collection, type, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        log.debug("Parsed WorldId: wid='{}', wid.getId()='{}'", wid, wid.getId());

        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Get documents metadata based on filters (without content for performance)
        List<WDocumentMetadata> all;
        if (!Strings.isBlank(collection) && !Strings.isBlank(type)) {
            log.debug("Query: findMetadataByCollection + filter type");
            all = documentService.findMetadataByCollection(wid, collection).stream()
                    .filter(d -> type.equals(d.getType()))
                    .collect(Collectors.toList());
        } else if (!Strings.isBlank(collection)) {
            log.debug("Query: findMetadataByCollection(wid='{}', collection='{}')", wid.getId(), collection);
            all = documentService.findMetadataByCollection(wid, collection);
        } else if (!Strings.isBlank(type)) {
            log.debug("Query: findMetadataByType(wid='{}', type='{}')", wid.getId(), type);
            all = documentService.findMetadataByType(wid, type);
        } else {
            log.debug("Query: findMetadataByWorldId(wid='{}')", wid.getId());
            all = documentService.findMetadataByWorldId(wid);
            log.debug("findMetadataByWorldId returned {} documents", all.size());
        }

        int totalCount = all.size();

        // Apply pagination
        List<DocumentMetadataDto> dtoList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toMetadataDto)
                .collect(Collectors.toList());

        log.debug("Returning {} document metadata (total: {}) for worldId='{}', collection='{}'",
                dtoList.size(), totalCount, worldId, collection);

        return ResponseEntity.ok(Map.of(
                "documents", dtoList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Lookup documents metadata from multiple sources (world, region collection, shared collection).
     * Returns metadata without content for performance.
     * GET /control/worlds/{worldId}/documents/lookup/{collection}
     */
    @GetMapping("/lookup/{collection}")
    @Operation(summary = "Lookup documents metadata from multiple sources")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> lookup(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Collection title") @PathVariable String collection) {

        log.debug("LOOKUP documents metadata: worldId={}, collection={}", worldId, collection);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(collection, "collection");
        if (validation != null) return validation;

        List<WDocumentMetadata> documents = documentService.lookupDocumentsMetadata(wid, collection);
        List<DocumentMetadataDto> dtoList = documents.stream()
                .map(this::toMetadataDto)
                .collect(Collectors.toList());

        log.debug("Returning {} document metadata from lookup", dtoList.size());

        return ResponseEntity.ok(Map.of(
                "documents", dtoList,
                "count", dtoList.size()
        ));
    }

    /**
     * Create new Document.
     * POST /control/worlds/{worldId}/documents
     */
    @PostMapping
    @Operation(summary = "Create new Document")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Document already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateDocumentRequest request) {

        log.debug("CREATE document: worldId={}, collection={}", worldId, request.collection());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (Strings.isBlank(request.collection())) {
            return bad("collection required");
        }

        try {
            // Generate new document ID
            String documentId = UUID.randomUUID().toString();

            WDocument saved = documentService.save(wid, request.collection(), documentId, doc -> {
                if (request.name() != null) doc.setName(request.name());
                if (request.title() != null) doc.setTitle(request.title());
                if (request.language() != null) doc.setLanguage(request.language());
                if (request.format() != null) doc.setFormat(request.format());
                if (request.content() != null) doc.setContent(request.content());
                if (request.summary() != null) doc.setSummary(request.summary());
                if (request.metadata() != null) doc.setMetadata(request.metadata());
                if (request.parentDocumentId() != null) doc.setParentDocumentId(request.parentDocumentId());
                if (request.isMain() != null) doc.setMain(request.isMain());
                if (request.type() != null) doc.setType(request.type());
                if (request.childType() != null) doc.setChildType(request.childType());
            });

            log.info("Created document: collection={}, documentId={}", request.collection(), documentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "documentId", saved.getDocumentId(),
                    "message", "Document created successfully"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating document: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing Document.
     * PUT /control/worlds/{worldId}/documents/{collection}/{documentId}
     */
    @PutMapping("/{collection}/{documentId}")
    @Operation(summary = "Update Document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Document is read-only"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Collection title") @PathVariable String collection,
            @Parameter(description = "Document identifier") @PathVariable String documentId,
            @RequestBody UpdateDocumentRequest request) {

        log.debug("UPDATE document: worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(collection, "collection");
        if (validation != null) return validation;
        validation = validateId(documentId, "documentId");
        if (validation != null) return validation;

        // Check if document is read-only
        Optional<WDocument> existingDoc = documentService.findByDocumentId(wid, collection, documentId);
        if (existingDoc.isEmpty()) {
            log.warn("Document not found for update: documentId={}", documentId);
            return notFound("document not found");
        }
        if (existingDoc.get().isReadOnly()) {
            log.warn("Attempted to update read-only document: documentId={}", documentId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot update read-only document"));
        }

        Optional<WDocument> updated = documentService.update(wid, collection, documentId, doc -> {
            if (request.name() != null) doc.setName(request.name());
            if (request.title() != null) doc.setTitle(request.title());
            if (request.language() != null) doc.setLanguage(request.language());
            if (request.format() != null) doc.setFormat(request.format());
            if (request.content() != null) doc.setContent(request.content());
            if (request.summary() != null) doc.setSummary(request.summary());
            if (request.metadata() != null) doc.setMetadata(request.metadata());
            if (request.parentDocumentId() != null) doc.setParentDocumentId(request.parentDocumentId());
            if (request.isMain() != null) doc.setMain(request.isMain());
            if (request.hash() != null) doc.setHash(request.hash());
            if (request.type() != null) doc.setType(request.type());
            if (request.childType() != null) doc.setChildType(request.childType());
        });

        if (updated.isEmpty()) {
            log.warn("Document not found for update: documentId={}", documentId);
            return notFound("document not found");
        }

        log.info("Updated document: documentId={}", documentId);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete Document.
     * DELETE /control/worlds/{worldId}/documents/{collection}/{documentId}
     */
    @DeleteMapping("/{collection}/{documentId}")
    @Operation(summary = "Delete Document")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "403", description = "Document is read-only"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Collection title") @PathVariable String collection,
            @Parameter(description = "Document identifier") @PathVariable String documentId) {

        log.debug("DELETE document: worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(collection, "collection");
        if (validation != null) return validation;
        validation = validateId(documentId, "documentId");
        if (validation != null) return validation;

        // Check if document is read-only
        Optional<WDocument> existingDoc = documentService.findByDocumentId(wid, collection, documentId);
        if (existingDoc.isEmpty()) {
            log.warn("Document not found for deletion: documentId={}", documentId);
            return notFound("document not found");
        }
        if (existingDoc.get().isReadOnly()) {
            log.warn("Attempted to delete read-only document: documentId={}", documentId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot delete read-only document"));
        }

        boolean deleted = documentService.delete(wid, collection, documentId);
        if (!deleted) {
            log.warn("Document not found for deletion: documentId={}", documentId);
            return notFound("document not found");
        }

        log.info("Deleted document: documentId={}", documentId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private DocumentDto toDto(WDocument entity) {
        return new DocumentDto(
                entity.getDocumentId(),
                entity.getName(),
                entity.getTitle(),
                entity.getCollection(),
                entity.getLanguage(),
                entity.getFormat(),
                entity.getContent(),
                entity.getSummary(),
                entity.getMetadata(),
                entity.getParentDocumentId(),
                entity.isMain(),
                entity.isReadOnly(),
                entity.getHash(),
                entity.getType(),
                entity.getChildType(),
                entity.getWorldId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DocumentMetadataDto toMetadataDto(WDocumentMetadata entity) {
        return new DocumentMetadataDto(
                entity.getDocumentId(),
                entity.getName(),
                entity.getTitle(),
                entity.getCollection(),
                entity.getLanguage(),
                entity.getFormat(),
                entity.getSummary(),
                entity.getMetadata(),
                entity.getParentDocumentId(),
                entity.isMain(),
                entity.isReadOnly(),
                entity.getHash(),
                entity.getType(),
                entity.getChildType(),
                entity.getWorldId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
