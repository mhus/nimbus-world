package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import de.mhus.nimbus.world.shared.world.AssetMetadata;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssetTools implements McpToolBean {

    private final SAssetService assetService;

    @Tool(name = "search_assets", description = "Search assets by collection, file type and query. Collections: 'w' (World), 'r' (Region), 'rp' (Region Public), 'm' (Minecraft-like Shared), 'n' (Nimbus Shared), 'p' (Public Shared).")
    public Map<String, Object> searchAssets(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Collection prefix: 'w', 'r', 'rp', 'm', 'n', 'p'", required = false) String collection,
            @ToolParam(description = "File extension (e.g., 'png', 'jpg', 'json')", required = false) String fileType,
            @ToolParam(description = "Search query for asset path/name (e.g., 'sand', 'stone')", required = false) String query,
            @ToolParam(description = "Pagination offset", required = false) Integer offset,
            @ToolParam(description = "Maximum number of results", required = false) Integer limit) {
        log.debug("MCP: Search assets: worldId={}, collection={}, fileType={}, query={}, offset={}, limit={}",
                worldId, collection, fileType, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        int effectiveOffset = offset != null ? offset : 0;
        int effectiveLimit = limit != null ? limit : 100;

        String searchQuery = query;
        if (Strings.isNotBlank(collection)) {
            searchQuery = Strings.isNotBlank(query) ? collection + ":" + query : collection + ":";
        }

        var result = assetService.searchAssets(wid, searchQuery, fileType, effectiveOffset, effectiveLimit);

        List<Map<String, Object>> assetDtos = result.assets().stream()
                .map(this::toAssetDto)
                .collect(Collectors.toList());

        return Map.of(
                "assets", assetDtos,
                "count", assetDtos.size(),
                "total", result.totalCount(),
                "offset", result.offset(),
                "limit", result.limit()
        );
    }

    @Tool(name = "import_local_asset", description = "Import a file from the local filesystem as an asset. Reads the file and stores it in the asset storage. Supports importing multiple files from a directory using a glob pattern.")
    public Map<String, Object> importLocalAsset(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Absolute path to the local file or directory (e.g. '/path/to/model.glb' or '/path/to/models/')") String localPath,
            @ToolParam(description = "Asset path in the storage (e.g. 'models/avatars/farmer.glb'). For directory imports, this is the base path prefix.") String assetPath,
            @ToolParam(description = "File extension filter when importing a directory (e.g. 'glb', 'png')", required = false) String fileExtension,
            @ToolParam(description = "Created by identifier", required = false) String createdBy) {
        log.debug("MCP: Import local asset: worldId={}, localPath={}, assetPath={}", worldId, localPath, assetPath);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "mcp";
        }

        Path path = Path.of(localPath);
        if (!Files.exists(path)) {
            throw new McpToolException("File not found: " + localPath);
        }

        if (Files.isDirectory(path)) {
            return importDirectory(wid, path, assetPath, fileExtension, createdBy);
        } else {
            return importSingleFile(wid, path, assetPath, createdBy);
        }
    }

    private Map<String, Object> importSingleFile(WorldId wid, Path filePath, String assetPath, String createdBy) {
        String mimeType = detectMimeType(filePath.getFileName().toString());
        var metadata = AssetMetadata.builder()
                .mimeType(mimeType)
                .category("models")
                .build();

        try (var stream = new FileInputStream(filePath.toFile())) {
            // Check if asset already exists and delete it first
            var existing = assetService.findByPath(wid, assetPath);
            if (existing.isPresent()) {
                assetService.delete(existing.get());
                log.info("Deleted existing asset before re-import: path={}", assetPath);
            }

            var asset = assetService.saveAsset(wid, assetPath, stream, createdBy, metadata);
            log.info("Imported asset: localPath={}, assetPath={}, size={}", filePath, assetPath, asset.getSize());
            return Map.of(
                    "status", "ok",
                    "asset", toAssetDto(asset)
            );
        } catch (IOException e) {
            throw new McpToolException("Failed to read file: " + filePath + " - " + e.getMessage());
        }
    }

    private Map<String, Object> importDirectory(WorldId wid, Path dirPath, String baseAssetPath, String fileExtension, String createdBy) {
        // Normalize base path (ensure trailing slash)
        String basePath = baseAssetPath.endsWith("/") ? baseAssetPath : baseAssetPath + "/";

        List<Map<String, Object>> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (var files = Files.list(dirPath)) {
            var fileList = files
                    .filter(Files::isRegularFile)
                    .filter(f -> fileExtension == null || f.getFileName().toString().toLowerCase().endsWith("." + fileExtension.toLowerCase()))
                    .sorted()
                    .toList();

            for (Path file : fileList) {
                String fileName = file.getFileName().toString();
                String targetPath = basePath + fileName;
                try {
                    var result = importSingleFile(wid, file, targetPath, createdBy);
                    @SuppressWarnings("unchecked")
                    var assetDto = (Map<String, Object>) result.get("asset");
                    imported.add(assetDto);
                } catch (Exception e) {
                    log.error("Failed to import {}: {}", fileName, e.getMessage());
                    errors.add(fileName + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new McpToolException("Failed to list directory: " + dirPath + " - " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", errors.isEmpty() ? "ok" : "partial");
        result.put("imported", imported.size());
        result.put("errors", errors);
        result.put("assets", imported);
        return result;
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".glb")) return "model/gltf-binary";
        if (lower.endsWith(".gltf")) return "model/gltf+json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    private Map<String, Object> toAssetDto(SAsset asset) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", asset.getId());
        dto.put("worldId", asset.getWorldId());
        dto.put("path", asset.getPath());
        dto.put("name", asset.getName());
        dto.put("size", asset.getSize());
        dto.put("enabled", asset.isEnabled());
        dto.put("compressed", asset.isCompressed());
        if (asset.getPublicData() != null) {
            dto.put("description", asset.getPublicData().getDescription());
            dto.put("mimeType", asset.getPublicData().getMimeType());
        }
        dto.put("createdAt", asset.getCreatedAt());
        dto.put("createdBy", asset.getCreatedBy());
        return dto;
    }
}
