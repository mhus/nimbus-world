package de.mhus.nimbus.shared.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark MongoDB entities with their schema version.
 * The version will be automatically stored in the "_schema" field
 * in MongoDB documents when the entity is saved.
 *
 * Example:
 * <pre>
 * @Document(collection = "users")
 * @SchemaVersion("1.0.0")
 * @Data
 * public class UUser {
 *     // ... entity fields ...
 * }
 * </pre>
 *
 * When loading entities, if the document's schema version doesn't match
 * the expected version from this annotation, a warning will be logged.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ActualSchemaVersion {
    /**
     * The schema version for this entity.
     * Format: semantic versioning recommended (e.g., "1.0.0", "2.1.3")
     *
     * @return the schema version string
     */
    String value();
}
