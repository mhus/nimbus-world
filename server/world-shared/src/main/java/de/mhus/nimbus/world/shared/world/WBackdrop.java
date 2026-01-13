package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Backdrop;
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
 * MongoDB Entity for Backdrop configurations.
 * Wraps generated Backdrop DTO in 'publicData' field.
 * Backdrops are visual elements rendered at chunk boundaries (fog, sky, etc.).
 */
@Document(collection = "w_backdrops")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "backdropId_idx", def = "{ 'backdropId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WBackdrop implements Identifiable {

    @Id
    private String id;

    /**
     * Backdrop identifier (e.g., "fog1", "sky", "hills").
     * Unique across all backdrops.
     */
    @Indexed(unique = true)
    private String backdropId;

    /**
     * Public data containing the generated Backdrop DTO.
     * This is what gets serialized and sent to clients.
     */
    private Backdrop publicData;

    /**
     * Optional world identifier for scoped backdrops.
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
