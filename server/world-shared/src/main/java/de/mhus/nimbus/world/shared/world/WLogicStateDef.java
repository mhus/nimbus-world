package de.mhus.nimbus.world.shared.world;

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
 * State definition for the Logic Machine.
 * Describes known state keys and their default values.
 * Auto-created when a key is first written if not already defined.
 */
@Document(collection = "w_logic_states")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_name_idx", def = "{ 'worldId': 1, 'name': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WLogicStateDef implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /**
     * Qualified state key name (e.g. "puzzle.hasKey", "quest.completed").
     */
    private String name;

    private Object defaultValue;

    /**
     * Type hint: "boolean", "integer", "string".
     */
    private String type;

    private String description;

    /**
     * True if auto-created on first write (not explicitly defined).
     */
    @Builder.Default
    private boolean autoCreated = false;

    private Instant createdAt;
}
