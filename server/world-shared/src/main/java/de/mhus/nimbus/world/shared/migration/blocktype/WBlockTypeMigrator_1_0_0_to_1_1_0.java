package de.mhus.nimbus.world.shared.migration.blocktype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for WBlockType from version 1.0.0 to 1.1.0.
 *
 * Changes in publicData (BlockType):
 * - initialStatus: number (0) -> string ("default")
 * - modifiers map key: number (0) -> string ("default")
 */
@Component
public class WBlockTypeMigrator_1_0_0_to_1_1_0 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "WBlockType";
    }

    @Override
    public SchemaVersion getFromVersion() {
        return SchemaVersion.create("1.0.0");
    }

    @Override
    public SchemaVersion getToVersion() {
        return SchemaVersion.create("1.1.0");
    }

    @Override
    public String migrate(String entityJson) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(entityJson);
        JsonNode publicData = root.get("publicData");
        if (publicData == null || !publicData.isObject()) {
            return entityJson;
        }
        ObjectNode pd = (ObjectNode) publicData;

        // Migrate initialStatus: number 0 -> string "default"
        JsonNode initialStatus = pd.get("initialStatus");
        if (initialStatus != null && initialStatus.isNumber()) {
            pd.put("initialStatus", "default");
        }

        // Migrate modifiers map key: number 0 -> string "default"
        JsonNode modifiers = pd.get("modifiers");
        if (modifiers != null && modifiers.isObject()) {
            ObjectNode modifiersObj = (ObjectNode) modifiers;
            JsonNode zeroEntry = modifiersObj.get("0");
            if (zeroEntry != null) {
                modifiersObj.remove("0");
                modifiersObj.set("default", zeroEntry);
            }
        }

        return MAPPER.writeValueAsString(root);
    }

}
