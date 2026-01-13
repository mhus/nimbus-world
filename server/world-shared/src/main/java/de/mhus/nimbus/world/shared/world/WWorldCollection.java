package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Entity for World Collections.
 * World Collections are groups of worlds identified by a worldId starting with '@'.
 */
@Document(collection = "w_world_collections")
@ActualSchemaVersion("1.0.0")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WWorldCollection implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    /**
     * Collection identifier (worldId).
     * Must start with '@' to identify it as a collection.
     */
    @Indexed(unique = true)
    private String worldId;

    /**
     * Display title for the collection.
     */
    private String title;

    /**
     * Description of the collection.
     */
    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Initialize timestamps for new entity.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
