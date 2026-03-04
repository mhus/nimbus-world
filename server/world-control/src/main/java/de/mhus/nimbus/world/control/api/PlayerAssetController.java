package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only asset controller for players.
 * Serves asset files under /control/player/assets/ using the worldId from the session cookie.
 */
@RestController
@RequestMapping("/control/player/assets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Assets", description = "Read-only asset access for players")
public class PlayerAssetController extends BaseEditorController {

    private final SAssetService assetService;

    @GetMapping("/{*path}")
    @Operation(summary = "Get asset file content for player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asset found"),
            @ApiResponse(responseCode = "400", description = "Not authenticated or invalid request"),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<?> getAssetFile(
            @Parameter(description = "Asset path") @PathVariable String path,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        if (Strings.isBlank(path)) {
            return bad("asset path required");
        }

        var wid = WorldId.of(worldId).orElse(null);
        if (wid == null) {
            return bad("Invalid worldId format");
        }

        log.debug("GET player asset: worldId={}, path={}, userId={}", worldId, path, userId);

        Optional<SAsset> opt = assetService.findByPath(wid, path);
        if (opt.isEmpty()) {
            log.warn("Player asset not found: worldId={}, path={}", worldId, path);
            return notFound("asset not found");
        }

        SAsset asset = opt.get();

        if (!asset.isEnabled()) {
            log.warn("Player asset disabled: path={}", path);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "asset disabled"));
        }

        InputStream contentStream = assetService.loadContent(asset);
        if (contentStream == null) {
            log.warn("Player asset has no content: {}", path);
            return ResponseEntity.notFound().build();
        }

        String mimeType = determineMimeType(path);

        InputStreamResource resource = new InputStreamResource(contentStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(asset.getSize())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    private String determineMimeType(String path) {
        if (path == null || !path.contains(".")) {
            return "application/octet-stream";
        }
        String ext = path.substring(path.lastIndexOf('.')).toLowerCase();
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
            default -> "application/octet-stream";
        };
    }
}
