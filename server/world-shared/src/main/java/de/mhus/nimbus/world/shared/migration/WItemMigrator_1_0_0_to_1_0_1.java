package de.mhus.nimbus.world.shared.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for WItem from version 1.0.0 to 1.0.1.
 *
 * Changes:
 * - WItem field: itemId -> name
 * (publicData already uses 'name', no change needed there)
 */
@Component
public class WItemMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getEntityType() {
        return "WItem";
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

        // Migrate root level: itemId -> name
        JsonNode itemIdNode = root.get("itemId");
        if (itemIdNode != null) {
            root.set("name", itemIdNode);
            root.remove("itemId");
        }

        return MAPPER.writeValueAsString(root);
    }
}
