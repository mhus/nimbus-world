package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manipulator for setting default parameters in the BlockManipulatorService.
 * This allows setting common parameters (like blockType) that will be applied
 * to all subsequent manipulators unless explicitly overridden.
 *
 * Example usage:
 * <pre>
 * {
 *   "defaults": {
 *     "blockType": "n:s",
 *     "diffuse": 0.1
 *   }
 * }
 * </pre>
 *
 * All parameters passed to this manipulator will be stored as defaults.
 * Defaults are stored in the BlockManipulatorService and persist across
 * multiple manipulator executions until changed or cleared.
 */
@Component
@Slf4j
public class DefaultsBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "defaults";
    }

    @Override
    public String getTitle() {
        return "Set Default Parameters";
    }

    @Override
    public String getDescription() {
        return "Sets default parameters for all subsequent manipulators. " +
                "Parameters: any key-value pairs to use as defaults (e.g., blockType, diffuse, etc.). " +
                "Example: {\"defaults\": {\"blockType\": \"n:s\", \"diffuse\": 0.1}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        BlockManipulatorService service = context.getService();
        if (service == null) {
            throw new BlockManipulatorException("BlockManipulatorService not available in context");
        }

        ObjectNode params = context.getParams();
        if (params == null || params.isEmpty()) {
            // No parameters provided - just report current defaults
            Map<String, Object> currentDefaults = service.getDefaultParameters();
            if (currentDefaults.isEmpty()) {
                return ManipulatorResult.success("No default parameters are currently set.");
            }

            List<String> defaultsList = new ArrayList<>();
            for (Map.Entry<String, Object> entry : currentDefaults.entrySet()) {
                defaultsList.add(entry.getKey() + "=" + entry.getValue());
            }

            return ManipulatorResult.success(
                    "Current defaults: " + String.join(", ", defaultsList)
            );
        }

        // Set all parameters as defaults
        List<String> setParameters = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            // Convert JsonNode to appropriate Java type
            Object javaValue = convertJsonNodeToValue(value);
            service.setDefaultParameter(key, javaValue);

            setParameters.add(key + "=" + javaValue);
            log.debug("Set default parameter: {} = {}", key, javaValue);
        }

        String message = "Set " + setParameters.size() + " default parameter(s): " +
                String.join(", ", setParameters);

        log.info("Defaults updated: {}", message);

        // Return success without ModelSelector (no blocks generated)
        return ManipulatorResult.success(message);
    }

    /**
     * Convert JsonNode to appropriate Java value type.
     *
     * @param node the JsonNode to convert
     * @return Java value (String, Integer, Long, Double, Boolean, etc.)
     */
    private Object convertJsonNodeToValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNull()) {
            return null;
        } else if (node.isObject() || node.isArray()) {
            // Keep complex types as JsonNode
            return node;
        } else {
            // Fallback to text representation
            return node.asText();
        }
    }
}
