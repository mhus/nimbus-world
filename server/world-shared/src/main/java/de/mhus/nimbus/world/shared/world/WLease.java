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
import java.util.Map;
import java.util.UUID;

/**
 * MongoDB Entity for short-lived access leases.
 * A lease grants a player temporary access to a widget interaction
 * (e.g. crafting station, dialog, chest, trade, document).
 * Leases expire automatically via MongoDB TTL index.
 */
@Document(collection = "w_lease")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_player_type_idx", def = "{ 'worldId': 1, 'playerId': 1, 'type': 1 }"),
        @CompoundIndex(name = "world_player_resource_idx", def = "{ 'worldId': 1, 'playerId': 1, 'resourceId': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WLease implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    @Indexed(unique = true)
    @Builder.Default
    private String leaseId = UUID.randomUUID().toString();

    /**
     * Player identifier (user ID).
     */
    @Indexed
    private String playerId;

    /**
     * Lease type: crafting-station, dialog, document, chest-access, trade-access.
     */
    private String type;

    /**
     * Identifies the resource being accessed (entityId, chestName, documentRef, etc.).
     */
    private String resourceId;

    /**
     * Optional display title.
     */
    private String title;

    /**
     * Lease-specific data needed by the widget.
     */
    private Map<String, Object> leaseData;

    private Instant createdAt;

    /**
     * TTL field - MongoDB automatically deletes the document after this time.
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    /**
     * Initialize timestamps for new lease.
     */
    public void touchCreate() {
        createdAt = Instant.now();
    }
}
