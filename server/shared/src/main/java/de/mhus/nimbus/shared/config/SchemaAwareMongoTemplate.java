package de.mhus.nimbus.shared.config;

import com.mongodb.client.result.UpdateResult;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended MongoTemplate that automatically adds the {@code _schema} field
 * to {@code updateFirst}, {@code updateMulti}, and {@code upsert} operations.
 *
 * <p>The standard Spring Data MongoDB lifecycle events ({@code BeforeSaveEvent})
 * only fire for {@code save()} operations. Update and upsert operations bypass
 * the event listener mechanism entirely, causing documents to be created or
 * modified without the {@code _schema} field.</p>
 *
 * <p>This subclass intercepts all update/upsert calls that provide an entity
 * class parameter, looks up the {@link ActualSchemaVersion} annotation, and
 * adds {@code _schema} to the {@link Update} object before delegating to the
 * parent implementation.</p>
 */
@Slf4j
public class SchemaAwareMongoTemplate extends MongoTemplate {

    private static final ConcurrentHashMap<Class<?>, String> SCHEMA_CACHE = new ConcurrentHashMap<>();

    public SchemaAwareMongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter mongoConverter) {
        super(mongoDbFactory, mongoConverter);
    }

    // --- updateFirst ---

    @Override
    public UpdateResult updateFirst(Query query, UpdateDefinition update, Class<?> entityClass) {
        enrichUpdate(update, entityClass);
        return super.updateFirst(query, update, entityClass);
    }

    @Override
    public UpdateResult updateFirst(Query query, UpdateDefinition update, Class<?> entityClass, String collectionName) {
        enrichUpdate(update, entityClass);
        return super.updateFirst(query, update, entityClass, collectionName);
    }

    // --- updateMulti ---

    @Override
    public UpdateResult updateMulti(Query query, UpdateDefinition update, Class<?> entityClass) {
        enrichUpdate(update, entityClass);
        return super.updateMulti(query, update, entityClass);
    }

    @Override
    public UpdateResult updateMulti(Query query, UpdateDefinition update, Class<?> entityClass, String collectionName) {
        enrichUpdate(update, entityClass);
        return super.updateMulti(query, update, entityClass, collectionName);
    }

    // --- upsert ---

    @Override
    public UpdateResult upsert(Query query, UpdateDefinition update, Class<?> entityClass) {
        enrichUpdate(update, entityClass);
        return super.upsert(query, update, entityClass);
    }

    @Override
    public UpdateResult upsert(Query query, UpdateDefinition update, Class<?> entityClass, String collectionName) {
        enrichUpdate(update, entityClass);
        return super.upsert(query, update, entityClass, collectionName);
    }

    /**
     * Enriches an UpdateDefinition with the {@code _schema} field based on
     * the entity class's {@link ActualSchemaVersion} annotation.
     *
     * <p>Only modifies the update if:</p>
     * <ul>
     *   <li>The entity class is not null</li>
     *   <li>The update is an instance of {@link Update} (not a raw pipeline update)</li>
     *   <li>The entity class has {@link ActualSchemaVersion} annotation</li>
     * </ul>
     */
    private void enrichUpdate(UpdateDefinition update, Class<?> entityClass) {
        if (entityClass == null || !(update instanceof Update u)) {
            return;
        }

        String version = resolveSchemaVersion(entityClass);
        if (version != null) {
            u.set("_schema", version);
            log.trace("Enriched update with _schema={} for {}", version, entityClass.getSimpleName());
        }
    }

    private String resolveSchemaVersion(Class<?> entityClass) {
        String cached = SCHEMA_CACHE.computeIfAbsent(entityClass, clazz -> {
            ActualSchemaVersion annotation = clazz.getAnnotation(ActualSchemaVersion.class);
            // Use empty string as "no annotation" marker to avoid repeated reflection
            return annotation != null ? annotation.value() : "";
        });
        return cached.isEmpty() ? null : cached;
    }
}
