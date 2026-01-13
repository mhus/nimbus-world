package de.mhus.nimbus.shared.persistence;

import de.mhus.nimbus.shared.types.SchemaVersion;

/**
 * Interface for schema migration implementations.
 * Each migrator handles the migration of an entity from one specific version to the next.
 *
 * <p>Migrators should be registered as Spring beans and will be automatically
 * discovered by the {@link de.mhus.nimbus.shared.service.SchemaMigrationService}.</p>
 *
 * <p>Example implementation:</p>
 * <pre>
 * @Component
 * public class UUserMigrator_1_0_to_1_1 implements SchemaMigrator {
 *     public String getEntityType() {
 *         return "UUser";
 *     }
 *
 *     public String getFromVersion() {
 *         return "1.0.0";
 *     }
 *
 *     public String getToVersion() {
 *         return "1.1.0";
 *     }
 *
 *     public String migrate(String entityJson) throws Exception {
 *         // Parse, modify, and return JSON
 *         return modifiedJson;
 *     }
 * }
 * </pre>
 */
public interface SchemaMigrator extends Comparable<SchemaMigrator> {

    /**
     * Returns the entity type this migrator handles.
     * Should match the simple class name of the entity (e.g., "UUser", "WWorld").
     *
     * @return the entity type name
     */
    String getEntityType();

    /**
     * Returns the source version this migrator migrates from.
     * Use "0" for entities without a schema version.
     *
     * @return the source schema version (e.g., "1.0.0" or "0")
     */
    SchemaVersion getFromVersion();

    /**
     * Returns the target version this migrator migrates to.
     *
     * @return the target schema version (e.g., "1.1.0")
     */
    SchemaVersion getToVersion();

    /**
     * Performs the migration from {@link #getFromVersion()} to {@link #getToVersion()}.
     * The entity is provided as a JSON string representing the MongoDB document.
     *
     * <p>The migrator should:</p>
     * <ul>
     *   <li>Parse the JSON string</li>
     *   <li>Apply necessary transformations</li>
     *   <li>Return the modified JSON string</li>
     * </ul>
     *
     * <p>Note: The {@code _schema} field will be automatically updated by the migration service,
     * so migrators should not modify it.</p>
     *
     * @param entityJson the entity as a JSON string (MongoDB document representation)
     * @return the migrated entity as a JSON string
     * @throws Exception if migration fails
     */
    String migrate(String entityJson) throws Exception;

    /**
     * Stream-based migration for storage objects.
     * Default implementation converts stream to string, migrates using {@link #migrate(String)},
     * and converts back to stream. This works for text-based data (JSON).
     *
     * <p>Override this method for true binary migration if needed (e.g., Protobuf upgrades).</p>
     *
     * @param input the input stream with data to migrate
     * @return the migrated data as input stream
     * @throws Exception if migration fails
     */
    default java.io.InputStream migrateStream(java.io.InputStream input) throws Exception {
        String json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String migrated = migrate(json);
        return new java.io.ByteArrayInputStream(migrated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    default int compareTo(SchemaMigrator other) {
        int entityComp = this.getEntityType().compareTo(other.getEntityType());
        if (entityComp != 0) return entityComp;
        return this.getFromVersion().compareTo(other.getFromVersion());
    }

}
