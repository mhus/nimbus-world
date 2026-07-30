package de.mhus.nimbus.world.shared.migration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for WEntity from version 1.0.0 to 1.0.1.
 *
 * Changes:
 * - WEntity field: entityId -> name
 * - publicData (Entity): id -> name (unique technical identifier)
 * - publicData (Entity): name -> title (display name)
 *
 * Detection: If "entityId" or publicData.id exists, migration is needed.
 */
@Component
public class WEntityMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "WEntity";
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

        // Migrate root level: entityId -> name
        JsonNode entityIdNode = root.get("entityId");
        if (entityIdNode != null) {
            root.set("name", entityIdNode);
            root.remove("entityId");
        }

        JsonNode publicData = root.get("publicData");
        if (publicData == null || !publicData.isObject()) {
            return entityJson;
        }
        ObjectNode pd = (ObjectNode) publicData;

        // Check if migration is needed: old format has "id" or "_id" field
        // (MongoDB maps "id" to "_id" internally, but SchemaMigrationService
        // works on raw JSON where it may appear as either)
        JsonNode idNode = pd.get("id");
        if (idNode == null) {
            idNode = pd.get("_id");
        }
        if (idNode == null) {
            return entityJson;
        }

        // old "name" (display name) -> new "title"
        JsonNode nameNode = pd.get("name");
        if (nameNode != null) {
            pd.set("title", nameNode);
        }

        // old "id" (technical identifier) -> new "name"
        pd.set("name", idNode);

        // Remove old "id" / "_id" field
        pd.remove("id");
        pd.remove("_id");

        return MAPPER.writeValueAsString(root);
    }
}
