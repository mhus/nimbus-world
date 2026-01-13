package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic service for WPlayerSession.
 * Manages player session state persistence (position, rotation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WPlayerSessionService {

    private final WPlayerSessionRepository repository;

    /**
     * Normalize playerId to ensure it always starts with '@'.
     * Format: @userId:characterId
     */
    private String normalizePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return playerId;
        }
        // If playerId doesn't start with @, add it
        if (!playerId.startsWith("@")) {
            return "@" + playerId;
        }
        return playerId;
    }

    /**
     * Update player session position and rotation.
     * This method ONLY updates position/rotation and does NOT touch previousWorldId/Position/Rotation.
     * If no session exists, it creates a new one (for regular world entry without teleport).
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @param position The player position
     * @param rotation The player rotation
     * @return The updated session
     * @throws IllegalArgumentException if worldId or playerId is null or blank
     */
    @Transactional
    public WPlayerSession updateSession(String worldId, String playerId,
                                         Vector3 position, Rotation rotation) {
        // Validation
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId cannot be null or blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId cannot be null or blank");
        }

        // Normalize playerId to ensure it starts with @
        playerId = normalizePlayerId(playerId);

        // Normalize legacy entries (without '@' prefix) if they exist
        normalizeLegacyPlayerIds(worldId, playerId);

        // Find existing session
        Optional<WPlayerSession> existingOpt = repository.findFirstByWorldIdAndPlayerIdOrderByUpdatedAtDesc(worldId, playerId);

        WPlayerSession session;
        if (existingOpt.isPresent()) {
            // Update existing - ONLY position and rotation, keep previousWorldId/Position/Rotation
            session = existingOpt.get();
            session.setPosition(position);
            session.setRotation(rotation);
            session.touchUpdate();
            log.info("Updated player session position/rotation: id={}, worldId={}, playerId={}, position={}, rotation={}, previousWorldId={}, previousPosition={}",
                    session.getId(), worldId, playerId, position, rotation,
                    session.getPreviousWorldId(), session.getPreviousPosition());
        } else {
            // Create new session (regular world entry without teleport)
            session = WPlayerSession.builder()
                    .worldId(worldId)
                    .playerId(playerId)
                    .position(position)
                    .rotation(rotation)
                    .build();
            session.touchCreate();
            log.info("Created new player session (no teleport): worldId={}, playerId={}, position={}, rotation={}",
                    worldId, playerId, position, rotation);
        }

        repository.save(session);
        log.debug("Saved player session to MongoDB: id={}", session.getId());
        return session;
    }

    /**
     * Load player session by worldId and playerId.
     * If duplicates exist, automatically cleans them up and keeps only the newest.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @return Optional containing the session if found
     */
    @Transactional
    public Optional<WPlayerSession> loadSession(String worldId, String playerId) {
        // Normalize playerId to ensure it starts with @
        playerId = normalizePlayerId(playerId);

        // Normalize legacy entries (without '@' prefix) if they exist
        normalizeLegacyPlayerIds(worldId, playerId);

        return repository.findFirstByWorldIdAndPlayerIdOrderByUpdatedAtDesc(worldId, playerId);
    }

    /**
     * Normalize legacy player session entries by updating playerId to include '@' prefix.
     * Does NOT delete any sessions - each world keeps its own player session.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId (normalized with '@' prefix)
     */
    private void normalizeLegacyPlayerIds(String worldId, String playerId) {
        // Check for legacy format without '@' prefix
        if (playerId.startsWith("@")) {
            String legacyPlayerId = playerId.substring(1); // Remove '@' prefix
            List<WPlayerSession> legacySessions = repository.findByWorldIdAndPlayerIdOrderByUpdatedAtDesc(worldId, legacyPlayerId);

            if (!legacySessions.isEmpty()) {
                log.info("Found {} legacy player sessions (without '@') for worldId={}, playerId={} - normalizing",
                        legacySessions.size(), worldId, legacyPlayerId);

                // Update all legacy entries to use normalized playerId
                for (WPlayerSession session : legacySessions) {
                    log.info("Normalizing playerId from '{}' to '{}' in session {} (previousWorldId={})",
                            session.getPlayerId(), playerId, session.getId(), session.getPreviousWorldId());
                    session.setPlayerId(playerId);
                    session.touchUpdate();
                    repository.save(session);
                }
            }
        }
    }

    /**
     * Delete player session by worldId and playerId.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @return true if session was deleted, false if not found
     */
    @Transactional
    public boolean deleteSession(String worldId, String playerId) {
        // Normalize playerId to ensure it starts with @
        playerId = normalizePlayerId(playerId);

        if (repository.existsByWorldIdAndPlayerId(worldId, playerId)) {
            repository.deleteByWorldIdAndPlayerId(worldId, playerId);
            log.debug("Deleted player session: worldId={}, playerId={}", worldId, playerId);
            return true;
        }
        return false;
    }

    /**
     * Find all sessions for a specific world.
     * Useful for admin/debugging purposes.
     *
     * @param worldId The full worldId (including instance)
     * @return List of sessions
     */
    @Transactional(readOnly = true)
    public List<WPlayerSession> findByWorld(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Find all sessions for a specific player.
     * Useful for admin/debugging purposes.
     *
     * @param playerId The playerId
     * @return List of sessions
     */
    @Transactional(readOnly = true)
    public List<WPlayerSession> findByPlayer(String playerId) {
        return repository.findByPlayerId(playerId);
    }

    /**
     * Count sessions by worldId.
     *
     * @param worldId The full worldId (including instance)
     * @return Number of sessions
     */
    @Transactional(readOnly = true)
    public long countByWorld(String worldId) {
        return repository.countByWorldId(worldId);
    }

    /**
     * Count sessions by playerId.
     *
     * @param playerId The playerId
     * @return Number of sessions
     */
    @Transactional(readOnly = true)
    public long countByPlayer(String playerId) {
        return repository.countByPlayerId(playerId);
    }

    /**
     * Create new player session for teleportation.
     * This method creates a new session and stores previous world/position/rotation data.
     * Used when player teleports to a new world.
     *
     * @param worldId New worldId (including instance)
     * @param playerId Player ID
     * @param sessionId Session ID reference
     * @param actor Actor type (PLAYER, EDITOR, SUPPORT)
     * @param previousWorldId Previous worldId before teleport
     * @param previousPosition Previous position before teleport
     * @param previousRotation Previous rotation before teleport
     * @return The created session
     * @throws IllegalArgumentException if worldId or playerId is null or blank
     */
    @Transactional
    public WPlayerSession createTeleportSession(String worldId, String playerId,
                                                  String sessionId, String actor,
                                                  Vector3 position,
                                                  Rotation rotation,
                                                  String previousWorldId,
                                                  Vector3 previousPosition,
                                                  Rotation previousRotation
                                                ) {
        // Validation
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId cannot be null or blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId cannot be null or blank");
        }

        // Normalize playerId to ensure it starts with @
        playerId = normalizePlayerId(playerId);

        // Normalize legacy entries (without '@' prefix) if they exist
        normalizeLegacyPlayerIds(worldId, playerId);

        // Upsert: find existing or create new
        Optional<WPlayerSession> existingOpt = repository.findFirstByWorldIdAndPlayerIdOrderByUpdatedAtDesc(worldId, playerId);

        WPlayerSession session;
        if (existingOpt.isPresent()) {
            // Update existing session for teleportation
            session = existingOpt.get();
            session.setSessionId(sessionId);
            session.setActor(actor);
            session.setPosition(position);
            session.setRotation(rotation);
            session.setPreviousWorldId(previousWorldId);
            session.setPreviousPosition(previousPosition);
            session.setPreviousRotation(previousRotation);
            session.touchUpdate();
            log.info("Updated existing player session for teleport: id={}, worldId={}, playerId={}, previousWorldId={}",
                    session.getId(), worldId, playerId, previousWorldId);
        } else {
            // Create new session with previous values
            session = WPlayerSession.builder()
                    .worldId(worldId)
                    .playerId(playerId)
                    .sessionId(sessionId)
                    .actor(actor)
                    .position(null) // Will be set when player enters world
                    .rotation(null) // Will be set when player enters world
                    .previousWorldId(previousWorldId)
                    .previousPosition(previousPosition)
                    .previousRotation(previousRotation)
                    .build();
            session.touchCreate();
            log.info("Created new teleport player session: worldId={}, playerId={}, previousWorldId={}",
                    worldId, playerId, previousWorldId);
        }

        repository.save(session);
        log.debug("Saved teleport session to MongoDB: id={}", session.getId());

        return session;
    }

    /**
     * Merge player status data from old session to new session.
     * Placeholder for future implementation.
     * This will transfer player state (health, mana, stamina, effects, inventory, etc.)
     * when the data model is ready.
     *
     * @param newSession The new target session
     * @param oldSession The old source session
     */
    @Transactional
    public void mergePlayerData(WPlayerSession newSession, WPlayerSession oldSession) {
        if (oldSession == null || newSession == null) {
            return;
        }

        log.debug("mergePlayerData called but not yet implemented: oldWorldId={}, newWorldId={}, playerId={}",
                oldSession.getWorldId(), newSession.getWorldId(), newSession.getPlayerId());

        // TODO: Implement player data merge when fields are defined
        // This will include: health, mana, stamina, effects, inventory, attributes, quest data, etc.
    }
}
