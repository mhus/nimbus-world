package de.mhus.nimbus.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.types.SchemaVersion;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MongoDB event listener that automatically manages schema versioning for entities.
 *
 * <p>This listener performs two main functions:</p>
 * <ul>
 *   <li><b>Before Save:</b> Automatically adds the "_schema" field to MongoDB documents
 *       based on the {@link ActualSchemaVersion} annotation on the entity class.</li>
 *   <li><b>After Load:</b> Validates that the loaded document's schema version matches
 *       the expected version. Can optionally trigger automatic migration.</li>
 * </ul>
 *
 * <p>Performance optimization: Schema versions are cached in memory to avoid
 * repeated reflection calls on entity classes.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>{@code nimbus.schema.auto-migrate=true}: Enable automatic migration on load (default: false)</li>
 * </ul>
 */
@Component
@Slf4j
public class SchemaVersionEventListener extends AbstractMongoEventListener<Object> {

    private final ConcurrentHashMap<Class<?>, String> schemaVersionCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private SchemaMigrationService migrationService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${nimbus.schema.auto-migrate:false}")
    private boolean autoMigrate;

    /**
     * Called before an entity is saved to MongoDB.
     * Adds the "_schema" field with the version from the {@link ActualSchemaVersion} annotation.
     *
     * This uses BeforeSaveEvent instead of BeforeConvertEvent to ensure the document is fully
     * prepared and available for modification.
     *
     * @param event the before save event containing the entity and document
     */
    @Override
    public void onBeforeSave(BeforeSaveEvent<Object> event) {
        Object source = event.getSource();
        Document document = event.getDocument();

        if (document == null || source == null) {
            return;
        }

        // Extract schema version from annotation (with caching for performance)
        Class<?> entityClass = source.getClass();
        String version = getSchemaVersion(entityClass);

        if (version != null) {
            document.put("_schema", version);

            log.trace("Added _schema={} to {} in collection {}",
                      version,
                      source.getClass().getSimpleName(),
                      event.getCollectionName());
        }
    }

    /**
     * Called after a MongoDB document is converted to an entity.
     * Validates that the document's schema version matches the expected version.
     *
     * @param event the after convert event containing the entity and document
     */
    @Override
    public void onAfterConvert(AfterConvertEvent<Object> event) {
        Object entity = event.getSource();
        Document document = event.getDocument();

        if (entity == null || document == null) {
            return;
        }

        // Get schema version from document and expected version from annotation
        String documentSchema = document.getString("_schema");
        Class<?> entityClass = entity.getClass();
        String expectedVersion = getSchemaVersion(entityClass);

        // Check if schema version doesn't match
        if (expectedVersion != null && !expectedVersion.equals(documentSchema)) {
            handleSchemaMismatch(entity, documentSchema, expectedVersion);
        }
    }

    /**
     * Gets the schema version for an entity class from its {@link ActualSchemaVersion} annotation.
     * Results are cached for performance.
     *
     * @param entityClass the entity class to get the schema version for
     * @return the schema version string, or null if no annotation is present
     */
    private String getSchemaVersion(Class<?> entityClass) {
        return schemaVersionCache.computeIfAbsent(entityClass, clazz -> {
            ActualSchemaVersion annotation = clazz.getAnnotation(ActualSchemaVersion.class);
            return annotation != null ? annotation.value() : null;
        });
    }

    /**
     * Handles the case when a loaded document's schema version doesn't match
     * the expected version from the entity class annotation.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Always logs a warning about the mismatch</li>
     *   <li>If {@code nimbus.schema.auto-migrate=true} and migration service is available,
     *       performs automatic in-place migration and persists to database</li>
     *   <li>Migration happens synchronously during entity load</li>
     * </ul>
     *
     * @param entity the loaded entity instance
     * @param documentSchema the schema version from the MongoDB document (may be null)
     * @param expectedSchema the expected schema version from the annotation
     */
    private void handleSchemaMismatch(Object entity, String documentSchema, String expectedSchema) {
        String entityType = entity.getClass().getSimpleName();
        SchemaVersion currentVersion = documentSchema != null ?
            SchemaVersion.create(documentSchema) : SchemaVersion.NULL;
        SchemaVersion targetVersion = SchemaVersion.create(expectedSchema);

        String entityId = null;
        if (entity instanceof Identifiable identifiable) {
            entityId = identifiable.getId();
        }

        log.debug("Schema version mismatch for entity {} (ID: {}): document has schema '{}', expected '{}'",
                 entityType,
                 entityId != null ? entityId : "unknown",
                 currentVersion,
                 targetVersion);

        // Automatic migration if enabled
        if (!autoMigrate) {
            log.trace("Auto-migrate disabled, skipping migration for {}", entityType);
            return;
        }

        if (migrationService == null) {
            log.debug("Auto-migrate enabled but migration service not available");
            return;
        }

        if (entityId == null) {
            log.warn("Cannot auto-migrate entity {} without ID field", entityType);
            return;
        }

        try {
            log.info("Performing automatic migration for {} (ID: {}) from {} to {}",
                    entityType, entityId, currentVersion, targetVersion);

            // Get the MongoDB collection name from entity annotation
            org.springframework.data.mongodb.core.mapping.Document docAnnotation =
                entity.getClass().getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);

            if (docAnnotation == null) {
                log.error("Cannot migrate entity {} - missing @Document annotation", entityType);
                return;
            }

            String collectionName = docAnnotation.collection();
            if (collectionName.isEmpty()) {
                // Use default collection name (lowercase class name)
                collectionName = entity.getClass().getSimpleName().toLowerCase();
            }

            // Load the document from MongoDB
            Query query = new Query(Criteria.where("_id").is(entityId));
            Document document = mongoTemplate.findOne(query, Document.class, collectionName);

            if (document == null) {
                log.error("Cannot migrate entity {} (ID: {}) - document not found in collection {}",
                        entityType, entityId, collectionName);
                return;
            }

            // Convert document to JSON for migration
            String documentJson = document.toJson();

            // Perform migration
            String migratedJson = migrationService.migrate(
                documentJson,
                entityType,
                targetVersion,
                currentVersion
            );

            // Parse migrated JSON back to Document
            Document migratedDocument = Document.parse(migratedJson);

            // Update the document in MongoDB with all fields from migrated document
            Update update = new Update();
            migratedDocument.forEach((key, value) -> {
                if (!key.equals("_id")) {  // Don't update _id field
                    update.set(key, value);
                }
            });

            mongoTemplate.updateFirst(query, update, collectionName);

            // Update the loaded entity with migrated values using reflection
            updateEntityFromDocument(entity, migratedDocument);

            log.info("Successfully migrated and persisted entity {} (ID: {}) from {} to {}",
                    entityType, entityId, currentVersion, targetVersion);

        } catch (SchemaMigrationService.MigrationException e) {
            log.error("Automatic migration failed for {} (ID: {}) from {} to {}: {}",
                    entityType, entityId, currentVersion, targetVersion, e.getMessage());
        } catch (Exception e) {
            log.error("Automatic migration failed for {} (ID: {}) from {} to {}: {}",
                    entityType, entityId, currentVersion, targetVersion, e.getMessage(), e);
        }
    }

    /**
     * Updates entity fields from migrated document using reflection.
     * This ensures the loaded entity reflects the migrated state.
     *
     * @param entity the entity to update
     * @param migratedDocument the migrated MongoDB document
     */
    private void updateEntityFromDocument(Object entity, Document migratedDocument) {
        try {
            // Convert migrated document back to entity type using MongoTemplate converter
            Object migratedEntity = mongoTemplate.getConverter().read(
                entity.getClass(),
                migratedDocument
            );

            // Copy all fields from migrated entity to original entity
            Class<?> entityClass = entity.getClass();
            while (entityClass != null && entityClass != Object.class) {
                for (Field field : entityClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(migratedEntity);
                    field.set(entity, value);
                }
                entityClass = entityClass.getSuperclass();
            }

        } catch (Exception e) {
            log.warn("Failed to update entity fields from migrated document: {}", e.getMessage());
        }
    }
}
