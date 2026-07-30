package de.mhus.nimbus.world.shared.migration.user;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for RUser from version 1.0.0 to 1.0.1.
 *
 * Changes:
 * - RUser field: username -> name
 * - publicData (PlayerUser): userId -> name
 */
@Component
public class RUserMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "RUser";
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

        // Migrate root level: username -> name
        JsonNode usernameNode = root.get("username");
        if (usernameNode != null) {
            root.set("name", usernameNode);
            root.remove("username");
        }

        // Migrate publicData: userId -> name
        JsonNode publicData = root.get("publicData");
        if (publicData != null && publicData.isObject()) {
            ObjectNode pd = (ObjectNode) publicData;
            JsonNode userIdNode = pd.get("userId");
            if (userIdNode != null) {
                pd.set("name", userIdNode);
                pd.remove("userId");
            }
        }

        return MAPPER.writeValueAsString(root);
    }
}
