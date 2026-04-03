package de.mhus.nimbus.world.shared.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for WEntityModel from version 1.0.0 to 1.0.1.
 *
 * Changes:
 * - WEntityModel field: modelId -> name
 * - publicData (EntityModel): id/_id -> name
 *
 * Detection: If "modelId" field exists at root level, migration is needed.
 */
@Component
public class WEntityModelMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "WEntityModel";
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
        ObjectNode root = (ObjectNode) MAPPER.readTree(entityJson);

        // Migrate root level: modelId -> name
        JsonNode modelIdNode = root.get("modelId");
        if (modelIdNode != null) {
            root.set("name", modelIdNode);
            root.remove("modelId");
        }

        // Migrate publicData: id/_id -> name
        JsonNode publicData = root.get("publicData");
        if (publicData != null && publicData.isObject()) {
            ObjectNode pd = (ObjectNode) publicData;
            JsonNode idNode = pd.get("id");
            if (idNode == null) {
                idNode = pd.get("_id");
            }
            if (idNode != null) {
                pd.set("name", idNode);
                pd.remove("id");
                pd.remove("_id");
            }
        }

        return MAPPER.writeValueAsString(root);
    }
}
