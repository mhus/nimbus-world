package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AccessLevel;
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
import java.util.Map;
import java.util.UUID;

/**
 * MongoDB Entity for player progress tracking.
 * Stores quest progress, achievements, and other player-specific progression data.
 */
@Document(collection = "w_progress")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_player_type_idx", def = "{ 'worldId': 1, 'playerId': 1, 'type': 1 }"),
        @CompoundIndex(name = "world_player_quest_idx", def = "{ 'worldId': 1, 'playerId': 1, 'quest': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@GenerateTypeScript("entities")
public class WProgress implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    /**
     * World identifier where this progress belongs.
     */
    @Indexed
    private String worldId;

    @Indexed(unique = true)
    @Builder.Default
    private String progressId = UUID.randomUUID().toString();

    private String title; // Optional title for the progress entry (e.g. quest name, achievement title)

    /**
     * Player identifier (user ID).
     */
    @Indexed
    private String playerId;

    /**
     * Optional quest identifier this progress is associated with.
     */
    @TypeScript(optional = true)
    private String quest;

    /**
     * Progress type (e.g. 'quest', 'achievement', 'skill', 'exploration').
     */
    private String type;

    /**
     * Arbitrary progress data as JSON key-value pairs.
     * Can contain any progress-related data like steps completed, scores, flags, etc.
     */
    private Map<String, Object> progressData;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Initialize timestamps for new progress entry.
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
