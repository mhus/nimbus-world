package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityModelTools implements McpToolBean {

    private final WEntityModelService entityModelService;
    private final ObjectMapper objectMapper;

    @Tool(name = "list_entity_models", description = "List all entity models for a world/region. Returns modelId, title, type, poseType, gender, modelPath, and enabled status.")
    public Map<String, Object> listEntityModels(
            @ToolParam(description = "World ID or region (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Optional search query to filter by modelId", required = false) String query) {
        log.debug("MCP: List entity models: worldId={}, query={}", worldId, query);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        List<WEntityModel> models;
        if (query != null && !query.isBlank()) {
            models = entityModelService.findByWorldIdAndQuery(wid, query);
        } else {
            models = entityModelService.findByWorldId(wid);
        }

        List<Map<String, Object>> dtos = models.stream()
                .map(this::toModelDto)
                .collect(Collectors.toList());

        return Map.of("models", dtos, "count", dtos.size());
    }

    @Tool(name = "create_entity_model", description = "Create or update a WEntityModel from a JSON definition. The JSON should contain the full EntityModel publicData (id, type, modelPath, scale, poseMapping, dimensions, etc.).")
    public Map<String, Object> createEntityModel(
            @ToolParam(description = "World ID or region collection (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Model ID (unique identifier, e.g. 'bull', 'farmer')") String modelId,
            @ToolParam(description = "Display title (e.g. 'Bull', 'Farmer')") String title,
            @ToolParam(description = "EntityModel publicData as JSON object") Map<String, Object> publicData,
            @ToolParam(description = "Description", required = false) String description) {
        log.debug("MCP: Create entity model: worldId={}, modelId={}", worldId, modelId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );
        if (!wid.isCollection()) {
            wid = wid.toCollection();
        }

        EntityModel entityModel = objectMapper.convertValue(publicData, EntityModel.class);
        entityModel.setId(modelId);

        var saved = entityModelService.save(wid, modelId, entityModel);
        if (description != null) {
            saved.setDescription(description);
            saved.setTitle(title);
        } else {
            saved.setTitle(title);
        }

        log.info("Created/Updated entity model: modelId={}, worldId={}", modelId, wid.getId());
        return Map.of("status", "ok", "model", toModelDto(saved));
    }

    @Tool(name = "import_entity_models_from_directory", description = "Import multiple WEntityModel definitions from JSON files in a local directory. Each JSON file should contain a full EntityModel definition.")
    public Map<String, Object> importEntityModelsFromDirectory(
            @ToolParam(description = "World ID or region collection (e.g. '@region:earth616')") String worldId,
            @ToolParam(description = "Absolute path to directory containing JSON files") String directoryPath,
            @ToolParam(description = "Optional modelPath prefix override. If set, replaces the modelPath prefix in each JSON (e.g. 'models/' to use 'models/<filename>.glb')", required = false) String modelPathPrefix) {
        log.debug("MCP: Import entity models from directory: worldId={}, dir={}", worldId, directoryPath);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );
        if (!wid.isCollection()) {
            wid = wid.toCollection();
        }

        Path dir = Path.of(directoryPath);
        if (!Files.isDirectory(dir)) {
            throw new McpToolException("Not a directory: " + directoryPath);
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (var files = Files.list(dir)) {
            var jsonFiles = files
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .toList();

            for (Path jsonFile : jsonFiles) {
                try {
                    String json = Files.readString(jsonFile);
                    EntityModel entityModel = objectMapper.readValue(json, EntityModel.class);

                    // Derive modelId from the JSON id field (strip prefix like "n:")
                    String modelId = entityModel.getId();
                    if (modelId != null && modelId.contains(":")) {
                        modelId = modelId.substring(modelId.indexOf(':') + 1);
                    }
                    if (modelId == null || modelId.isBlank()) {
                        // Fallback: derive from filename
                        String fileName = jsonFile.getFileName().toString();
                        modelId = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase()
                                .replace(' ', '-').replace('_', '-');
                    }

                    // Override modelPath if prefix is provided
                    if (modelPathPrefix != null && !modelPathPrefix.isBlank()) {
                        // Find the GLB file name from the original path or from the JSON file name
                        String glbFileName = jsonFile.getFileName().toString()
                                .replace(".json", ".glb");
                        String prefix = modelPathPrefix.endsWith("/") ? modelPathPrefix : modelPathPrefix + "/";
                        entityModel.setModelPath(prefix + glbFileName);
                    }

                    entityModel.setId(modelId);

                    var saved = entityModelService.save(wid, modelId, entityModel);
                    // Derive title from modelId
                    String title = modelId.substring(0, 1).toUpperCase() + modelId.substring(1)
                            .replace('-', ' ').replace('_', ' ');
                    saved.setTitle(title);

                    imported.add(toModelDto(saved));
                    log.info("Imported entity model: modelId={} from {}", modelId, jsonFile.getFileName());
                } catch (Exception e) {
                    String error = jsonFile.getFileName() + ": " + e.getMessage();
                    log.error("Failed to import entity model from {}: {}", jsonFile.getFileName(), e.getMessage());
                    errors.add(error);
                }
            }
        } catch (IOException e) {
            throw new McpToolException("Failed to list directory: " + directoryPath + " - " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", errors.isEmpty() ? "ok" : "partial");
        result.put("imported", imported.size());
        result.put("errors", errors);
        result.put("models", imported);
        return result;
    }

    private Map<String, Object> toModelDto(WEntityModel model) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", model.getId());
        dto.put("modelId", model.getModelId());
        dto.put("worldId", model.getWorldId());
        dto.put("title", model.getTitle());
        dto.put("description", model.getDescription());
        dto.put("enabled", model.isEnabled());
        if (model.getPublicData() != null) {
            dto.put("type", model.getPublicData().getType());
            dto.put("poseType", model.getPublicData().getPoseType());
            dto.put("gender", model.getPublicData().getGender());
            dto.put("modelPath", model.getPublicData().getModelPath());
        }
        return dto;
    }
}
