package de.mhus.nimbus.world.shared.migration.chunk;

import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WChunkStorageMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private final EngineMapper mapper;

    @Override
    public String getEntityType() {
        return "WChunkStorage";
    }

    @Override
    public SchemaVersion getFromVersion() {
        return SchemaVersion.create("1.0.0");
    }

    @Override
    public SchemaVersion getToVersion() {
        return SchemaVersion.create("1.0.1");
    }

    @Override
    public String migrate(String entityJson) throws Exception {
        if (!Strings.CS.containsAny(
                entityJson,
                "rotationX"
                ,"rotationY"
                ,"textures"
        )) return entityJson;

        var json = mapper.readTree(entityJson);

        var blocks = json.get("blocks"); // array of blocks
        if (blocks != null && blocks.isArray()) {
            for (var blockNode : blocks) {
                processBlockModifiers(blockNode, mapper);
            }
        }
        return mapper.writeValueAsString(json);
    }

    /**
     * Verarbeitet die Modifikatoren eines Block-Knotens
     */
    public static void processBlockModifiers(com.fasterxml.jackson.databind.JsonNode blockNode, EngineMapper mapper) {
        var modifiersMap = blockNode.get("modifiers"); // object status
        // every value is modifier
        if (modifiersMap != null && modifiersMap.isObject()) {
            var fields = modifiersMap.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var modifierValue = entry.getValue();
                var visibility = modifierValue.get("visibility");
                if (visibility != null && visibility.isObject()) {
                    processVisibilityModifiers(blockNode, visibility, mapper);
                }
            }
        }
    }

    /**
     * Verarbeitet die Sichtbarkeits-Modifikatoren
     */
    public static void processVisibilityModifiers(com.fasterxml.jackson.databind.JsonNode blockNode,
                                                   com.fasterxml.jackson.databind.JsonNode visibility,
                                                   EngineMapper mapper) {
        processRotationFix(blockNode, visibility, mapper);
        processTextureFix(visibility);
    }

    /**
     * Behandelt die Rotation-Korrektur: verschiebt rotationX/Y vom visibility-Modifier zum Block
     */
    private static void processRotationFix(com.fasterxml.jackson.databind.JsonNode blockNode,
                                          com.fasterxml.jackson.databind.JsonNode visibility,
                                          EngineMapper mapper) {
        double rotationX = visibility.get("rotationX") != null ? visibility.get("rotationX").asDouble(0) : 0;
        double rotationY = visibility.get("rotationY") != null ? visibility.get("rotationY").asDouble(0) : 0;

        if (rotationX != 0 || rotationY != 0) {
            // Apply rotation to block, remove from modifier
            var rotationNode = mapper.createObjectNode();
            rotationNode.put("x", rotationX);
            rotationNode.put("y", rotationY);
            ((com.fasterxml.jackson.databind.node.ObjectNode) blockNode).set("rotation", rotationNode);
        }

        // Remove rotation values from visibility
        ((com.fasterxml.jackson.databind.node.ObjectNode) visibility).remove("rotationX");
        ((com.fasterxml.jackson.databind.node.ObjectNode) visibility).remove("rotationY");
    }

    /**
     * Behandelt die Textur-Korrektur: fügt 'w/' Präfix zu Textur-Pfaden hinzu
     */
    private static void processTextureFix(com.fasterxml.jackson.databind.JsonNode visibility) {
        var texturesMap = visibility.get("textures");
        if (texturesMap != null && texturesMap.isObject()) {
            var textureFields = texturesMap.fields();
            while (textureFields.hasNext()) {
                var textureEntry = textureFields.next();
                var textureValue = textureEntry.getValue();

                if (textureValue.isTextual()) {
                    processTextualTexture(texturesMap, textureEntry);
                } else if (textureValue.isObject()) {
                    processObjectTexture(textureValue);
                }
            }
        }
    }

    /**
     * Verarbeitet textuelle Textur-Werte
     */
    private static void processTextualTexture(com.fasterxml.jackson.databind.JsonNode texturesMap,
                                             java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> textureEntry) {
        String textureStr = textureEntry.getValue().asText();
        if (!textureStr.startsWith("w/")) {
            // Add prefix 'w/'
            ((com.fasterxml.jackson.databind.node.ObjectNode) texturesMap).put(textureEntry.getKey(), "w/" + textureStr);
        }
    }

    /**
     * Verarbeitet Objekt-Textur-Werte (mit path-Eigenschaft)
     */
    private static void processObjectTexture(com.fasterxml.jackson.databind.JsonNode textureValue) {
        var pathNode = textureValue.get("path");
        if (pathNode != null && pathNode.isTextual()) {
            String pathStr = pathNode.asText();
            if (!pathStr.startsWith("w/")) {
                // Add prefix 'w/'
                ((com.fasterxml.jackson.databind.node.ObjectNode) textureValue).put("path", "w/" + pathStr);
            }
        }
    }
}
