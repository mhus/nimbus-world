package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.SAssetService;
import de.mhus.nimbus.world.shared.world.AssetMetadata;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetRepository;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Assets (read-only).
 * Serves binary assets and metadata from MongoDB.
 *
 * Endpoints:
 * - GET /player/worlds/{worldId}/assets - List assets with filters
 * - GET /player/worlds/{worldId}/assets/** - Serve binary asset
 * - GET /player/worlds/{worldId}/assets/**.info - Serve asset metadata
 */
@RestController
@RequestMapping("/player/worlds/{worldId}/assets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Assets", description = "Asset management (textures, models, audio)")
public class AssetController {

    private final SAssetService assetService;
    private final SAssetRepository assetRepository;

    /**
     * Serve binary asset file (alternative with explicit path).
     * This works better with Spring's path matching.
     */
    @GetMapping("/{*assetPath}")
    @Operation(summary = "Get asset binary", description = "Returns asset binary content with proper content type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asset binary"),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<?> getAssetByPath(
            @PathVariable String worldId,
            @PathVariable String assetPath) {

        // Remove leading slash if present (Spring path variable includes it)
        if (assetPath != null && assetPath.startsWith("/")) {
            assetPath = assetPath.substring(1);
        }
        final String finalAssetPath = assetPath; // Make final for lambda

        log.debug("Asset request: worldId={}, path={}", worldId, finalAssetPath);

        // Find asset in database
        // Try with worldId as regionId first (for main worlds), then fallback to null regionId
        SAsset asset = assetService.findByPath(WorldId.of(worldId).orElse(null), finalAssetPath)
                .orElse(null);

        if (asset == null) {
            log.debug("Asset not found: {}", finalAssetPath);
            return ResponseEntity.notFound().build();
        }

        if (!asset.isEnabled()) {
            log.debug("Asset disabled: {}", finalAssetPath);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Load content as stream - no memory loading!
        InputStream contentStream = assetService.loadContent(asset);
        if (contentStream == null) {
            log.warn("Asset has no content: {}", finalAssetPath);
            return ResponseEntity.notFound().build();
        }

        // Determine content type from metadata or path
        String contentType = determineContentType(asset);

        // Build response with headers for streaming
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));

        // Set content length if available from asset metadata
        if (asset.getSize() > 0) {
            headers.setContentLength(asset.getSize());
        }
        //XXX
        headers.setCacheControl("public, max-age=86400"); // 24 hours cache

        // Set filename for download (use asset name from DB)
        String filename = asset.getName() != null ? asset.getName() : "asset";
        headers.setContentDispositionFormData("inline", filename);

        log.trace("Streaming asset: path={}, size={}, type={}, filename={}",
                 finalAssetPath, asset.getSize(), contentType, filename);

        // Return InputStreamResource for direct streaming without memory loading
        org.springframework.core.io.InputStreamResource resource = new org.springframework.core.io.InputStreamResource(contentStream);

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Determine content type from asset metadata or file extension.
     */
    private String determineContentType(SAsset asset) {
        // Try metadata first
        if (asset.getPublicData() != null && asset.getPublicData().getMimeType() != null) {
            return asset.getPublicData().getMimeType();
        }

        // Fallback to extension-based detection
        String path = asset.getPath();
        if (path == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".gif")) return "image/gif";
        if (lowerPath.endsWith(".webp")) return "image/webp";
        if (lowerPath.endsWith(".svg")) return "image/svg+xml";
        if (lowerPath.endsWith(".json")) return MediaType.APPLICATION_JSON_VALUE;
        if (lowerPath.endsWith(".obj") || lowerPath.endsWith(".mtl")) return MediaType.TEXT_PLAIN_VALUE;
        if (lowerPath.endsWith(".glb") || lowerPath.endsWith(".gltf")) return "model/gltf-binary";
        if (lowerPath.endsWith(".wav")) return "audio/wav";
        if (lowerPath.endsWith(".mp3")) return "audio/mpeg";
        if (lowerPath.endsWith(".ogg")) return "audio/ogg";

        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

}
