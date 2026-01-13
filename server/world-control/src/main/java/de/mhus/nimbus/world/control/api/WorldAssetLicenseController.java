package de.mhus.nimbus.world.control.api;

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
 * REST Controller for Asset license operations at /control/worlds/{worldId}/assetlicense
 * Handles license information (source, author, license) for assets.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/assetlicense")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WorldAssetLicense", description = "Asset license management")
public class WorldAssetLicenseController extends BaseEditorController {

    private final SAssetService assetService;

    /**
     * Get license information for an asset.
     * GET /control/worlds/{worldId}/assetlicense/{*path}
     * Returns: { source, author, license, licenseFixed }
     */
    @GetMapping("/{*path}")
    @Operation(summary = "Get asset license information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "License info found or empty"),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<?> getLicenseInfo(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path) {

        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("GET asset license: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        Optional<SAsset> opt = assetService.findByPath(wid, path);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "source", "",
                    "author", "",
                    "license", "",
                    "licenseFixed", false
            ));
        }

        SAsset asset = opt.get();
        AssetMetadata metadata = asset.getPublicData();

        if (metadata == null) {
            return ResponseEntity.ok(Map.of(
                    "source", "",
                    "author", "",
                    "license", "",
                    "licenseFixed", false
            ));
        }

        return ResponseEntity.ok(Map.of(
                "source", metadata.getSource() != null ? metadata.getSource() : "",
                "author", metadata.getAuthor() != null ? metadata.getAuthor() : "",
                "license", metadata.getLicense() != null ? metadata.getLicense() : "",
                "licenseFixed", metadata.getLicenseFixed() != null ? metadata.getLicenseFixed() : false
        ));
    }

    /**
     * Set license information for an asset.
     * PUT /control/worlds/{worldId}/assetlicense/{*path}
     * Body: { source, author, license }
     * Automatically sets licenseFixed=true
     */
    @PutMapping("/{*path}")
    @Operation(summary = "Set asset license information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "License info updated"),
            @ApiResponse(responseCode = "404", description = "Asset not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> setLicenseInfo(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Asset path") @PathVariable String path,
            @RequestBody Map<String, String> licenseData) {

        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }

        log.debug("SET asset license: worldId={}, path={}", worldId, path);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId)
        );
        if (blank(path)) {
            return bad("asset path required");
        }

        try {
            Optional<SAsset> existing = assetService.findByPath(wid, path);
            if (existing.isEmpty()) {
                return notFound("asset not found");
            }

            SAsset asset = existing.get();
            AssetMetadata metadata = asset.getPublicData();
            if (metadata == null) {
                metadata = new AssetMetadata();
            }

            // Set license fields
            metadata.setSource(licenseData.get("source"));
            metadata.setAuthor(licenseData.get("author"));
            metadata.setLicense(licenseData.get("license"));
            // Automatically set licenseFixed to true
            metadata.setLicenseFixed(true);

            Optional<SAsset> updated = assetService.updateMetadata(asset, metadata);
            if (updated.isPresent()) {
                log.info("Updated asset license (licenseFixed=true): path={}", path);
                return ResponseEntity.ok(Map.of(
                        "source", metadata.getSource() != null ? metadata.getSource() : "",
                        "author", metadata.getAuthor() != null ? metadata.getAuthor() : "",
                        "license", metadata.getLicense() != null ? metadata.getLicense() : "",
                        "licenseFixed", true
                ));
            } else {
                return notFound("asset disappeared during update");
            }
        } catch (Exception e) {
            log.error("Error updating license info", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error"));
        }
    }
}
