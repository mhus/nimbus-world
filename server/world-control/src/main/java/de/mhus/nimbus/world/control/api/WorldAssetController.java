package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.SAssetService;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.AssetMetadata;
import de.mhus.nimbus.world.shared.world.FolderInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Asset list operations at /control/worlds/{worldId}/assets
 * This provides the test_server compatible asset list endpoint.
 * For full CRUD operations, use EAssetController at /editor/user/asset
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/assets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WorldAssets", description = "Asset listing for world editor")
public class WorldAssetController extends BaseEditorController {

    private final SAssetService assetService;

    // DTOs
    public record AssetListItemDto(
            String path,
            long size,
            String mimeType,
            Instant lastModified,
            String extension,
            String category
    ) {
    }

    /**
     * List/search assets for a world with pagination and extension filter.
     * GET /control/worlds/{worldId}/assets?query=...&ext=png,jpg&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List assets for world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "World not found")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Extension filter (comma-separated, e.g., 'png,jpg')") @RequestParam(required = false) String ext,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST assets: worldId={}, query={}, ext={}, offset={}, limit={}", worldId, query, ext, offset, limit);

        // Get assets using database-level filtering and pagination
        WorldId wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("invalid worldId")
        );

        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Use new search method with database-level filtering
        SAssetService.AssetSearchResult searchResult = assetService.searchAssets(wid, query, ext, offset, limit);

        // Convert to DTOs
        List<AssetListItemDto> dtos = searchResult.assets().stream()
                .map(this::toListDto)
                .collect(Collectors.toList());

        int totalCount = searchResult.totalCount();
        log.debug("Returning {} assets (total: {})", dtos.size(), totalCount);

        // TypeScript compatible format (match test_server response)
        return ResponseEntity.ok(Map.of(
                "assets", dtos,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Get/serve asset file content.
     * GET /control/worlds/{worldId}/assets/{*path}
     * Example: GET /control/worlds/main/assets/textures/block/stone.png
     *
     * For metadata (.info), use WorldAssetInfoController
     */
    @GetMapping("/{*path}")
    @Operation(summary = "Get asset file content")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asset found"),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<?> getAssetFile(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("GET asset file: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        // Find asset (regionId=worldId, worldId=worldId)
        Optional<SAsset> opt = assetService.findByPath(wid, path);
        if (opt.isEmpty()) {
            log.warn("Asset not found: worldId={}, path={}", worldId, path);
            return notFound("asset not found");
        }

        SAsset asset = opt.get();

        // Serve binary content
        if (!asset.isEnabled()) {
            log.warn("Asset disabled: path={}", path);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "asset disabled"));
        }

        // Load content as stream - no memory loading!
        InputStream contentStream = assetService.loadContent(asset);
        if (contentStream == null) {
            log.warn("Asset has no content: {}", path);
            return ResponseEntity.notFound().build();
        }

        // Determine content type
        String mimeType = determineMimeType(path);

        log.debug("Streaming asset: path={}, size={}, mimeType={}", path, asset.getSize(), mimeType);

        // Return InputStreamResource for streaming
        org.springframework.core.io.InputStreamResource resource = new org.springframework.core.io.InputStreamResource(contentStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(asset.getSize())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    /**
     * Create new asset.
     * POST /control/worlds/{worldId}/assets/{*path}
     */
    @PostMapping("/{*path}")
    @Operation(summary = "Create new asset")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asset created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Asset already exists")
    })
    public ResponseEntity<?> createAsset(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path,
            InputStream contentStream) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("CREATE asset: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        // Check if asset already exists
        if (assetService.findByPath(wid, path).isPresent()) {
            return conflict("asset already exists");
        }

        try {
            AssetMetadata metadata = createMetadataFromPath(path);
            SAsset saved = assetService.saveAsset(wid, path, contentStream, "editor", metadata);
            log.info("Created asset: path={}, size={}", path, saved.getSize());
            return ResponseEntity.status(HttpStatus.CREATED).body(toListDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating asset: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating asset", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update asset content.
     * PUT /control/worlds/{worldId}/assets/textures/block/stone.png - Update/create binary content
     *
     * For metadata updates (.info), use WorldAssetInfoController
     */
    @PutMapping("/{*path}")
    @Operation(summary = "Update asset content")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asset updated"),
            @ApiResponse(responseCode = "201", description = "Asset created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> updateAsset(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path,
            InputStream contentStream) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("UPDATE asset content: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        try {
            Optional<SAsset> existing = assetService.findByPath(wid, path);

            // Update/create binary content
            if (existing.isPresent()) {
                // Update existing
                SAsset updated = assetService.updateContent(existing.get(), contentStream);
                if (updated != null) {
                    log.info("Updated asset: path={}, size={}", path, updated.getSize());
                    return ResponseEntity.ok(toListDto(updated));
                } else {
                    return notFound("asset disappeared during update");
                }
            } else {
                // Create new
                AssetMetadata metadata = createMetadataFromPath(path);
                SAsset saved = assetService.saveAsset(wid, path, contentStream, "editor", metadata);
                log.info("Created asset via PUT: path={}, size={}", path, saved.getSize());
                return ResponseEntity.status(HttpStatus.CREATED).body(toListDto(saved));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Validation error updating asset: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error updating asset", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Delete asset.
     * DELETE /control/worlds/{worldId}/assets/{*path}
     */
    @DeleteMapping("/{*path}")
    @Operation(summary = "Delete asset")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Asset deleted"),
            @ApiResponse(responseCode = "404", description = "Asset not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> deleteAsset(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("DELETE asset: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        Optional<SAsset> existing = assetService.findByPath(wid, path);
        if (existing.isEmpty()) {
            log.warn("Asset not found for deletion: path={}", path);
            return notFound("asset not found");
        }

        assetService.delete(existing.get());
        log.info("Deleted asset: path={}", path);
        return ResponseEntity.noContent().build();
    }


    /**
     * Duplicate Asset with a new path (same world).
     * PATCH /control/worlds/{worldId}/assets/duplicate
     * Body: { "sourcePath": "...", "newPath": "..." }
     */
    @PatchMapping("/duplicate")
    @Operation(summary = "Asset duplizieren",
            description = "Erstellt eine Kopie eines Assets mit einem neuen Pfad")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asset erfolgreich dupliziert"),
            @ApiResponse(responseCode = "400", description = "Ungültige Parameter"),
            @ApiResponse(responseCode = "404", description = "Quell-Asset nicht gefunden"),
            @ApiResponse(responseCode = "409", description = "Ziel-Asset existiert bereits")
    })
    public ResponseEntity<?> duplicate(
                                       @Parameter(description = "World identifier") @PathVariable String worldId,
                                       @RequestBody Map<String, String> body) {
        String sourcePath = normalizePath(body.get("sourcePath"));
        String newPath = normalizePath(body.get("newPath"));

        log.debug("DUPLICATE asset: worldId={}, sourcePath={}, newPath={}",
                worldId, sourcePath, newPath);

        if (blank(sourcePath)) return bad("sourcePath required");
        if (blank(newPath)) return bad("newPath required");
        if (sourcePath.equals(newPath)) return bad("sourcePath and newPath must be different");

        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid == null) return bad("invalid worldId");

        // Check if source asset exists
        Optional<SAsset> sourceOpt = assetService.findByPath(wid, sourcePath);
        if (sourceOpt.isEmpty()) {
            log.warn("Source asset not found for duplication: path={}", sourcePath);
            return notFound("source asset not found");
        }

        // Check if new path already exists
        if (assetService.findByPath(wid, newPath).isPresent()) {
            return conflict("asset already exists at new path: " + newPath);
        }

        try {
            SAsset source = sourceOpt.get();

            // Duplicate the asset
            SAsset duplicate = assetService.duplicateAsset(source, newPath, "editor");

            log.info("Duplicated asset: sourcePath={}, newPath={}, size={}",
                    sourcePath, newPath, duplicate.getSize());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "path", duplicate.getPath(),
                    "message", "Asset duplicated successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to duplicate asset", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to duplicate asset: " + e.getMessage()));
        }
    }

    /**
     * Duplicate/Copy Asset from source world to target world (cross-world copy).
     * POST /control/worlds/{targetWorldId}/assets/copy-from
     * Body: { "sourceWorldId": "...", "sourcePath": "...", "newPath": "..." }
     */
    @PostMapping("/copy-from")
    @Operation(summary = "Asset zwischen Welten kopieren",
            description = "Kopiert ein Asset von einer Quell-Welt in diese Ziel-Welt. Erhält alle Metadaten (publicData).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asset erfolgreich kopiert"),
            @ApiResponse(responseCode = "400", description = "Ungültige Parameter"),
            @ApiResponse(responseCode = "404", description = "Quell-Asset nicht gefunden"),
            @ApiResponse(responseCode = "409", description = "Ziel-Asset existiert bereits")
    })
    public ResponseEntity<?> copyFromWorld(
            @Parameter(description = "Target world identifier") @PathVariable String worldId,
            @RequestBody Map<String, String> body) {
        String sourceWorldId = body.get("sourceWorldId");
        String sourcePath = normalizePath(body.get("sourcePath"));
        String newPath = normalizePath(body.get("newPath"));

        log.debug("COPY asset cross-world: sourceWorldId={}, sourcePath={}, targetWorldId={}, newPath={}",
                sourceWorldId, sourcePath, worldId, newPath);

        if (blank(sourceWorldId)) return bad("sourceWorldId required");
        if (blank(sourcePath)) return bad("sourcePath required");
        if (blank(newPath)) return bad("newPath required");

        WorldId sourceWid = WorldId.of(sourceWorldId).orElse(null);
        WorldId targetWid = WorldId.of(worldId).orElse(null);
        if (sourceWid == null) return bad("invalid sourceWorldId");
        if (targetWid == null) return bad("invalid targetWorldId");

        // Check if source asset exists
        Optional<SAsset> sourceOpt = assetService.findByPath(sourceWid, sourcePath);
        if (sourceOpt.isEmpty()) {
            log.warn("Source asset not found for cross-world copy: sourceWorldId={}, path={}", sourceWorldId, sourcePath);
            return notFound("source asset not found");
        }

        // Check if target path already exists
        if (assetService.findByPath(targetWid, newPath).isPresent()) {
            return conflict("asset already exists at target path: " + newPath);
        }

        try {
            SAsset source = sourceOpt.get();

            // Copy asset to target world with all metadata
            SAsset copy = assetService.duplicateAssetToWorld(source, targetWid, newPath, "editor");

            log.info("Copied asset cross-world: sourceWorldId={}, sourcePath={}, targetWorldId={}, newPath={}, size={}",
                    sourceWorldId, sourcePath, worldId, newPath, copy.getSize());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "path", copy.getPath(),
                    "worldId", copy.getWorldId(),
                    "message", "Asset copied successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to copy asset cross-world", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to copy asset: " + e.getMessage()));
        }
    }

    /**
     * Get virtual folder tree for a world.
     * GET /control/worlds/{worldId}/assets/folders?parent=textures
     *
     * Returns folders derived from asset paths. Folders don't exist in the database as entities.
     * Example: Asset "textures/block/stone.png" creates virtual folders "textures" and "textures/block".
     */
    @GetMapping("/folders")
    @Operation(
            summary = "Get folder tree for world",
            description = "Returns virtual folders derived from asset paths. Folders are computed from asset paths and don't exist as database entities."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid worldId"),
            @ApiResponse(responseCode = "404", description = "World not found")
    })
    public ResponseEntity<?> getFolders(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Parent path filter (optional) - get only subfolders of this path")
            @RequestParam(required = false) String parent) {

        log.debug("GET folders: worldId={}, parent={}", worldId, parent);

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Extract folders from asset paths
            List<FolderInfo> folders = assetService.extractFolders(wid, parent);

            log.debug("Returning {} folders", folders.size());

            return ResponseEntity.ok(Map.of(
                    "folders", folders,
                    "count", folders.size(),
                    "parent", parent != null ? parent : ""
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get folders for world {}", worldId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get folders: " + e.getMessage()));
        }
    }

    /**
     * Move/rename a folder by updating all asset paths with the given prefix.
     * PATCH /control/worlds/{worldId}/assets/folders/move
     * Body: { "oldPath": "old_textures", "newPath": "textures" }
     *
     * WARNING: This is a dangerous operation that affects all assets in the folder.
     * It validates for conflicts before proceeding.
     */
    @PatchMapping("/folders/move")
    @Operation(
            summary = "Move or rename a folder",
            description = "Updates all asset paths with the old prefix to the new prefix. " +
                    "This affects all assets in the folder and its subfolders. " +
                    "Validates for conflicts before proceeding."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder moved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation error"),
            @ApiResponse(responseCode = "409", description = "Conflict - target paths already exist"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> moveFolder(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Folder move request with oldPath and newPath",
                    required = true
            )
            @RequestBody Map<String, String> body) {

        log.debug("PATCH move folder: worldId={}, body={}", worldId, body);

        try {
            String oldPath = body.get("oldPath");
            String newPath = body.get("newPath");

            // Validation
            if (oldPath == null || oldPath.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "oldPath is required"));
            }

            if (newPath == null || newPath.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "newPath is required"));
            }

            // Normalize paths
            oldPath = normalizePath(oldPath);
            newPath = normalizePath(newPath);

            if (oldPath.equals(newPath)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "oldPath and newPath must be different"));
            }

            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Perform bulk update
            int updatedCount = assetService.updatePathPrefix(wid, oldPath, newPath);

            log.info("Moved folder '{}' to '{}', updated {} assets", oldPath, newPath, updatedCount);

            return ResponseEntity.ok(Map.of(
                    "message", "Folder moved successfully",
                    "oldPath", oldPath,
                    "newPath", newPath,
                    "updatedAssets", updatedCount
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid move folder request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            // Conflict detected
            log.warn("Conflict detected during folder move: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to move folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to move folder: " + e.getMessage()));
        }
    }


    // Helper methods

    private String normalizePath(String path) {
        if (path == null) return null;
        return path.replaceAll("/{2,}", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+\\$", "") // remove trailing slashes
                .replaceAll("/+\\$", ""); // idempotent second pass
    }

    private AssetListItemDto toListDto(SAsset asset) {
        return new AssetListItemDto(
                asset.getPath(),
                asset.getSize(),
                determineMimeType(asset.getPath()),
                asset.getCreatedAt(),
                extractExtension(asset.getPath()),
                extractCategory(asset.getPath())
        );
    }


    private String extractExtension(String path) {
        if (path == null || !path.contains(".")) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        return path.substring(lastDot);
    }

    private String extractCategory(String path) {
        if (path == null || !path.contains("/")) {
            return "other";
        }
        int firstSlash = path.indexOf('/');
        return path.substring(0, firstSlash);
    }

    private String determineMimeType(String path) {
        String ext = extractExtension(path).toLowerCase();
        return switch (ext) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".svg" -> "image/svg+xml";
            case ".json" -> "application/json";
            case ".glb" -> "model/gltf-binary";
            case ".gltf" -> "model/gltf+json";
            case ".obj" -> "model/obj";
            case ".ogg" -> "audio/ogg";
            case ".mp3" -> "audio/mpeg";
            case ".wav" -> "audio/wav";
            default -> "application/octet_stream";
        };
    }

    private AssetMetadata createMetadataFromPath(String path) {
        return AssetMetadata.builder()
                .mimeType(determineMimeType(path))
                .category(extractCategory(path))
                .extension(extractExtension(path))
                .build();
    }
}
