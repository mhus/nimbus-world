package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.world.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkTools {

    private final WChunkService chunkService;
    private final WWorldService worldService;
    private final WEditCacheService editCacheService;

    @Tool(name = "get_chunk_data", description = "Get chunk storage data including blocks for a specific chunk position")
    public Map<String, Object> getChunkData(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Chunk X coordinate") int cx,
            @ToolParam(description = "Chunk Z coordinate") int cz) {
        log.debug("MCP: Get chunk data: worldId={}, cx={}, cz={}", worldId, cx, cz);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        String chunkKey = cx + ":" + cz;

        Optional<WChunk> chunkOpt = chunkService.find(wid, chunkKey);

        Map<String, Object> result = new HashMap<>();
        result.put("chunkKey", chunkKey);
        result.put("cx", cx);
        result.put("cz", cz);

        if (chunkOpt.isEmpty()) {
            result.put("exists", false);
            result.put("message", "No WChunk entity found for this position");
            return result;
        }

        WChunk chunk = chunkOpt.get();
        result.put("exists", true);
        result.put("metadata", toChunkMetadataDto(chunk));

        Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(wid, chunkKey, false);
        if (chunkDataOpt.isEmpty()) {
            result.put("storageDataLoaded", false);
            result.put("message", "WChunk entity exists but storage data could not be loaded");
            return result;
        }

        ChunkData chunkData = chunkDataOpt.get();
        result.put("storageDataLoaded", true);

        List<Block> blocks = chunkData.getBlocks();
        result.put("blockCount", blocks != null ? blocks.size() : 0);

        if (blocks != null && !blocks.isEmpty()) {
            List<Map<String, Object>> blockDtos = blocks.stream()
                    .map(this::toChunkBlockDto)
                    .collect(Collectors.toList());
            result.put("blocks", blockDtos);

            Map<String, Long> blockTypeCounts = blocks.stream()
                    .filter(b -> b.getBlockTypeId() != null)
                    .collect(Collectors.groupingBy(Block::getBlockTypeId, Collectors.counting()));
            result.put("blockTypeSummary", blockTypeCounts);
        } else {
            result.put("blocks", List.of());
            result.put("blockTypeSummary", Map.of());
        }

        Map<String, int[]> heightData = chunkData.getHeightData();
        result.put("heightDataEntries", heightData != null ? heightData.size() : 0);

        return result;
    }

    @Tool(name = "get_block_at", description = "Get the block at an exact world position (x, y, z). Automatically calculates the correct chunk.")
    public Map<String, Object> getBlockAt(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Y coordinate") int y,
            @ToolParam(description = "World Z coordinate") int z) {
        log.debug("MCP: Get block at: worldId={}, x={}, y={}, z={}", worldId, x, y, z);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new McpToolException("World not found: " + worldId)
        );
        int chunkSize = world.getPublicData().getChunkSize();

        int cx = Math.floorDiv(x, chunkSize);
        int cz = Math.floorDiv(z, chunkSize);
        String chunkKey = cx + ":" + cz;

        Map<String, Object> result = new HashMap<>();
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        result.put("chunkSize", chunkSize);
        result.put("chunkKey", chunkKey);
        result.put("cx", cx);
        result.put("cz", cz);

        Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(wid, chunkKey, false);
        if (chunkDataOpt.isEmpty()) {
            result.put("found", false);
            result.put("message", "Chunk " + chunkKey + " not found or has no storage data");
            return result;
        }

        ChunkData chunkData = chunkDataOpt.get();
        List<Block> blocks = chunkData.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            result.put("found", false);
            result.put("message", "Chunk exists but contains no blocks");
            return result;
        }

        Block match = blocks.stream()
                .filter(b -> b.getPosition() != null
                        && (int) b.getPosition().getX() == x
                        && (int) b.getPosition().getY() == y
                        && (int) b.getPosition().getZ() == z)
                .findFirst()
                .orElse(null);

        if (match != null) {
            result.put("found", true);
            result.put("source", "chunk");
            result.put("block", toChunkBlockDto(match));
        } else {
            result.put("found", false);
            result.put("message", "No block at this position in chunk storage (air or empty)");
            List<Map<String, Object>> nearby = blocks.stream()
                    .filter(b -> b.getPosition() != null
                            && Math.abs((int) b.getPosition().getX() - x) <= 1
                            && Math.abs((int) b.getPosition().getY() - y) <= 1
                            && Math.abs((int) b.getPosition().getZ() - z) <= 1)
                    .map(this::toChunkBlockDto)
                    .collect(Collectors.toList());
            if (!nearby.isEmpty()) {
                result.put("nearbyBlocks", nearby);
            }
        }

        // Check EditCache for uncommitted edits
        List<WEditCache> editCacheEntries = editCacheService.findByWorldIdAndPosition(worldId, x, y, z);
        if (!editCacheEntries.isEmpty()) {
            List<Map<String, Object>> cacheBlocks = editCacheEntries.stream()
                    .map(entry -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("layerDataId", entry.getLayerDataId());
                        dto.put("modelName", entry.getModelName());
                        dto.put("chunk", entry.getChunk());
                        dto.put("createdAt", entry.getCreatedAt());
                        dto.put("modifiedAt", entry.getModifiedAt());
                        if (entry.getBlock() != null) {
                            if (entry.getBlock().getBlock() != null) {
                                dto.put("block", toChunkBlockDto(entry.getBlock().getBlock()));
                            }
                            if (entry.getBlock().getGroup() != null) {
                                dto.put("group", entry.getBlock().getGroup());
                            }
                            if (entry.getBlock().getMetadata() != null) {
                                dto.put("layerMetadata", entry.getBlock().getMetadata());
                            }
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());
            result.put("editCache", cacheBlocks);
            if (!(boolean) result.get("found")) {
                result.put("found", true);
                result.put("source", "editCache");
            }
        }

        return result;
    }

    @Tool(name = "list_server_info_keys", description = "List all block coordinate keys that have server info in a chunk. Returns coordinate keys like '-24,70,39'.")
    public Map<String, Object> listServerInfoKeys(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Chunk key (e.g. '-1:1')") String chunkKey) {
        log.debug("MCP: List server info keys: worldId={}, chunkKey={}", worldId, chunkKey);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        var keys = chunkService.getServerInfoKeys(wid, chunkKey);
        return Map.of("chunkKey", chunkKey, "keys", keys, "count", keys.size());
    }

    @Tool(name = "get_server_info", description = "Get server info (metadata) for a specific block position. Server info contains action configuration like 'action=door', 'value=toggle' etc.")
    public Map<String, Object> getServerInfo(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Y coordinate") int y,
            @ToolParam(description = "World Z coordinate") int z) {
        log.debug("MCP: Get server info: worldId={}, x={}, y={}, z={}", worldId, x, y, z);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        var info = chunkService.getServerInfo(wid, x, y, z);
        Map<String, Object> result = new HashMap<>();
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        if (info != null) {
            result.put("found", true);
            result.put("serverInfo", info);
        } else {
            result.put("found", false);
        }
        return result;
    }

    @Tool(name = "set_server_info", description = "Set server info (metadata) for a specific block position. Use to configure block actions like doors (action=door, value=toggle).")
    public Map<String, Object> setServerInfo(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Y coordinate") int y,
            @ToolParam(description = "World Z coordinate") int z,
            @ToolParam(description = "Server info key-value pairs (e.g. {\"action\": \"door\", \"value\": \"toggle\"})") Map<String, String> serverInfo) {
        log.debug("MCP: Set server info: worldId={}, x={}, y={}, z={}, info={}", worldId, x, y, z, serverInfo);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (serverInfo == null || serverInfo.isEmpty()) {
            throw new McpToolException("serverInfo must not be empty");
        }

        chunkService.setServerInfo(wid, x, y, z, serverInfo);
        return Map.of("x", x, "y", y, "z", z, "serverInfo", serverInfo, "status", "saved");
    }

    @Tool(name = "remove_server_info", description = "Remove server info (metadata) for a specific block position.")
    public Map<String, Object> removeServerInfo(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Y coordinate") int y,
            @ToolParam(description = "World Z coordinate") int z) {
        log.debug("MCP: Remove server info: worldId={}, x={}, y={}, z={}", worldId, x, y, z);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        chunkService.removeServerInfo(wid, x, y, z);
        return Map.of("x", x, "y", y, "z", z, "status", "removed");
    }

    private Map<String, Object> toChunkMetadataDto(WChunk chunk) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", chunk.getId());
        dto.put("worldId", chunk.getWorldId());
        dto.put("chunk", chunk.getChunk());
        dto.put("hex", chunk.getHex());
        dto.put("storageId", chunk.getStorageId());
        dto.put("compressed", chunk.isCompressed());
        dto.put("blockCount", chunk.getBlockCount());
        dto.put("chunkSize", chunk.getChunkSize());
        dto.put("hasInfoServer", chunk.getInfoServer() != null && !chunk.getInfoServer().isEmpty());
        dto.put("createdAt", chunk.getCreatedAt());
        dto.put("updatedAt", chunk.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toChunkBlockDto(Block block) {
        Map<String, Object> dto = new HashMap<>();
        if (block.getPosition() != null) {
            dto.put("x", (int) block.getPosition().getX());
            dto.put("y", (int) block.getPosition().getY());
            dto.put("z", (int) block.getPosition().getZ());
        }
        dto.put("blockTypeId", block.getBlockTypeId());
        if (block.getFaceVisibility() != null) {
            dto.put("faceVisibility", block.getFaceVisibility());
        }
        if (!BlockUtil.isStatusDefault(block.getStatus())) {
            dto.put("status", block.getStatus());
        }
        if (block.getOffsets() != null) {
            dto.put("offsets", block.getOffsets());
        }
        if (block.getMetadata() != null) {
            dto.put("metadata", block.getMetadata());
        }
        return dto;
    }
}
