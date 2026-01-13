package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Entity for storing editor settings per world and user.
 * Stores user-specific editor configuration like block palettes.
 */
@Document(collection = "w_world_edit_settings")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_userId_idx", def = "{ 'worldId': 1, 'userId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WWorldEditSettings {

    @Id
    private String id;

    /**
     * World identifier (not validated here, but should be a valid WorldId string).
     */
    private String worldId;

    /**
     * User identifier (NOT player ID - this is the user ID from the session).
     */
    private String userId;

    /**
     * List of palette block definitions for paste operations.
     */
    @Builder.Default
    private List<PaletteBlockDefinition> palette = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

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
