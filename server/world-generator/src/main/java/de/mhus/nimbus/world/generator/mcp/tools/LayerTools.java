package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.dto.CreateLayerRequest;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
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
public class LayerTools {

    private final WLayerService layerService;

    @Tool(name = "list_layers", description = "List all layers for a world. Use epoch parameter to filter by specific epoch.")
    public Map<String, Object> listLayers(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Optional epoch number to filter layers belonging to this epoch", required = false) Integer epoch) {
        log.debug("MCP: List layers: worldId={}, epoch={}", worldId, epoch);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        List<WLayer> layers = layerService.findByWorldId(worldId);

        // Filter by epoch if specified
        if (epoch != null) {
            layers = layers.stream()
                    .filter(l -> l.getEpoches() != null && l.getEpoches().contains(epoch))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> layerDtos = layers.stream()
                .map(this::toLayerDto)
                .collect(Collectors.toList());

        return Map.of(
                "layers", layerDtos,
                "count", layerDtos.size()
        );
    }

    @Tool(name = "get_layer", description = "Get detailed information about a specific layer")
    public Map<String, Object> getLayer(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer ID") String layerId) {
        log.debug("MCP: Get layer: worldId={}, layerId={}", worldId, layerId);

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

        return toLayerDto(layerOpt.get());
    }

    @Tool(name = "create_layer", description = "Create a new layer in a world. layerType must be TERRAIN or MODEL.")
    public Map<String, Object> createLayer(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Layer name (must be unique per world)") String name,
            @ToolParam(description = "Layer type: TERRAIN or MODEL") String layerType,
            @ToolParam(description = "Layer order (lower renders first)", required = false) Integer order,
            @ToolParam(description = "Whether the layer is enabled", required = false) Boolean enabled,
            @ToolParam(description = "Whether this layer defines ground level", required = false) Boolean baseGround,
            @ToolParam(description = "Epoch numbers this layer belongs to (e.g. [0,1,2]). If not specified, defaults to empty list.", required = false) List<Integer> epoches) {
        log.debug("MCP: Create layer: worldId={}, name={}", worldId, name);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(name)) {
            throw new McpToolException("name required");
        }

        if (Strings.isBlank(layerType)) {
            throw new McpToolException("layerType required (TERRAIN or MODEL)");
        }

        de.mhus.nimbus.world.shared.layer.LayerType type;
        try {
            type = de.mhus.nimbus.world.shared.layer.LayerType.valueOf(layerType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new McpToolException("Invalid layerType: " + layerType + " (must be TERRAIN or MODEL)");
        }

        if (layerService.findByWorldIdAndName(worldId, name).isPresent()) {
            throw new McpToolException("layer name already exists");
        }

        WLayer layer = WLayer.builder()
                .worldId(worldId)
                .name(name)
                .layerType(type)
                .allChunks(true)
                .affectedChunks(List.of())
                .order(order != null ? order : 0)
                .enabled(enabled != null ? enabled : true)
                .baseGround(baseGround != null ? baseGround : false)
                .epoches(epoches != null ? new ArrayList<>(epoches) : new ArrayList<>())
                .build();

        layer.touchCreate();
        WLayer saved = layerService.save(layer);

        log.info("MCP: Created layer: id={}, name={}", saved.getId(), saved.getName());
        return Map.of("id", saved.getId());
    }

    private Map<String, Object> toLayerDto(WLayer layer) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", layer.getId());
        dto.put("worldId", layer.getWorldId());
        dto.put("name", layer.getName());
        dto.put("layerType", layer.getLayerType().name());
        dto.put("layerDataId", layer.getLayerDataId());
        dto.put("allChunks", layer.isAllChunks());
        dto.put("affectedChunks", layer.getAffectedChunks());
        dto.put("order", layer.getOrder());
        dto.put("enabled", layer.isEnabled());
        dto.put("baseGround", layer.isBaseGround());
        dto.put("groups", layer.getGroups());
        dto.put("epoches", layer.getEpoches() != null ? layer.getEpoches() : List.of());
        dto.put("createdAt", layer.getCreatedAt());
        dto.put("updatedAt", layer.getUpdatedAt());
        return dto;
    }
}
