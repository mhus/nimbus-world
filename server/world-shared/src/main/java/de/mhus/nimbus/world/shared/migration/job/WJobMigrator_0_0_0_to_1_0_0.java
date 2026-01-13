package de.mhus.nimbus.world.shared.migration.job;

import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.springframework.stereotype.Component;

/**
 * Schema migrator for SAsset from version 0 to 1.0.0.
 * This is the initial migration that sets the schema version without making changes.
 */
@Component
public class WJobMigrator_0_0_0_to_1_0_0 implements SchemaMigrator {

    @Override
    public String getEntityType() {
        return "WJob";
    }

    @Override
    public SchemaVersion getFromVersion() {
        return SchemaVersion.create("0.0.0");
    }

    @Override
    public SchemaVersion getToVersion() {
        return SchemaVersion.create("1.0.0");
    }

    @Override
    public String migrate(String entityJson) throws Exception {
        // No changes needed, just version upgrade
        return entityJson;
    }
}
