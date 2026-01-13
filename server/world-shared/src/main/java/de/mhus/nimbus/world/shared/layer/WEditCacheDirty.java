package de.mhus.nimbus.world.shared.layer;

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
 * Edit cache dirty entity - work queue for merging cached edits into layers.
 * Tracks layers that have pending changes in WEditCache that need to be committed.
 * When processed, all WEditCache entries for the worldId+layerDataId are merged into WLayerModel.
 */
@Document(collection = "w_edit_cache_dirty")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_layerDataId_idx", def = "{ 'worldId': 1, 'layerDataId': 1 }", unique = true),
        @CompoundIndex(name = "createdAt_idx", def = "{ 'createdAt': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WEditCacheDirty implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier.
     */
    @Indexed
    private String worldId;

    /**
     * Layer data identifier - references WLayer.layerDataId.
     */
    @Indexed
    private String layerDataId;

    /**
     * Timestamp when marked dirty.
     */
    private Instant createdAt;

    /**
     * Initialize timestamp.
     */
    public void touch() {
        createdAt = Instant.now();
    }
}
