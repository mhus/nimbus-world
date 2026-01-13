package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.SAssetService;
import de.mhus.nimbus.world.shared.world.AssetMetadata;
import de.mhus.nimbus.world.shared.world.SAsset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Asset metadata operations at /control/worlds/{worldId}/assetinfo
 * Handles .info suffix for asset metadata (GET and PUT only).
 * For binary asset content, use WorldAssetController.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/assetinfo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WorldAssetInfo", description = "Asset metadata management")
public class WorldAssetInfoController extends BaseEditorController {

    private final SAssetService assetService;
    private final ObjectMapper objectMapper;

    /**
     * Get asset metadata.
     * GET /control/worlds/{worldId}/assetinfo/{*path}
     * Example: GET /control/worlds/main/assetinfo/textures/block/stone.png
     * Returns the asset's publicData (AssetMetadata)
     */
    @GetMapping("/{*path}")
    @Operation(summary = "Get asset metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata found or empty"),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<?> getAssetInfo(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("GET asset info: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        // Find asset (worldId=worldId)
        Optional<SAsset> opt = assetService.findByPath(wid, path);
        if (opt.isEmpty()) {
            // Return empty description if not found (like test_server)
            log.debug("Asset not found for info request: {}", path);
            return ResponseEntity.ok(Map.of("description", ""));
        }

        SAsset asset = opt.get();

        // Return metadata (publicData)
        if (asset.getPublicData() == null) {
            return ResponseEntity.ok(Map.of("description", ""));
        }

        return ResponseEntity.ok(asset.getPublicData());
    }

    /**
     * Update asset metadata.
     * PUT /control/worlds/{worldId}/assetinfo/{*path}
     * Example: PUT /control/worlds/main/assetinfo/textures/block/stone.png
     * Body: AssetMetadata JSON { description: "..." }
     */
    @PutMapping("/{*path}")
    @Operation(summary = "Update asset metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata updated"),
            @ApiResponse(responseCode = "404", description = "Asset not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> updateAssetInfo(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path,
            @RequestBody(required = true) String jsonContent) {

        // Remove leading slash if present
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("UPDATE asset metadata: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        try {
            Optional<SAsset> existing = assetService.findByPath(wid, path);
            if (existing.isEmpty()) {
                return notFound("asset not found");
            }

            // Parse metadata from request body (JSON)
            AssetMetadata metadata = objectMapper.readValue(jsonContent, AssetMetadata.class);

            Optional<SAsset> updated = assetService.updateMetadata(existing.get(), metadata);
            if (updated.isPresent()) {
                log.info("Updated asset metadata: path={}", path);
                return ResponseEntity.ok(updated.get().getPublicData());
            } else {
                return notFound("asset disappeared during update");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Validation error updating metadata: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error updating metadata", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error"));
        }
    }

}

