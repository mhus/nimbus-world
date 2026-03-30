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
 * Flag definition for the Logic Machine.
 * Describes known flags and their default values.
 * Auto-created when a flag is first written if not already defined.
 */
@Document(collection = "w_logic_flags")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_flag_idx", def = "{ 'worldId': 1, 'flagName': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WLogicFlag implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    private String flagName;

    private Object defaultValue;

    /**
     * Type hint: "boolean", "integer", "string".
     */
    private String type;

    private String description;

    /**
     * True if this flag was auto-created on first write (not explicitly defined).
     */
    @Builder.Default
    private boolean autoCreated = false;

    private Instant createdAt;
}
