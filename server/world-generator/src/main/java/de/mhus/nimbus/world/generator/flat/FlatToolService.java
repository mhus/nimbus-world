package de.mhus.nimbus.world.generator.flat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import dev.langchain4j.agent.tool.Tool;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for flat terrain tool operations.
 * Provides reusable functionality for manipulating, creating, and exporting flats.
 * Can be used by chat agents, AI agents, and other components.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FlatToolService {

    private final FlatManipulatorService flatManipulatorService;
    private final FlatCreateService flatCreateService;
    private final FlatExportService flatExportService;
    private final WFlatService wFlatService;
    private final ObjectMapper objectMapper;

    /**
     * Tool information record.
     */
    @Getter
    @Builder
    public static class FlatToolInfo {
        private final String name;
        private final String type;
        private final String title;
        private final String description;
        private final List<ParameterInfo> parameters;
        private final String exampleJson;
    }

    /**
     * Parameter information record.
     */
    @Getter
    @Builder
    public static class ParameterInfo {
        private final String name;
        private final String type;
        private final boolean required;
        private final String defaultValue;
        private final String description;
    }

    /**
     * Tool execution result.
     */
    @Getter
    @Builder
    public static class FlatToolResult {
        private final boolean success;
        private final String message;
        private final String flatId;
        private final int affectedColumns;
        private final String error;

        public static FlatToolResult success(String message) {
            return FlatToolResult.builder()
                    .success(true)
                    .message(message)
                    .build();
        }

        public static FlatToolResult success(String message, String flatId) {
            return FlatToolResult.builder()
                    .success(true)
                    .message(message)
                    .flatId(flatId)
                    .build();
        }

        public static FlatToolResult success(String message, int affectedColumns) {
            return FlatToolResult.builder()
                    .success(true)
                    .message(message)
                    .affectedColumns(affectedColumns)
                    .build();
        }

        public static FlatToolResult error(String error) {
            return FlatToolResult.builder()
                    .success(false)
                    .error(error)
                    .build();
        }
    }

    /**
     * Get list of all available tools.
     *
     * @return List of tool information
     */
    public List<FlatToolInfo> getAvailableTools() {
        List<FlatToolInfo> tools = new ArrayList<>();

        // Manipulator tool
        tools.add(FlatToolInfo.builder()
                .name("manipulator")
                .type("manipulator")
                .title("Flat Manipulator")
                .description("Execute a manipulator on an existing flat terrain. " +
                        "Modifies the flat in a specific region with manipulator-specific parameters.")
                .parameters(List.of(
                        ParameterInfo.builder()
                                .name("manipulator")
                                .type("string")
                                .required(true)
                                .description("Name of the manipulator to execute (e.g., 'raise', 'smooth', 'noise')")
                                .build(),
                        ParameterInfo.builder()
                                .name("flatId")
                                .type("string")
                                .required(true)
                                .description("ID of the flat to manipulate")
                                .build(),
                        ParameterInfo.builder()
                                .name("x")
                                .type("integer")
                                .required(false)
                                .defaultValue("0")
                                .description("X coordinate of the region start")
                                .build(),
                        ParameterInfo.builder()
                                .name("z")
                                .type("integer")
                                .required(false)
                                .defaultValue("0")
                                .description("Z coordinate of the region start")
                                .build(),
                        ParameterInfo.builder()
                                .name("sizeX")
                                .type("integer")
                                .required(false)
                                .defaultValue("entire flat width")
                                .description("Width of the region to manipulate")
                                .build(),
                        ParameterInfo.builder()
                                .name("sizeZ")
                                .type("integer")
                                .required(false)
                                .defaultValue("entire flat height")
                                .description("Height of the region to manipulate")
                                .build(),
                        ParameterInfo.builder()
                                .name("parameters")
                                .type("object")
                                .required(false)
                                .description("Manipulator-specific parameters as key-value map")
                                .build()
                ))
                .exampleJson("{\n" +
                        "  \"manipulator\": \"raise\",\n" +
                        "  \"flatId\": \"flat-1\",\n" +
                        "  \"x\": 0,\n" +
                        "  \"z\": 0,\n" +
                        "  \"sizeX\": 100,\n" +
                        "  \"sizeZ\": 100,\n" +
                        "  \"parameters\": {\n" +
                        "    \"height\": \"10\",\n" +
                        "    \"strength\": \"0.5\"\n" +
                        "  }\n" +
                        "}")
                .build());

        // Create tool
        tools.add(FlatToolInfo.builder()
                .name("create")
                .type("create")
                .title("Flat Creator")
                .description("Create a new flat terrain with specified dimensions. " +
                        "Ocean level is automatically loaded from world configuration.")
                .parameters(List.of(
                        ParameterInfo.builder()
                                .name("create")
                                .type("boolean")
                                .required(true)
                                .description("Must be true to create a flat")
                                .build(),
                        ParameterInfo.builder()
                                .name("worldId")
                                .type("string")
                                .required(false)
                                .defaultValue("from context")
                                .description("World ID")
                                .build(),
                        ParameterInfo.builder()
                                .name("layerDataId")
                                .type("string")
                                .required(true)
                                .description("Layer data ID")
                                .build(),
                        ParameterInfo.builder()
                                .name("flatId")
                                .type("string")
                                .required(true)
                                .description("Unique flat identifier")
                                .build(),
                        ParameterInfo.builder()
                                .name("sizeX")
                                .type("integer")
                                .required(true)
                                .description("Width of the flat in blocks (1-800)")
                                .build(),
                        ParameterInfo.builder()
                                .name("sizeZ")
                                .type("integer")
                                .required(true)
                                .description("Height of the flat in blocks (1-800)")
                                .build(),
                        ParameterInfo.builder()
                                .name("mountX")
                                .type("integer")
                                .required(false)
                                .defaultValue("0")
                                .description("Mount X position")
                                .build(),
                        ParameterInfo.builder()
                                .name("mountZ")
                                .type("integer")
                                .required(false)
                                .defaultValue("0")
                                .description("Mount Z position")
                                .build(),
                        ParameterInfo.builder()
                                .name("title")
                                .type("string")
                                .required(false)
                                .description("Display name for the flat")
                                .build(),
                        ParameterInfo.builder()
                                .name("description")
                                .type("string")
                                .required(false)
                                .description("Description of the flat")
                                .build()
                ))
                .exampleJson("{\n" +
                        "  \"create\": true,\n" +
                        "  \"worldId\": \"world-1\",\n" +
                        "  \"layerDataId\": \"layer-1\",\n" +
                        "  \"flatId\": \"my-terrain\",\n" +
                        "  \"sizeX\": 100,\n" +
                        "  \"sizeZ\": 100,\n" +
                        "  \"title\": \"My Flat\"\n" +
                        "}")
                .build());

        // Export tool
        tools.add(FlatToolInfo.builder()
                .name("export")
                .type("export")
                .title("Flat Exporter")
                .description("Export a flat terrain to a GROUND layer. " +
                        "Optionally smooth corners and optimize face visibility.")
                .parameters(List.of(
                        ParameterInfo.builder()
                                .name("export")
                                .type("boolean")
                                .required(true)
                                .description("Must be true to export a flat")
                                .build(),
                        ParameterInfo.builder()
                                .name("flatId")
                                .type("string")
                                .required(true)
                                .description("ID of the flat to export")
                                .build(),
                        ParameterInfo.builder()
                                .name("worldId")
                                .type("string")
                                .required(false)
                                .defaultValue("from context")
                                .description("World ID")
                                .build(),
                        ParameterInfo.builder()
                                .name("layerName")
                                .type("string")
                                .required(true)
                                .description("Name of the target GROUND layer")
                                .build(),
                        ParameterInfo.builder()
                                .name("smoothCorners")
                                .type("boolean")
                                .required(false)
                                .defaultValue("false")
                                .description("Smooth corners of top GROUND blocks")
                                .build(),
                        ParameterInfo.builder()
                                .name("optimizeFaces")
                                .type("boolean")
                                .required(false)
                                .defaultValue("false")
                                .description("Optimize face visibility to hide non-visible faces")
                                .build()
                ))
                .exampleJson("{\n" +
                        "  \"export\": true,\n" +
                        "  \"flatId\": \"flat-1\",\n" +
                        "  \"worldId\": \"world-1\",\n" +
                        "  \"layerName\": \"ground\",\n" +
                        "  \"smoothCorners\": true,\n" +
                        "  \"optimizeFaces\": true\n" +
                        "}")
                .build());

        return tools;
    }

    /**
     * Get information about a specific tool.
     *
     * @param toolName Name of the tool (manipulator, create, export)
     * @return Tool information, or empty if not found
     */
    public Optional<FlatToolInfo> getToolInfo(String toolName) {
        return getAvailableTools().stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst();
    }

    /**
     * Get description of a specific tool.
     *
     * @param toolName Name of the tool
     * @return Tool description, or empty if not found
     */
    public Optional<String> getToolDescription(String toolName) {
        return getToolInfo(toolName)
                .map(tool -> String.format("%s (%s): %s",
                        tool.getTitle(), tool.getName(), tool.getDescription()));
    }

    /**
     * Execute a manipulator command.
     *
     * @param params JSON parameters containing manipulator name, flatId, region, and parameters
     * @return Execution result
     */
    public FlatToolResult executeManipulator(ObjectNode params) {
        // Extract flatId (required)
        String flatId = params.has("flatId") ? params.get("flatId").asText() : null;

        if (flatId == null || flatId.isBlank()) {
            return FlatToolResult.error("flatId required for manipulator command");
        }

        // Load flat
        Optional<WFlat> flatOpt = wFlatService.findById(flatId);
        if (flatOpt.isEmpty()) {
            return FlatToolResult.error("Flat not found: " + flatId);
        }

        WFlat flat = flatOpt.get();

        // Extract manipulator name
        String manipulatorName = params.get("manipulator").asText();

        // Extract region (with defaults to entire flat)
        int x = params.has("x") ? params.get("x").asInt() : 0;
        int z = params.has("z") ? params.get("z").asInt() : 0;
        int sizeX = params.has("sizeX") ? params.get("sizeX").asInt() : flat.getSizeX();
        int sizeZ = params.has("sizeZ") ? params.get("sizeZ").asInt() : flat.getSizeZ();

        // Extract parameters (convert JsonNode to Map<String, String>)
        Map<String, String> parameters = new HashMap<>();
        if (params.has("parameters") && params.get("parameters").isObject()) {
            JsonNode paramsNode = params.get("parameters");
            paramsNode.fields().forEachRemaining(entry -> {
                String value = entry.getValue().isTextual()
                        ? entry.getValue().asText()
                        : entry.getValue().toString();
                parameters.put(entry.getKey(), value);
            });
        }

        log.info("Executing manipulator '{}' on flat '{}': region=({},{},{}x{}), params={}",
                manipulatorName, flatId, x, z, sizeX, sizeZ, parameters);

        // Execute manipulator
        try {
            flatManipulatorService.executeManipulator(manipulatorName, flat, x, z, sizeX, sizeZ, parameters);

            // Save updated flat
            wFlatService.update(flat);

            String message = String.format("Manipulator '%s' executed successfully on flat '%s' (region: %dx%d at %d,%d)",
                    manipulatorName, flatId, sizeX, sizeZ, x, z);

            return FlatToolResult.success(message);

        } catch (Exception e) {
            log.error("Manipulator execution failed", e);
            return FlatToolResult.error("Manipulator execution failed: " + e.getMessage());
        }
    }

    /**
     * Execute a create command.
     *
     * @param params JSON parameters containing world info, flat dimensions, and metadata
     * @param defaultWorldId Default world ID if not specified in params
     * @return Execution result with created flatId
     */
    public FlatToolResult executeCreate(ObjectNode params, String defaultWorldId) {
        // Extract required parameters
        String worldIdParam = params.has("worldId") ? params.get("worldId").asText() : defaultWorldId;
        String layerDataId = params.has("layerDataId") ? params.get("layerDataId").asText() : null;
        String flatId = params.has("flatId") ? params.get("flatId").asText() : null;
        Integer sizeX = params.has("sizeX") ? params.get("sizeX").asInt() : null;
        Integer sizeZ = params.has("sizeZ") ? params.get("sizeZ").asInt() : null;

        // Validate required parameters
        if (layerDataId == null || layerDataId.isBlank()) {
            return FlatToolResult.error("layerDataId required for create command");
        }
        if (flatId == null || flatId.isBlank()) {
            return FlatToolResult.error("flatId required for create command");
        }
        if (sizeX == null || sizeX <= 0) {
            return FlatToolResult.error("sizeX required and must be > 0 for create command");
        }
        if (sizeZ == null || sizeZ <= 0) {
            return FlatToolResult.error("sizeZ required and must be > 0 for create command");
        }

        // Extract optional parameters
        int mountX = params.has("mountX") ? params.get("mountX").asInt() : 0;
        int mountZ = params.has("mountZ") ? params.get("mountZ").asInt() : 0;
        String title = params.has("title") ? params.get("title").asText() : null;
        String description = params.has("description") ? params.get("description").asText() : null;

        log.info("Creating flat: worldId={}, layerDataId={}, flatId={}, size={}x{}, mount=({},{})",
                worldIdParam, layerDataId, flatId, sizeX, sizeZ, mountX, mountZ);

        // Execute create
        try {
            WFlat flat = flatCreateService.createFlat(
                    worldIdParam, layerDataId, flatId,
                    sizeX, sizeZ, mountX, mountZ,
                    title, description);

            String message = String.format("Flat '%s' created successfully: size=%dx%d, mount=(%d,%d), id=%s",
                    flatId, sizeX, sizeZ, mountX, mountZ, flat.getId());

            return FlatToolResult.success(message, flat.getId());

        } catch (Exception e) {
            log.error("Create flat failed", e);
            return FlatToolResult.error("Create flat failed: " + e.getMessage());
        }
    }

    /**
     * Execute an export command.
     *
     * @param params JSON parameters containing flatId, worldId, layerName, and export options
     * @param defaultWorldId Default world ID if not specified in params
     * @return Execution result with number of exported columns
     */
    public FlatToolResult executeExport(ObjectNode params, String defaultWorldId) {
        // Extract required parameters
        String flatId = params.has("flatId") ? params.get("flatId").asText() : null;
        String worldIdParam = params.has("worldId") ? params.get("worldId").asText() : defaultWorldId;
        String layerName = params.has("layerName") ? params.get("layerName").asText() : null;

        // Validate required parameters
        if (flatId == null || flatId.isBlank()) {
            return FlatToolResult.error("flatId required for export command");
        }
        if (layerName == null || layerName.isBlank()) {
            return FlatToolResult.error("layerName required for export command");
        }

        // Extract optional parameters
        boolean smoothCorners = params.has("smoothCorners") && params.get("smoothCorners").asBoolean();
        boolean optimizeFaces = params.has("optimizeFaces") && params.get("optimizeFaces").asBoolean();

        log.info("Exporting flat to layer: flatId={}, worldId={}, layerName={}, smoothCorners={}, optimizeFaces={}",
                flatId, worldIdParam, layerName, smoothCorners, optimizeFaces);

        // Execute export
        try {
            int exportedColumns = flatExportService.exportToLayer(
                    flatId, worldIdParam, layerName, smoothCorners, optimizeFaces);

            String message = String.format("Flat '%s' exported successfully to layer '%s': %d columns exported",
                    flatId, layerName, exportedColumns);

            return FlatToolResult.success(message, exportedColumns);

        } catch (Exception e) {
            log.error("Export flat failed", e);
            return FlatToolResult.error("Export flat failed: " + e.getMessage());
        }
    }

    /**
     * Get list of available manipulators from FlatManipulatorService.
     *
     * @return List of manipulator names
     */
    public List<String> getAvailableManipulators() {
        // This would require exposing manipulator names from FlatManipulatorService
        // For now, return empty list - can be implemented later
        log.warn("getAvailableManipulators() not yet fully implemented");
        return List.of();
    }

    // ========== AI Tool Methods (for langchain4j) ==========

    /**
     * Create a new flat terrain for AI agents.
     *
     * @param flatId Unique flat ID
     * @param worldId World ID
     * @param sizeX Size in X direction
     * @param sizeZ Size in Z direction
     * @param title Flat title
     * @param description Flat description
     * @return Execution result
     */
    @Tool("Create a new flat terrain. Provide flatId, worldId, sizeX, sizeZ, title, and description. Returns the created flatId.")
    public String executeCreate(
            String flatId,
            String worldId,
            int sizeX,
            int sizeZ,
            String title,
            String description) {

        log.info("AI Tool: executeCreate - flatId={}, worldId={}, sizeX={}, sizeZ={}",
                flatId, worldId, sizeX, sizeZ);

        // Build params
        ObjectNode params = objectMapper.createObjectNode();
        params.put("flatId", flatId);
        params.put("worldId", worldId);
        params.put("layerDataId", ""); // Empty for flats
        params.put("sizeX", sizeX);
        params.put("sizeZ", sizeZ);
        params.put("mountX", 0);
        params.put("mountZ", 0);
        params.put("title", title);
        params.put("description", description);

        // Execute
        FlatToolResult result = executeCreate(params, worldId);

        if (result.isSuccess()) {
            return String.format("SUCCESS: Flat '%s' created (%dx%d blocks)",
                result.getFlatId(), sizeX, sizeZ);
        } else {
            return String.format("ERROR: %s", result.getError());
        }
    }

    /**
     * Manipulate a flat terrain for AI agents.
     *
     * @param flatId Flat ID to manipulate
     * @param manipulatorName Manipulator name (e.g., "raise", "lower", "smooth")
     * @param parametersJson JSON parameters for manipulator
     * @return Execution result
     */
    @Tool("Manipulate an existing flat terrain. Provide flatId, manipulator name (raise/lower/smooth/plateau), and parameters as JSON. Returns affected column count.")
    public String executeManipulator(
            String flatId,
            String manipulatorName,
            String parametersJson) {

        log.info("AI Tool: executeManipulator - flatId={}, manipulator={}",
                flatId, manipulatorName);

        // Parse parameters
        ObjectNode params;
        try {
            params = (ObjectNode) objectMapper.readTree(parametersJson);
        } catch (Exception e) {
            return "ERROR: Invalid parameters JSON: " + e.getMessage();
        }

        // Add flatId and manipulatorName to params
        params.put("flatId", flatId);
        params.put("manipulatorName", manipulatorName);

        // Execute
        FlatToolResult result = executeManipulator(params);

        if (result.isSuccess()) {
            return String.format("SUCCESS: %s (Affected columns: %d)",
                result.getMessage(), result.getAffectedColumns());
        } else {
            return String.format("ERROR: %s", result.getError());
        }
    }

    /**
     * Export a flat terrain to a world layer for AI agents.
     *
     * @param flatId Flat ID to export
     * @param worldId World ID
     * @param layerName Layer name (usually "GROUND")
     * @param smoothCorners Whether to smooth corners
     * @param optimizeFaces Whether to optimize faces
     * @return Execution result
     */
    @Tool("Export a flat terrain to a world layer to activate it. Provide flatId, worldId, layerName (usually 'GROUND'), smoothCorners (true/false), and optimizeFaces (true/false). This MUST be called after create or manipulate to make changes visible.")
    public String executeExport(
            String flatId,
            String worldId,
            String layerName,
            boolean smoothCorners,
            boolean optimizeFaces) {

        log.info("AI Tool: executeExport - flatId={}, worldId={}, layerName={}",
                flatId, worldId, layerName);

        // Build params
        ObjectNode params = objectMapper.createObjectNode();
        params.put("flatId", flatId);
        params.put("worldId", worldId);
        params.put("layerName", layerName);
        params.put("smoothCorners", smoothCorners);
        params.put("optimizeFaces", optimizeFaces);

        // Execute
        FlatToolResult result = executeExport(params, worldId);

        if (result.isSuccess()) {
            return String.format("SUCCESS: %s", result.getMessage());
        } else {
            return String.format("ERROR: %s", result.getError());
        }
    }
}
