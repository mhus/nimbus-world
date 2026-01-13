package de.mhus.nimbus.shared.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for importing MongoDB collections from JSON files with schema migration support.
 *
 * <p>This service imports entities from JSON Lines files, automatically migrating them
 * to the latest schema version before saving them to the database.</p>
 *
 * <p>Import process:</p>
 * <ol>
 *   <li>Read entity from JSON Lines file (one entity per line)</li>
 *   <li>Detect entity type and current schema version</li>
 *   <li>Migrate entity to target version using {@link SchemaMigrationService}</li>
 *   <li>Parse migrated JSON into entity object</li>
 *   <li>Save entity to MongoDB collection</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>
 * ImportResult result = importService.importCollection("w_entities", WEntity.class, "WEntity", "1.0.0", inputPath);
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final SchemaMigrationService schemaMigrationService;

    /**
     * Imports entities from a JSON Lines file into a collection with schema migration.
     * Entity type is extracted from _class field in JSON, migrates to latest available version.
     *
     * @param collectionName the name of the MongoDB collection to import into
     * @param inputFile      the path to the input file (JSON Lines format)
     * @return ImportResult containing statistics about the import
     * @throws IOException if file operations fail
     */
    public ImportResult importCollection(
            String collectionName,
            Path inputFile) throws IOException {
        return importCollection(collectionName, inputFile, null, ImportMode.SKIP);
    }

    /**
     * Imports entities from a JSON Lines file into a collection with schema migration and optional worldId filter.
     * Entity type is extracted from _class field in JSON, migrates to latest available version.
     *
     * @param collectionName the name of the MongoDB collection to import into
     * @param inputFile      the path to the input file (JSON Lines format)
     * @param worldId        optional worldId filter (null or "*" for all worlds)
     * @return ImportResult containing statistics about the import
     * @throws IOException if file operations fail
     */
    public ImportResult importCollection(
            String collectionName,
            Path inputFile,
            String worldId) throws IOException {
        return importCollection(collectionName, inputFile, worldId, ImportMode.SKIP);
    }

    /**
     * Imports entities from a JSON Lines file into a collection with schema migration, worldId filter, and import mode.
     * Imports data directly as MongoDB Documents to preserve all fields including _schema.
     * Entity type is automatically extracted from _class field, migrates to latest available version.
     *
     * @param collectionName the name of the MongoDB collection to import into
     * @param inputFile      the path to the input file (JSON Lines format)
     * @param worldId        optional worldId filter (null or "*" for all worlds)
     * @param importMode     import mode for handling existing entities (SKIP or OVERWRITE)
     * @return ImportResult containing statistics about the import
     * @throws IOException if file operations fail
     */
    public ImportResult importCollection(
            String collectionName,
            Path inputFile,
            String worldId,
            ImportMode importMode) throws IOException {

        log.info("Starting import of collection '{}' from file: {} (worldId: {}, mode: {})",
                collectionName, inputFile, worldId == null || "*".equals(worldId) ? "all" : worldId, importMode);

        if (!Files.exists(inputFile)) {
            throw new IOException("Import file not found: " + inputFile);
        }

        long startTime = System.currentTimeMillis();
        int totalCount = 0;
        int successCount = 0;
        int migrationCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        int skippedExistingCount = 0;

        boolean filterByWorldId = worldId != null && !"*".equals(worldId);

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile.toFile()))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                totalCount++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Filter by worldId if specified
                    if (filterByWorldId && !matchesWorldId(line, worldId)) {
                        skippedCount++;
                        continue;
                    }

                    // Extract entity type from _class field
                    String entityType = extractEntityTypeFromClass(line);
                    if (entityType == null) {
                        log.warn("No _class field found at line {}, skipping entity", lineNumber);
                        skippedCount++;
                        continue;
                    }

                    // Migrate entity JSON to latest available version
                    String migratedJson;
                    try {
                        migratedJson = schemaMigrationService.migrateToLatest(line, entityType);
                        if (!migratedJson.equals(line)) {
                            migrationCount++;
                        }
                    } catch (SchemaMigrationService.MigrationException e) {
                        log.warn("Migration failed for entity at line {} (using original): {}", lineNumber, e.getMessage());
                        migratedJson = line;
                    }

                    // Check if entity already exists
                    String entityId = extractEntityId(migratedJson);
                    boolean exists = entityId != null && entityExists(entityId, collectionName);

                    if (exists && importMode == ImportMode.SKIP) {
                        // Skip existing entity
                        skippedExistingCount++;
                        if (skippedExistingCount % 1000 == 0) {
                            log.debug("Skipped {} existing entities", skippedExistingCount);
                        }
                        continue;
                    }

                    // Save migrated JSON directly as Document to preserve _schema field
                    Document document = Document.parse(migratedJson);
                    mongoTemplate.save(document, collectionName);
                    successCount++;

                    if (successCount % 1000 == 0) {
                        log.debug("Imported {} / {} entities", successCount, totalCount);
                    }

                } catch (Exception e) {
                    log.error("Failed to import entity at line {}: {}", lineNumber, e.getMessage(), e);
                    errorCount++;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        ImportResult result = ImportResult.builder()
                .collectionName(collectionName)
                .totalCount(totalCount)
                .successCount(successCount)
                .migrationCount(migrationCount)
                .skippedCount(skippedCount)
                .skippedExistingCount(skippedExistingCount)
                .errorCount(errorCount)
                .durationMs(duration)
                .inputFile(inputFile.toString())
                .build();

        log.info("Import completed: {} - {} entities imported, {} migrated, {} skipped (filter), {} skipped (existing), {} errors in {} ms",
                collectionName, successCount, migrationCount, skippedCount, skippedExistingCount, errorCount, duration);

        return result;
    }

    /**
     * Extracts the entity type name from the _class field in JSON.
     * Format: "\"_class\":\"de.mhus.nimbus.world.shared.world.WEntity\"" -> "WEntity"
     *
     * @param entityJson the entity as JSON string
     * @return the simple entity type name, or null if not found
     */
    private String extractEntityTypeFromClass(String entityJson) {
        // Simple JSON parsing to extract _class field
        // Format: "\"_class\":\"de.mhus.nimbus.world.shared.world.WEntity\""
        int classIndex = entityJson.indexOf("\"_class\"");
        if (classIndex == -1) {
            return null;
        }

        int valueStart = entityJson.indexOf("\"", classIndex + 10);
        if (valueStart == -1) {
            return null;
        }

        int valueEnd = entityJson.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return null;
        }

        String fullClassName = entityJson.substring(valueStart + 1, valueEnd);

        // Extract simple class name from full package path
        // "de.mhus.nimbus.world.shared.world.WEntity" -> "WEntity"
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot != -1 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * Extracts the entity ID from a JSON string.
     *
     * @param entityJson the entity as JSON string
     * @return the entity ID, or null if not found
     */
    private String extractEntityId(String entityJson) {
        // Simple JSON parsing to extract id field
        // Format: "\"id\":\"123456\""
        int idIndex = entityJson.indexOf("\"id\"");
        if (idIndex == -1) {
            return null;
        }

        int valueStart = entityJson.indexOf("\"", idIndex + 5);
        if (valueStart == -1) {
            return null;
        }

        int valueEnd = entityJson.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return null;
        }

        return entityJson.substring(valueStart + 1, valueEnd);
    }

    /**
     * Checks if an entity with the given ID exists in the collection.
     *
     * @param entityId       the entity ID
     * @param collectionName the collection name
     * @return true if entity exists
     */
    private boolean entityExists(String entityId, String collectionName) {
        return mongoTemplate.exists(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("id").is(entityId)
                ),
                collectionName
        );
    }

    /**
     * Checks if a JSON entity contains a worldId field matching the specified worldId.
     *
     * @param entityJson the entity as JSON string
     * @param worldId    the worldId to match
     * @return true if the entity's worldId matches
     */
    private boolean matchesWorldId(String entityJson, String worldId) {
        // Simple JSON parsing to extract worldId field
        // Format: "\"worldId\":\"main\""
        int worldIdIndex = entityJson.indexOf("\"worldId\"");
        if (worldIdIndex == -1) {
            return false;
        }

        int valueStart = entityJson.indexOf("\"", worldIdIndex + 10);
        if (valueStart == -1) {
            return false;
        }

        int valueEnd = entityJson.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return false;
        }

        String entityWorldId = entityJson.substring(valueStart + 1, valueEnd);
        return worldId.equals(entityWorldId);
    }

    /**
     * Result of an import operation.
     */
    @lombok.Builder
    @lombok.Data
    public static class ImportResult {
        private String collectionName;
        private int totalCount;
        private int successCount;
        private int migrationCount;
        private int skippedCount;
        private int skippedExistingCount;
        private int errorCount;
        private long durationMs;
        private String inputFile;

        public boolean hasErrors() {
            return errorCount > 0;
        }

        public boolean isSuccess() {
            return successCount > 0 && errorCount == 0;
        }
    }
}
