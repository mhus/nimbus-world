package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TerrainTools implements McpToolBean {

    private final WLayerService layerService;
    private final WWorldService worldService;

    @Tool(name = "list_terrain_chunk_keys", description = "List chunk keys (cx:cz) that have terrain data for a specific layer")
    public Map<String, Object> listTerrainChunkKeys(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer name") String layerName) {
        log.debug("MCP: List terrain chunk keys: worldId={}, layerName={}", worldId, layerName);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
        if (layerOpt.isEmpty()) {
            throw new McpToolException("layer not found: " + layerName);
        }

        WLayer layer = layerOpt.get();
        List<String> chunkKeys = layerService.findChunkKeysByLayerDataId(layer.getLayerDataId());

        Map<String, Object> result = new HashMap<>();
        result.put("layerName", layer.getName());
        result.put("layerType", layer.getLayerType().name());
        result.put("layerDataId", layer.getLayerDataId());
        result.put("chunkKeys", chunkKeys);
        result.put("count", chunkKeys.size());

        return result;
    }

    @Tool(name = "get_terrain_chunk_data", description = "Get terrain chunk storage data including blocks for a specific layer and chunk position")
    public Map<String, Object> getTerrainChunkData(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer name") String layerName,
            @ToolParam(description = "Chunk X coordinate") int cx,
            @ToolParam(description = "Chunk Z coordinate") int cz) {
        log.debug("MCP: Get terrain chunk data: worldId={}, layerName={}, cx={}, cz={}", worldId, layerName, cx, cz);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
        if (layerOpt.isEmpty()) {
            throw new McpToolException("layer not found: " + layerName);
        }

        WLayer layer = layerOpt.get();
        String chunkKey = cx + ":" + cz;

        Map<String, Object> result = new HashMap<>();
        result.put("layerName", layer.getName());
        result.put("layerType", layer.getLayerType().name());
        result.put("layerDataId", layer.getLayerDataId());
        result.put("chunkKey", chunkKey);
        result.put("cx", cx);
        result.put("cz", cz);

        Optional<WLayerTerrain> terrainOpt = layerService
                .findTerrainChunk(worldId, layer.getLayerDataId(), chunkKey);

        if (terrainOpt.isEmpty()) {
            result.put("exists", false);
            result.put("message", "No WLayerTerrain entity found for this layer/chunk combination");
            return result;
        }

        WLayerTerrain terrain = terrainOpt.get();
        result.put("exists", true);
        result.put("metadata", toTerrainMetadataDto(terrain));

        Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(worldId, layer.getLayerDataId(), chunkKey);
        if (chunkDataOpt.isEmpty()) {
            result.put("storageDataLoaded", false);
            result.put("message", "WLayerTerrain entity exists but storage data could not be loaded");
            return result;
        }

        LayerChunkData chunkData = chunkDataOpt.get();
        result.put("storageDataLoaded", true);

        List<LayerBlock> blocks = chunkData.getBlocks();
        result.put("blockCount", blocks != null ? blocks.size() : 0);

        if (blocks != null && !blocks.isEmpty()) {
            List<Map<String, Object>> blockDtos = blocks.stream()
                    .map(this::toLayerBlockDto)
                    .collect(Collectors.toList());
            result.put("blocks", blockDtos);

            Map<String, Long> blockTypeCounts = blocks.stream()
                    .filter(b -> b.getBlock() != null && b.getBlock().getBlockTypeId() != null)
                    .collect(Collectors.groupingBy(b -> b.getBlock().getBlockTypeId(), Collectors.counting()));
            result.put("blockTypeSummary", blockTypeCounts);
        } else {
            result.put("blocks", List.of());
            result.put("blockTypeSummary", Map.of());
        }

        Map<String, int[]> heightData = chunkData.getHeightData();
        result.put("heightDataEntries", heightData != null ? heightData.size() : 0);

        return result;
    }

    @Tool(name = "get_terrain_block_at", description = "Get a single block from a terrain layer at an exact world position (x, y, z). Automatically calculates the correct chunk.")
    public Map<String, Object> getTerrainBlockAt(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer name") String layerName,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Y coordinate") int y,
            @ToolParam(description = "World Z coordinate") int z) {
        log.debug("MCP: Get terrain block at: worldId={}, layerName={}, x={}, y={}, z={}", worldId, layerName, x, y, z);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new McpToolException("World not found: " + worldId)
        );
        int chunkSize = world.getPublicData().getChunkSize();

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
        if (layerOpt.isEmpty()) {
            throw new McpToolException("layer not found: " + layerName);
        }
        WLayer layer = layerOpt.get();

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
        result.put("layerName", layer.getName());

        Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(worldId, layer.getLayerDataId(), chunkKey);
        if (chunkDataOpt.isEmpty()) {
            result.put("found", false);
            result.put("message", "No terrain data for layer '" + layerName + "' in chunk " + chunkKey);
            return result;
        }

        LayerChunkData chunkData = chunkDataOpt.get();
        List<LayerBlock> blocks = chunkData.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            result.put("found", false);
            result.put("message", "Terrain chunk exists but contains no blocks");
            return result;
        }

        LayerBlock match = blocks.stream()
                .filter(b -> b.getBlock() != null && b.getBlock().getPosition() != null
                        && (int) b.getBlock().getPosition().getX() == x
                        && (int) b.getBlock().getPosition().getY() == y
                        && (int) b.getBlock().getPosition().getZ() == z)
                .findFirst()
                .orElse(null);

        if (match != null) {
            result.put("found", true);
            result.put("block", toLayerBlockDto(match));
        } else {
            result.put("found", false);
            result.put("message", "No block at this position in terrain layer (air or empty)");
            List<Map<String, Object>> nearby = blocks.stream()
                    .filter(b -> b.getBlock() != null && b.getBlock().getPosition() != null
                            && Math.abs((int) b.getBlock().getPosition().getX() - x) <= 1
                            && Math.abs((int) b.getBlock().getPosition().getY() - y) <= 1
                            && Math.abs((int) b.getBlock().getPosition().getZ() - z) <= 1)
                    .map(this::toLayerBlockDto)
                    .collect(Collectors.toList());
            if (!nearby.isEmpty()) {
                result.put("nearbyBlocks", nearby);
            }
        }

        return result;
    }

    private Map<String, Object> toTerrainMetadataDto(WLayerTerrain terrain) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", terrain.getId());
        dto.put("worldId", terrain.getWorldId());
        dto.put("layerDataId", terrain.getLayerDataId());
        dto.put("chunkKey", terrain.getChunkKey());
        dto.put("storageId", terrain.getStorageId());
        dto.put("compressed", terrain.isCompressed());
        dto.put("createdAt", terrain.getCreatedAt());
        dto.put("updatedAt", terrain.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toLayerBlockDto(LayerBlock layerBlock) {
        Map<String, Object> dto = new HashMap<>();
        if (layerBlock.getBlock() != null) {
            var block = layerBlock.getBlock();
            if (block.getPosition() != null) {
                dto.put("x", (int) block.getPosition().getX());
                dto.put("y", (int) block.getPosition().getY());
                dto.put("z", (int) block.getPosition().getZ());
            }
            dto.put("blockId", block.getBlockTypeId());
            if (block.getMetadata() != null) {
                dto.put("blockMetadata", block.getMetadata());
            }
            if (block.getModifiers() != null && !block.getModifiers().isEmpty()) {
                dto.put("modifiers", block.getModifiers());
            }
            if (block.getStatus() != null) {
                dto.put("status", block.getStatus());
            }
        }
        dto.put("group", layerBlock.getGroup());
        dto.put("layerMetadata", layerBlock.getMetadata());
        return dto;
    }
}
