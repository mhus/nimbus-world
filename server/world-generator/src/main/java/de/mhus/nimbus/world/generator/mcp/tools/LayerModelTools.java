package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.generator.mcp.dto.BlockRequest;
import de.mhus.nimbus.world.generator.mcp.dto.ImportLayerModelRequest;
import de.mhus.nimbus.world.shared.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LayerModelTools {

    private final WLayerService layerService;
    private final WLayerModelRepository modelRepository;

    @Tool(name = "get_layer_blocks", description = "Get blocks from a MODEL layer")
    public Map<String, Object> getLayerBlocks(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer ID (must be MODEL type)") String layerId) {
        log.debug("MCP: Get layer blocks: worldId={}, layerId={}", worldId, layerId);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(layerId)) {
            throw new McpToolException("layerId is required");
        }

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            throw new McpToolException("layer not found");
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            throw new McpToolException("operation only supported for MODEL layers");
        }

        if (layer.getLayerDataId() == null) {
            return Map.of("blocks", List.of(), "count", 0);
        }

        Optional<WLayerModel> modelOpt = modelRepository.findFirstByLayerDataId(layer.getLayerDataId());
        if (modelOpt.isEmpty()) {
            return Map.of("blocks", List.of(), "count", 0);
        }

        List<LayerBlock> blocks = modelOpt.get().getContent();
        List<Map<String, Object>> blockDtos = blocks.stream()
                .map(this::toLayerBlockDto)
                .collect(Collectors.toList());

        return Map.of(
                "blocks", blockDtos,
                "count", blockDtos.size()
        );
    }

    @Tool(name = "add_layer_blocks", description = "Add blocks to a MODEL layer")
    public Map<String, Object> addLayerBlocks(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer ID (must be MODEL type)") String layerId,
            @ToolParam(description = "Array of blocks to add with x, y, z, blockId, and optional group") List<BlockRequest> blocks) {
        log.debug("MCP: Add layer blocks: worldId={}, layerId={}, count={}", worldId, layerId, blocks.size());

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(layerId)) {
            throw new McpToolException("layerId is required");
        }

        if (blocks == null || blocks.isEmpty()) {
            throw new McpToolException("blocks required");
        }

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            throw new McpToolException("layer not found");
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            throw new McpToolException("operation only supported for MODEL layers");
        }

        // Ensure layerDataId exists
        if (layer.getLayerDataId() == null) {
            layer.setLayerDataId(UUID.randomUUID().toString());
            layer.touchUpdate();
            layerService.save(layer);
        }

        // Load or create model
        WLayerModel model = modelRepository.findFirstByLayerDataId(layer.getLayerDataId())
                .orElseGet(() -> {
                    WLayerModel newModel = WLayerModel.builder()
                            .worldId(worldId)
                            .layerDataId(layer.getLayerDataId())
                            .content(new ArrayList<>())
                            .build();
                    newModel.touchCreate();
                    return newModel;
                });

        // Convert request blocks to LayerBlocks
        List<LayerBlock> newBlocks = blocks.stream()
                .map(b -> {
                    de.mhus.nimbus.generated.types.Vector3Int pos = new de.mhus.nimbus.generated.types.Vector3Int();
                    pos.setX(b.x());
                    pos.setY(b.y());
                    pos.setZ(b.z());

                    Block block = Block.builder()
                            .position(pos)
                            .blockTypeId(b.blockId())
                            .build();

                    return LayerBlock.builder()
                            .block(block)
                            .group(b.group())
                            .build();
                })
                .collect(Collectors.toList());

        // Add to existing blocks
        List<LayerBlock> allBlocks = new ArrayList<>(model.getContent());
        allBlocks.addAll(newBlocks);
        model.setContent(allBlocks);
        model.touchUpdate();

        modelRepository.save(model);

        log.info("MCP: Added {} blocks to layer: id={}", newBlocks.size(), layerId);
        return Map.of(
                "added", newBlocks.size(),
                "total", allBlocks.size()
        );
    }

    @Tool(name = "import_layer_model", description = "Import a WLayerModel from JSON (e.g. schematic-tool output) into a MODEL layer. Creates a new model with blocks, metadata, and parameters.")
    public Map<String, Object> importLayerModel(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer ID (must be MODEL type)") String layerId,
            @ToolParam(description = "Model name (technical identifier, defaults to layer name)", required = false) String name,
            @ToolParam(description = "Display title", required = false) String title,
            @ToolParam(description = "License source URL", required = false) String licenseSource,
            @ToolParam(description = "License type (e.g. CC-BY-4.0)", required = false) String licenseType,
            @ToolParam(description = "License author", required = false) String licenseAuthor,
            @ToolParam(description = "Mount point X coordinate", required = false) Integer mountX,
            @ToolParam(description = "Mount point Y coordinate", required = false) Integer mountY,
            @ToolParam(description = "Mount point Z coordinate", required = false) Integer mountZ,
            @ToolParam(description = "Rotation (0-3)", required = false) Integer rotation,
            @ToolParam(description = "Order (default 100)", required = false) Integer order,
            @ToolParam(description = "Size X", required = false) Integer sizeX,
            @ToolParam(description = "Size Y", required = false) Integer sizeY,
            @ToolParam(description = "Size Z", required = false) Integer sizeZ,
            @ToolParam(description = "Groups map", required = false) Map<String, String> groups,
            @ToolParam(description = "Key-value metadata (e.g. style, kind)", required = false) Map<String, String> parameters,
            @ToolParam(description = "Array of blocks (same format as add_layer_blocks)", required = false) List<BlockRequest> blocks) {
        log.debug("MCP: Import layer model: worldId={}, layerId={}", worldId, layerId);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(layerId)) {
            throw new McpToolException("layerId is required");
        }

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            throw new McpToolException("layer not found");
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            throw new McpToolException("operation only supported for MODEL layers");
        }

        // Ensure layerDataId exists
        if (layer.getLayerDataId() == null) {
            layer.setLayerDataId(UUID.randomUUID().toString());
            layer.touchUpdate();
            layerService.save(layer);
        }

        // Build model
        WLayerModel model = WLayerModel.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .name(name != null ? name : layer.getName())
                .title(title)
                .licenseSource(licenseSource)
                .licenseType(licenseType)
                .licenseAuthor(licenseAuthor)
                .mountX(mountX != null ? mountX : 0)
                .mountY(mountY != null ? mountY : 0)
                .mountZ(mountZ != null ? mountZ : 0)
                .rotation(rotation != null ? rotation : 0)
                .order(order != null ? order : 100)
                .sizeX(sizeX != null ? sizeX : 0)
                .sizeY(sizeY != null ? sizeY : 0)
                .sizeZ(sizeZ != null ? sizeZ : 0)
                .groups(groups != null ? groups : new HashMap<>())
                .parameters(parameters != null ? parameters : new HashMap<>())
                .content(new ArrayList<>())
                .build();

        // Convert blocks if provided
        if (blocks != null && !blocks.isEmpty()) {
            List<LayerBlock> layerBlocks = blocks.stream()
                    .map(b -> {
                        de.mhus.nimbus.generated.types.Vector3Int pos = new de.mhus.nimbus.generated.types.Vector3Int();
                        pos.setX(b.x());
                        pos.setY(b.y());
                        pos.setZ(b.z());

                        Block block = Block.builder()
                                .position(pos)
                                .blockTypeId(b.blockId())
                                .build();

                        return LayerBlock.builder()
                                .block(block)
                                .group(b.group())
                                .build();
                    })
                    .collect(Collectors.toList());
            model.setContent(layerBlocks);
        }

        model.touchCreate();
        WLayerModel saved = modelRepository.save(model);

        log.info("MCP: Imported layer model: id={}, name={}, blocks={}", saved.getId(), saved.getName(), saved.getContent().size());

        return Map.of(
                "id", saved.getId(),
                "name", saved.getName() != null ? saved.getName() : "",
                "blocks", saved.getContent().size()
        );
    }

    private Map<String, Object> toLayerBlockDto(LayerBlock layerBlock) {
        Map<String, Object> dto = new HashMap<>();
        if (layerBlock.getBlock() != null) {
            Block block = layerBlock.getBlock();
            if (block.getPosition() != null) {
                dto.put("x", (int) block.getPosition().getX());
                dto.put("y", (int) block.getPosition().getY());
                dto.put("z", (int) block.getPosition().getZ());
            }
            dto.put("blockId", block.getBlockTypeId());
        }
        dto.put("group", layerBlock.getGroup());
        dto.put("metadata", layerBlock.getMetadata());
        return dto;
    }
}
