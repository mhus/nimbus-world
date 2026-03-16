package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Additional metadata for a WChunk, stored separately to avoid performance impact
 * on chunk loading. Tracks which group and layer each block belongs to.
 * Uniqueness: (worldId, chunk, epoches) — mirrors the corresponding WChunk document.
 */
@Document(collection = "w_chunk_infos")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_chunk_idx", def = "{ 'worldId': 1, 'chunk': 1 }"),
        @CompoundIndex(name = "world_chunk_epoches_idx", def = "{ 'worldId': 1, 'chunk': 1, 'epoches': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WChunkInfo implements Identifiable, EpochEntity {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /** Chunk Identifier 'cx:cz'. */
    @Indexed
    private String chunk;

    @Builder.Default
    private List<Integer> epoches = List.of();

    private Instant createdAt;
    private Instant updatedAt;

    /** Group assignment per block. Key: "x,y,z", Value: groupId. */
    @Builder.Default
    private Map<String, String> blockGroups = new HashMap<>();

    /** Layer origin per block. Key: "x,y,z", Value: layerId. */
    @Builder.Default
    private Map<String, String> blockLayers = new HashMap<>();

    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
