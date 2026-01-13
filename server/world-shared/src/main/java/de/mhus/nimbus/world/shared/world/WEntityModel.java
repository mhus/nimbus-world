package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Entity for EntityModel templates.
 * Wraps generated EntityModel DTO in 'publicData' field.
 * EntityModels are templates that define 3D models, animations, and physics for entities.
 */
@Document(collection = "w_entity_models")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "modelId_idx", def = "{ 'modelId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WEntityModel implements Identifiable {

    @Id
    private String id;

    /**
     * Model identifier (e.g., "cow1", "farmer1", "pig1").
     * Unique across all entity models.
     */
    @Indexed(unique = true)
    private String modelId;

    /**
     * Public data containing the generated EntityModel DTO.
     * This is what gets serialized and sent to clients.
     */
    private EntityModel publicData;

    /**
     * Must have world identifier
     */
    @Indexed
    private String worldId;

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
