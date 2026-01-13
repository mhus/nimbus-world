package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.service.MongoRawDocumentService;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST controller for schema migration operations.
 * Provides endpoints to migrate MongoDB documents to their latest schema version.
 *
 * <p>This controller should be enabled in modules that need migration endpoints
 * by component scanning the {@code de.mhus.nimbus.shared.api} package.</p>
 */
@RestController
@RequestMapping("/control/schema")
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationController {

    private final MongoRawDocumentService rawDocumentService;
    private final SchemaMigrationService migrationService;

    /**
     * Migration request DTO.
     */
    public record MigrationRequest(
            String collectionName,
            String documentId,      // ID, "*" for all, or "no-schema" for documents without _schema
            String entityType,
            SchemaVersion targetVersion
    ) {}

    /**
     * Migration response DTO.
     */
    public record MigrationResponse(
            boolean success,
            String message,
            int successCount,
            int failureCount,
            int skippedCount,
            int totalCount
    ) {}

    /**
     * Storage migration response DTO.
     */
    public record StorageMigrationResponse(
            String storageId,
            String schema,
            SchemaVersion fromVersion,
            SchemaVersion toVersion,
            boolean migrated,
            String message
    ) {}

    /**
     * Migrates a storage object to the latest available version.
     * POST /shared/schema/migrate/storage/{storageId}
     *
     * @param storageId the storage ID to migrate
     * @return migration result with details
     */
    @PostMapping("/migrate/storage/{storageId}")
    public ResponseEntity<StorageMigrationResponse> migrateStorage(@PathVariable String storageId) {
        try {
            log.info("Storage migration request for storageId: {}", storageId);

            SchemaMigrationService.MigrationResult result = migrationService.migrateStorage(storageId);

            StorageMigrationResponse response = new StorageMigrationResponse(
                    result.storageId(),
                    result.schema(),
                    result.fromVersion(),
                    result.toVersion(),
                    result.migrated(),
                    result.message()
            );

            return ResponseEntity.ok(response);

        } catch (SchemaMigrationService.MigrationException e) {
            log.error("Storage migration failed for storageId: {}", storageId, e);

            StorageMigrationResponse response = new StorageMigrationResponse(
                    storageId,
                    null,
                    null,
                    null,
                    false,
                    "Migration failed: " + e.getMessage()
            );

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Migrate a single document or all documents in a collection.
     *
     * @param request the migration request
     * @return migration result
     */
    @PostMapping("/migrate")
    public ResponseEntity<MigrationResponse> migrate(@RequestBody MigrationRequest request) {
        log.info("Migration request: collection={}, pattern={}, entity={}, targetVersion={}",
                request.collectionName, request.documentId, request.entityType, request.targetVersion);

        try {
            // Validate request
            if (request.collectionName == null || request.collectionName.isBlank()) {
                return ResponseEntity.badRequest().body(new MigrationResponse(
                        false, "Collection name is required", 0, 0, 0, 0));
            }

            if (request.entityType == null || request.entityType.isBlank()) {
                return ResponseEntity.badRequest().body(new MigrationResponse(
                        false, "Entity type is required", 0, 0, 0, 0));
            }

            if (request.targetVersion == null || request.targetVersion.isNull()) {
                return ResponseEntity.badRequest().body(new MigrationResponse(
                        false, "Target version is required", 0, 0, 0, 0));
            }

            // Route to appropriate migration method
            MigrationResponse response = switch (request.documentId.toLowerCase()) {
                case "*" -> migrateAllDocuments(request);
                case "no-schema" -> migrateDocumentsWithoutSchema(request);
                default -> migrateSingleDocument(request);
            };

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new MigrationResponse(
                    false, "Migration failed: " + e.getMessage(), 0, 0, 0, 0));
        }
    }

    /**
     * Get migration statistics for a collection.
     *
     * @param collectionName the collection name
     * @return statistics about schema versions in the collection
     */
    @GetMapping("/stats/{collectionName}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String collectionName) {

        try {
            List<String> documents = rawDocumentService.findAllDocuments(collectionName);

            Map<String, Integer> versionCounts = new HashMap<>();
            int noSchemaCount = 0;

            for (String doc : documents) {
                var version = extractSchemaVersion(doc);
                if (version == null || version.isNull()) {
                    noSchemaCount++;
                } else {
                    versionCounts.merge(version.toString(), 1, Integer::sum);
                }
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("collectionName", collectionName);
            stats.put("totalDocuments", documents.size());
            stats.put("documentsWithoutSchema", noSchemaCount);
            stats.put("versionDistribution", versionCounts);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to get statistics: " + e.getMessage()));
        }
    }

    /**
     * List all registered entity types that have migrators.
     *
     * @return list of entity types
     */
    @GetMapping("/entity-types")
    public ResponseEntity<Map<String, Object>> listEntityTypes() {
        var entityTypes = migrationService.getRegisteredEntityTypes();

        Map<String, Object> response = new HashMap<>();
        response.put("entityTypes", entityTypes);
        response.put("count", entityTypes.size());

        return ResponseEntity.ok(response);
    }

    // Private helper methods

    private MigrationResponse migrateSingleDocument(MigrationRequest request) {
        log.info("Migrating single document: {} in {}", request.documentId, request.collectionName);

        String documentJson = rawDocumentService.findDocumentById(request.collectionName, request.documentId);
        if (documentJson == null) {
            return new MigrationResponse(false, "Document not found: " + request.documentId, 0, 1, 0, 1);
        }

        try {
            var currentVersion = extractSchemaVersion(documentJson);
            String migratedJson = migrationService.migrate(documentJson, request.entityType, request.targetVersion, currentVersion);
            boolean updated = rawDocumentService.replaceDocument(request.collectionName, request.documentId, migratedJson);

            if (updated) {
                log.info("Successfully migrated document {} to version {}", request.documentId, request.targetVersion);
                return new MigrationResponse(true,
                        "Document migrated successfully to version " + request.targetVersion, 1, 0, 0, 1);
            } else {
                return new MigrationResponse(false, "Failed to update document: " + request.documentId, 0, 1, 0, 1);
            }

        } catch (SchemaMigrationService.MigrationException e) {
            log.error("Migration failed for document {}: {}", request.documentId, e.getMessage());
            return new MigrationResponse(false, "Migration failed: " + e.getMessage(), 0, 1, 0, 1);
        }
    }

    private MigrationResponse migrateAllDocuments(MigrationRequest request) {
        log.info("Migrating all documents in collection: {}", request.collectionName);

        List<String> documents = rawDocumentService.findAllDocuments(request.collectionName);
        if (documents.isEmpty()) {
            return new MigrationResponse(true, "No documents found in collection", 0, 0, 0, 0);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        for (String documentJson : documents) {
            try {
                String documentId = rawDocumentService.extractDocumentId(documentJson);

                // Check if migration is needed
                if (!needsMigration(documentJson, request.targetVersion)) {
                    skippedCount.incrementAndGet();
                    log.debug("Document {} already at version {}, skipping", documentId, request.targetVersion);
                    continue;
                }

                var currentVersion = extractSchemaVersion(documentJson);
                String migratedJson = migrationService.migrate(documentJson, request.entityType, request.targetVersion, currentVersion);
                boolean updated = rawDocumentService.replaceDocument(request.collectionName, documentId, migratedJson);

                if (updated) {
                    successCount.incrementAndGet();
                    log.debug("Migrated document {} to version {}", documentId, request.targetVersion);
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

        return new MigrationResponse(
                failureCount.get() == 0,
                message,
                successCount.get(),
                failureCount.get(),
                skippedCount.get(),
                documents.size()
        );
    }

    private MigrationResponse migrateDocumentsWithoutSchema(MigrationRequest request) {
        log.info("Migrating documents without _schema field in collection: {}", request.collectionName);

        List<String> documents = rawDocumentService.findDocumentsWithoutField(request.collectionName, "_schema");
        if (documents.isEmpty()) {
            return new MigrationResponse(true, "No documents without _schema field found", 0, 0, 0, 0);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (String documentJson : documents) {
            try {
                String documentId = rawDocumentService.extractDocumentId(documentJson);
                var currentVersion = extractSchemaVersion(documentJson);
                String migratedJson = migrationService.migrate(documentJson, request.entityType, request.targetVersion, currentVersion);
                boolean updated = rawDocumentService.replaceDocument(request.collectionName, documentId, migratedJson);

                if (updated) {
                    successCount.incrementAndGet();
                    log.debug("Migrated document {} from no-schema to version {}", documentId, request.targetVersion);
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

        return new MigrationResponse(
                failureCount.get() == 0,
                message,
                successCount.get(),
                failureCount.get(),
                0,
                documents.size()
        );
    }

    private boolean needsMigration(String documentJson, SchemaVersion targetVersion) {
        var currentVersion = extractSchemaVersion(documentJson);
        return !targetVersion.equals(currentVersion);
    }

    private SchemaVersion extractSchemaVersion(String documentJson) {
        int schemaIndex = documentJson.indexOf("\"_schema\"");
        if (schemaIndex == -1) {
            return SchemaVersion.NULL;
        }

        int valueStart = documentJson.indexOf("\"", schemaIndex + 10);
        if (valueStart == -1) {
            return SchemaVersion.NULL;
        }

        int valueEnd = documentJson.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return SchemaVersion.NULL;
        }

        return SchemaVersion.create(documentJson.substring(valueStart + 1, valueEnd));
    }
}
