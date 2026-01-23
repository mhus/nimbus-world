package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.Builder;
import lombok.Data;

/**
 * Context for block manipulation operations.
 * Contains all necessary information for executing a block manipulator including
 * service reference, original parameters, and enriched parameter JSON.
 */
@Data
@Builder
public class ManipulatorContext {

    /**
     * Reference to the BlockManipulatorService for accessing default parameters
     * and other manipulators.
     */
    private BlockManipulatorService service;

    /**
     * World ID for session and position lookups.
     */
    private String worldId;

    /**
     * Session ID for accessing player position and WSession data.
     */
    private String sessionId;

    /**
     * Layer data ID for WEditCache operations.
     */
    private String layerDataId;

    /**
     * Layer name (from EditState.selectedLayer).
     * Used for ModelSelector autoSelectName format: layerDataId:layerName
     */
    private String layerName;

    /**
     * Model name for MODEL type layers (null for other layer types).
     */
    private String modelName;

    /**
     * Group ID for block grouping.
     */
    @Builder.Default
    private String groupId = null;

    /**
     * Original parameter string as provided by the user/chat.
     */
    private String originalParams;

    /**
     * Enriched parameters as JSON object.
     * Includes default parameters and transformed position values.
     * This is the main parameter source for manipulators.
     */
    private ObjectNode params;

    /**
     * Model selector for tracking generated blocks.
     * Automatically filled by EditCachePainter when blocks are painted.
     */
    private ModelSelector modelSelector;

    /**
     * Get a parameter value as String.
     *
     * @param key parameter key
     * @return parameter value as String, or null if not found
     */
    public String getParameter(String key) {
        if (params == null || !params.has(key)) return null;
        JsonNode node = params.get(key);
        return node.isTextual() ? node.asText() : node.toString();
    }

    /**
     * Get a parameter value as String with default value.
     *
     * @param key parameter key
     * @param defaultValue default value if parameter not found
     * @return parameter value as String, or defaultValue
     */
    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a parameter value as Integer.
     *
     * @param key parameter key
     * @return parameter value as Integer, or null if not found
     */
    public Integer getIntParameter(String key) {
        if (params == null || !params.has(key)) return null;
        JsonNode node = params.get(key);
        return node.isNumber() ? node.asInt() : null;
    }

    /**
     * Get a parameter value as Integer with default value.
     *
     * @param key parameter key
     * @param defaultValue default value if parameter not found
     * @return parameter value as Integer, or defaultValue
     */
    public Integer getIntParameter(String key, Integer defaultValue) {
        Integer value = getIntParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a parameter value as Double.
     *
     * @param key parameter key
     * @return parameter value as Double, or null if not found
     */
    public Double getDoubleParameter(String key) {
        if (params == null || !params.has(key)) return null;
        JsonNode node = params.get(key);
        return node.isNumber() ? node.asDouble() : null;
    }

    /**
     * Get a parameter value as Double with default value.
     *
     * @param key parameter key
     * @param defaultValue default value if parameter not found
     * @return parameter value as Double, or defaultValue
     */
    public Double getDoubleParameter(String key, Double defaultValue) {
        Double value = getDoubleParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a parameter value as Boolean.
     *
     * @param key parameter key
     * @return parameter value as Boolean, or null if not found
     */
    public Boolean getBooleanParameter(String key) {
        if (params == null || !params.has(key)) return null;
        JsonNode node = params.get(key);
        return node.isBoolean() ? node.asBoolean() : null;
    }

    /**
     * Get a parameter value as Boolean with default value.
     *
     * @param key parameter key
     * @param defaultValue default value if parameter not found
     * @return parameter value as Boolean, or defaultValue
     */
    public Boolean getBooleanParameter(String key, Boolean defaultValue) {
        Boolean value = getBooleanParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a parameter value as JsonNode.
     *
     * @param key parameter key
     * @return parameter value as JsonNode, or null if not found
     */
    public JsonNode getJsonParameter(String key) {
        if (params == null || !params.has(key)) return null;
        return params.get(key);
    }
}
