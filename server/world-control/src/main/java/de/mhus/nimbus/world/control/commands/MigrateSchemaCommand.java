package de.mhus.nimbus.world.control.commands;

import de.mhus.nimbus.shared.service.MongoRawDocumentService;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MigrateSchema command - migrates MongoDB entities to their latest schema version.
 *
 * <p>This command can migrate:</p>
 * <ul>
 *   <li>A single document by ID</li>
 *   <li>All documents in a collection</li>
 *   <li>All documents in a collection without _schema field</li>
 * </ul>
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Migrate single document
 * MigrateSchema users 507f1f77bcf86cd799439011 UUser 1.0.0
 *
 * // Migrate all documents in collection
 * MigrateSchema users * UUser 1.0.0
 *
 * // Migrate documents without schema field
 * MigrateSchema users no-schema UUser 1.0.0
 * </pre>
 *
 * <p>Arguments:</p>
 * <ol>
 *   <li>Collection name (e.g., "users", "worlds", "w_chunks")</li>
 *   <li>Document ID or "*" for all documents or "no-schema" for documents without _schema field</li>
 *   <li>Entity type (e.g., "UUser", "WWorld", "WChunk")</li>
 *   <li>Target schema version (e.g., "1.0.0")</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MigrateSchemaCommand implements Command {

    private final MongoRawDocumentService rawDocumentService;
    private final SchemaMigrationService migrationService;

    @Override
    public String getName() {
        return "MigrateSchema";
    }

    @Override
    public String getHelp() {
        return """
                MigrateSchema - Migrate MongoDB documents to target schema version

                Usage:
                  MigrateSchema <collection> <id|*|no-schema> <entityType> <targetVersion>

                Arguments:
                  collection    - MongoDB collection name (e.g., 'users', 'w_worlds', 'w_chunks')
                  id            - Document ID to migrate
                                  Use '*' to migrate all documents
                                  Use 'no-schema' to migrate documents without _schema field
                  entityType    - Entity class name (e.g., 'UUser', 'WWorld', 'WChunk')
                  targetVersion - Target schema version (e.g., '1.0.0', '2.0.0')

                Examples:
                  MigrateSchema users 507f1f77bcf86cd799439011 UUser 1.0.0
                    - Migrates single user document to version 1.0.0

                  MigrateSchema w_worlds * WWorld 2.0.0
                    - Migrates all worlds to version 2.0.0

                  MigrateSchema w_chunks no-schema WChunk 1.0.0
                    - Migrates all chunks without schema field to version 1.0.0

                Return codes:
                  0  - Success
                  -1 - Invalid arguments
                  -2 - Document not found
                  -3 - Update failed
                  -4 - Partial failure (some documents failed)
                  -10 - Migration error
                """;
    }

    @Override
    public boolean requiresSession() {
        return false; // Can be called remotely without session
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        // Validate arguments
        if (args.size() < 4) {
            return CommandResult.error(-1,
                    "Usage: MigrateSchema <collection> <id|*|no-schema> <entityType> <targetVersion>");
        }

        String collectionName = args.get(0);
        String idOrPattern = args.get(1);
        String entityType = args.get(2);
        SchemaVersion targetVersion = SchemaVersion.create(args.get(3));

        log.info("Starting schema migration for collection '{}', pattern '{}', entity '{}', target version '{}'",
                collectionName, idOrPattern, entityType, targetVersion);

        try {
            return switch (idOrPattern.toLowerCase()) {
                case "*" -> migrateAllDocuments(collectionName, entityType, targetVersion);
                case "no-schema" -> migrateDocumentsWithoutSchema(collectionName, entityType, targetVersion);
                default -> migrateSingleDocument(collectionName, idOrPattern, entityType, targetVersion);
            };

        } catch (Exception e) {
            log.error("Schema migration failed for collection '{}': {}",
                    collectionName, e.getMessage(), e);
            return CommandResult.error(-100, "Migration failed: " + e.getMessage());
        }
    }

    /**
     * Migrates a single document by ID.
     */
    private CommandResult migrateSingleDocument(String collectionName, String id,
                                                String entityType, SchemaVersion targetVersion) {
        log.info("Migrating single document: {} in {}", id, collectionName);

        // Load document as JSON
        String documentJson = rawDocumentService.findDocumentById(collectionName, id);
        if (documentJson == null) {
            return CommandResult.error(-2, "Document not found: " + id);
        }

        try {
            // Extract current version from document
            SchemaVersion currentVersion = extractSchemaVersion(documentJson);

            // Migrate document
            String migratedJson = migrationService.migrate(documentJson, entityType, targetVersion, currentVersion);

            // Save migrated document back to MongoDB
            boolean updated = rawDocumentService.replaceDocument(collectionName, id, migratedJson);

            if (updated) {
                log.info("Successfully migrated document {} to version {}", id, targetVersion);
                return CommandResult.success("Document migrated successfully to version " + targetVersion);
            } else {
                return CommandResult.error(-3, "Failed to update document: " + id);
            }

        } catch (SchemaMigrationService.MigrationException e) {
            log.error("Migration failed for document {}: {}", id, e.getMessage());
            return CommandResult.error(-10, "Migration failed: " + e.getMessage());
        }
    }

    /**
     * Migrates all documents in a collection.
     */
    private CommandResult migrateAllDocuments(String collectionName,
                                              String entityType, SchemaVersion targetVersion) {
        log.info("Migrating all documents in collection: {}", collectionName);

        // Load all documents
        List<String> documents = rawDocumentService.findAllDocuments(collectionName);
        if (documents.isEmpty()) {
            return CommandResult.success("No documents found in collection");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        // Migrate each document
        for (String documentJson : documents) {
            try {
                String documentId = rawDocumentService.extractDocumentId(documentJson);

                // Check if migration is needed
                if (!needsMigration(documentJson, targetVersion)) {
                    skippedCount.incrementAndGet();
                    log.debug("Document {} already at version {}, skipping", documentId, targetVersion);
                    continue;
                }

                // Extract current version from document
                SchemaVersion currentVersion = extractSchemaVersion(documentJson);

                // Migrate document
                String migratedJson = migrationService.migrate(documentJson, entityType, targetVersion, currentVersion);

                // Save migrated document
                boolean updated = rawDocumentService.replaceDocument(collectionName, documentId, migratedJson);

                if (updated) {
                    successCount.incrementAndGet();
                    log.debug("Migrated document {} to version {}", documentId, targetVersion);
                } else {
                    failureCount.incrementAndGet();
                    log.warn("Failed to update document {}", documentId);
                }

            } catch (SchemaMigrationService.MigrationException e) {
                failureCount.incrementAndGet();
                log.error("Migration failed for document: {}", e.getMessage());
            }
        }

        String message = String.format(
                "Migration completed: %d succeeded, %d failed, %d skipped (total: %d)",
                successCount.get(), failureCount.get(), skippedCount.get(), documents.size());

        log.info(message);

        if (failureCount.get() > 0) {
            return CommandResult.error(-4, message);
        } else {
            return CommandResult.success(message);
        }
    }

    /**
     * Migrates documents without _schema field.
     */
    private CommandResult migrateDocumentsWithoutSchema(String collectionName,
                                                        String entityType, SchemaVersion targetVersion) {
        log.info("Migrating documents without _schema field in collection: {}", collectionName);

        // Load documents without _schema field
        List<String> documents = rawDocumentService.findDocumentsWithoutField(collectionName, "_schema");
        if (documents.isEmpty()) {
            return CommandResult.success("No documents without _schema field found");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Migrate each document
        for (String documentJson : documents) {
            try {
                String documentId = rawDocumentService.extractDocumentId(documentJson);

                // Migrate document (starting from version "0")
                SchemaVersion currentVersion = SchemaVersion.create("0"); // Documents without schema start at version 0
                String migratedJson = migrationService.migrate(documentJson, entityType, targetVersion, currentVersion);

                // Save migrated document
                boolean updated = rawDocumentService.replaceDocument(collectionName, documentId, migratedJson);

                if (updated) {
                    successCount.incrementAndGet();
                    log.debug("Migrated document {} from no-schema to version {}", documentId, targetVersion);
                } else {
                    failureCount.incrementAndGet();
                    log.warn("Failed to update document {}", documentId);
                }

            } catch (SchemaMigrationService.MigrationException e) {
                failureCount.incrementAndGet();
                log.error("Migration failed for document: {}", e.getMessage());
            }
        }

        String message = String.format(
                "Migration completed: %d succeeded, %d failed (total: %d documents without schema)",
                successCount.get(), failureCount.get(), documents.size());

        log.info(message);

        if (failureCount.get() > 0) {
            return CommandResult.error(-5, message);
        } else {
            return CommandResult.success(message);
        }
    }

    /**
     * Checks if a document needs migration based on its current schema version.
     */
    private boolean needsMigration(String documentJson, SchemaVersion targetVersion) {
        SchemaVersion currentVersion = extractSchemaVersion(documentJson);
        return !currentVersion.equals(targetVersion);
    }

    /**
     * Extracts the schema version from a JSON document.
     */
    private SchemaVersion extractSchemaVersion(String documentJson) {
        // Look for _schema field
        int schemaIndex = documentJson.indexOf("\"_schema\"");
        if (schemaIndex == -1) {
            return SchemaVersion.create("0"); // Default to version 0 if no schema field
        }

        int valueStart = documentJson.indexOf("\"", schemaIndex + 10);
        if (valueStart == -1) {
            return SchemaVersion.create("0");
        }

        int valueEnd = documentJson.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return SchemaVersion.create("0");
        }

        String versionString = documentJson.substring(valueStart + 1, valueEnd);
        try {
            return SchemaVersion.create(versionString);
        } catch (Exception e) {
            log.warn("Invalid schema version '{}' in document, defaulting to 0", versionString);
            return SchemaVersion.create("0");
        }
    }
}
