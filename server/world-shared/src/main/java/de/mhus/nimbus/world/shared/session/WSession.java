package de.mhus.nimbus.world.shared.session;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WSession {
    private String id; // 60 zufällige alphanumerische Zeichen
    private WSessionStatus status;
    private String worldId;
    private String playerId;
    private String actor;
    private String playerUrl; // Internal URL of the world-player service
    private String entryPoint; // Entry point specification: "last", "grid:q,r", or "world"
    private String teleportation; // Teleportation data for later use
    private List<String> modelSelector; // Model selector for block build operations (config + selected blocks in Vector3Color format)
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expireAt;

    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}

