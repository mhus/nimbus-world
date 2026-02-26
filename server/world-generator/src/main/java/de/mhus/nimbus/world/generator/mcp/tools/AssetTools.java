package de.mhus.nimbus.world.generator.mcp.tools;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssetTools {

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
