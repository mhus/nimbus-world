package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for block manipulation tool operations.
 * Provides reusable functionality for executing block manipulators.
 * Can be used by chat agents, AI agents, and other components.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlockToolService {

    private final BlockManipulatorService blockManipulatorService;

    /**
     * Tool information record.
     */
    @Getter
    @Builder
    public static class BlockToolInfo {
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
    public static class BlockToolResult {
        private final boolean success;
        private final String message;
        private final ModelSelector modelSelector;
        private final int blockCount;
        private final String error;

        public static BlockToolResult success(String message, ModelSelector modelSelector) {
            return BlockToolResult.builder()
                    .success(true)
                    .message(message)
                    .modelSelector(modelSelector)
                    .blockCount(modelSelector != null ? modelSelector.getBlockCount() : 0)
                    .build();
        }

        public static BlockToolResult success(String message) {
            return BlockToolResult.builder()
                    .success(true)
                    .message(message)
                    .blockCount(0)
                    .build();
        }

        public static BlockToolResult error(String error) {
            return BlockToolResult.builder()
                    .success(false)
                    .error(error)
                    .blockCount(0)
                    .build();
        }
    }

    /**
     * Get list of all available tools.
     *
     * @return List of tool information
     */
    public List<BlockToolInfo> getAvailableTools() {
        List<BlockToolInfo> tools = new ArrayList<>();

        // Block manipulator tool (generic - covers all manipulator types)
        tools.add(BlockToolInfo.builder()
                .name("manipulator")
                .type("block-manipulator")
                .title("Block Manipulator")
                .description("Execute a block manipulator to generate or modify blocks in the world. " +
                        "Supports various manipulator types (plateau, cube, sphere, line, pyramid, etc.). " +
                        "Each manipulator has its own specific parameters. " +
                        "Returns a ModelSelector highlighting the affected blocks.")
                .parameters(List.of(
                        ParameterInfo.builder()
                                .name("manipulatorName")
                                .type("string")
                                .required(true)
                                .description("Name of the manipulator to execute (e.g., 'plateau', 'cube', 'sphere', 'line')")
                                .build(),
                        ParameterInfo.builder()
                                .name("params")
                                .type("object")
                                .required(true)
                                .description("Manipulator-specific parameters (varies by manipulator type)")
                                .build(),
                        ParameterInfo.builder()
                                .name("sessionId")
                                .type("string")
                                .required(false)
                                .description("Session ID for loading EditState context (layerDataId, layerName, modelName, groupId)")
                                .build(),
                        ParameterInfo.builder()
                                .name("worldId")
                                .type("string")
                                .required(true)
                                .description("World ID where blocks will be placed")
                                .build(),
                        ParameterInfo.builder()
                                .name("layerDataId")
                                .type("string")
                                .required(false)
                                .defaultValue("from EditState")
                                .description("Layer data ID (can be loaded from EditState)")
                                .build(),
                        ParameterInfo.builder()
                                .name("layerName")
                                .type("string")
                                .required(false)
                                .defaultValue("from EditState")
                                .description("Layer name (can be loaded from EditState)")
                                .build(),
                        ParameterInfo.builder()
                                .name("modelName")
                                .type("string")
                                .required(false)
                                .defaultValue("from EditState")
                                .description("Model name for MODEL layers (can be loaded from EditState)")
                                .build(),
                        ParameterInfo.builder()
                                .name("groupId")
                                .type("integer")
                                .required(false)
                                .defaultValue("0 or from EditState")
                                .description("Group ID (can be loaded from EditState)")
                                .build()
                ))
                .exampleJson("{\n" +
                        "  \"plateau\": {\n" +
                        "    \"transform\": \"position,forward\",\n" +
                        "    \"width\": 10,\n" +
                        "    \"depth\": 5,\n" +
                        "    \"height\": 3\n" +
                        "  }\n" +
                        "}\n" +
                        "or with defaults:\n" +
                        "{\n" +
                        "  \"defaults\": {\n" +
                        "    \"blockType\": \"n:s\"\n" +
                        "  }\n" +
                        "}")
                .build());

        return tools;
    }

    /**
     * Get information about a specific tool.
     *
     * @param toolName Name of the tool (currently only "manipulator")
     * @return Tool information, or empty if not found
     */
    public Optional<BlockToolInfo> getToolInfo(String toolName) {
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
     * Execute a block manipulator.
     *
     * @param manipulatorName Name of the manipulator to execute
     * @param context ManipulatorContext with world info, session, layer data, and parameters
     * @return Execution result with ModelSelector if successful
     */
    public BlockToolResult executeManipulator(String manipulatorName, ManipulatorContext context) {
        log.info("Executing block manipulator '{}': worldId={}, sessionId={}, layerDataId={}, layerName={}",
                manipulatorName, context.getWorldId(), context.getSessionId(),
                context.getLayerDataId(), context.getLayerName());

        // Execute manipulator
        ManipulatorResult result;
        try {
            result = blockManipulatorService.execute(manipulatorName, context);
        } catch (Exception e) {
            log.error("Manipulator '{}' execution failed", manipulatorName, e);
            return BlockToolResult.error("Manipulator execution failed: " + e.getMessage());
        }

        // Check result
        if (!result.isSuccess()) {
            return BlockToolResult.error(result.getMessage());
        }

        // Success
        ModelSelector modelSelector = result.getModelSelector();
        int blockCount = modelSelector != null ? modelSelector.getBlockCount() : 0;

        log.info("Manipulator '{}' executed successfully: {} blocks generated",
                manipulatorName, blockCount);

        return BlockToolResult.success(result.getMessage(), modelSelector);
    }

    /**
     * Get list of available manipulators from BlockManipulatorService.
     *
     * @return List of manipulator names
     */
    public List<String> getAvailableManipulators() {
        return blockManipulatorService.getManipulatorNames();
    }

    /**
     * Get information about a specific manipulator.
     *
     * @param manipulatorName Name of the manipulator
     * @return Manipulator information (name, title, description), or empty if not found
     */
    public Optional<ManipulatorInfo> getManipulatorInfo(String manipulatorName) {
        BlockManipulator manipulator = blockManipulatorService.getManipulator(manipulatorName);
        if (manipulator == null) {
            return Optional.empty();
        }
        return Optional.of(ManipulatorInfo.builder()
                .name(manipulator.getName())
                .title(manipulator.getTitle())
                .description(manipulator.getDescription())
                .build());
    }

    /**
     * Manipulator information record.
     */
    @Getter
    @Builder
    public static class ManipulatorInfo {
        private final String name;
        private final String title;
        private final String description;
    }
}
