package de.mhus.nimbus.world.shared.migration.blocktype;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for WBlockType from version 1.1.0 to 1.2.0.
 *
 * Changes:
 * - WBlockType field: blockId -> name
 * - publicData (BlockType): id -> name
 *
 * Detection: If "blockId" field exists at root level, migration is needed.
 */
@Component
public class WBlockTypeMigrator_1_1_0_to_1_2_0 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "WBlockType";
    }

    @Override
    public SchemaVersion getFromVersion() {
        return SchemaVersion.create("1.1.0");
    }

    @Override
    public SchemaVersion getToVersion() {
        return SchemaVersion.create("1.2.0");
    }

    @Override
    public String migrate(String entityJson) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(entityJson);

        // Migrate root level: blockId -> name
        JsonNode blockIdNode = root.get("blockId");
        if (blockIdNode != null) {
            root.set("name", blockIdNode);
            root.remove("blockId");
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
