package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WSessionService {

    private final StringRedisTemplate redis;
    private final WorldSettings props;
    private final EngineMapper mapper;

    private static final String KEY_PREFIX = "wsession:"; // Namespace
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_WORLD = "world";
    private static final String FIELD_USER = "user";
    private static final String FIELD_ACTOR = "actor";
    private static final String FIELD_PLAYER_URL = "playerUrl";
    private static final String FIELD_ENTRY_POINT = "entryPoint";
    private static final String FIELD_TELEPORTATION = "teleportation";
    private static final String FIELD_MODEL_SELECTOR = "modelSelector";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_UPDATED = "updated";
    private static final String FIELD_EXPIRE = "expire";

    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 60;

    public WSession create(WorldId worldId, PlayerId playerId, String actor) {
        String id = randomId();
        Instant now = Instant.now();
        Duration effectiveTtl = Duration.ofMinutes(props.getWaitingMinutes());
        Instant expire = now.plus(effectiveTtl);
        WSession session = WSession.builder()
                .id(id)
                .status(WSessionStatus.WAITING)
                .worldId(worldId.getId())
                .playerId(playerId.getId())
                .actor(actor)
                .createdAt(now)
                .updatedAt(now)
                .expireAt(expire)
                .build();
        write(session, effectiveTtl);

        // Store player session in Redis: region:<regionId>:player:<playerId>
        String regionId = worldId.getRegionId();
        storePlayerSession(regionId, playerId.getId(), id);

        log.debug("WSession erstellt id={} world={} user={} status=WAITING ttl={}min", id, worldId, playerId, effectiveTtl.toMinutes());
        return session;
    }

    public Optional<WSession> get(String id) {
        var ops = redis.opsForHash();
        var map = ops.entries(key(id));
        if (map == null || map.isEmpty()) return Optional.empty();
        try {
            // Parse modelSelector if present
            java.util.List<String> modelSelector = null;
            Object modelSelectorObj = map.get(FIELD_MODEL_SELECTOR);
            if (modelSelectorObj instanceof String modelSelectorStr && !modelSelectorStr.isBlank()) {
                // Stored as JSON array string
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                modelSelector = mapper.readValue(modelSelectorStr,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            }

            WSession session = WSession.builder()
                    .id(id)
                    .status(WSessionStatus.valueOf((String) map.get(FIELD_STATUS)))
                    .worldId((String) map.get(FIELD_WORLD))
                    .playerId((String) map.get(FIELD_USER))
                    .playerUrl((String) map.get(FIELD_PLAYER_URL))
                    .actor((String) map.get(FIELD_ACTOR))
                    .entryPoint((String) map.get(FIELD_ENTRY_POINT))
                    .teleportation((String) map.get(FIELD_TELEPORTATION))
                    .modelSelector(modelSelector)
                    .createdAt(Instant.parse((String) map.get(FIELD_CREATED)))
                    .updatedAt(Instant.parse((String) map.get(FIELD_UPDATED)))
                    .expireAt(Instant.parse((String) map.get(FIELD_EXPIRE)))
                    .build();
            return Optional.of(session);
        } catch (Exception e) {
            log.warn("Fehler beim Lesen der Session {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lädt eine WSession aus Redis mit allen Daten inklusive internal player URL.
     * Diese Methode ist identisch zu get(), aber mit aussagekräftigerem Namen für
     * den Use-Case, wenn explizit die Player-URL benötigt wird.
     *
     * @param sessionId Die Session-ID
     * @return Optional mit WSession inkl. playerUrl, oder empty wenn nicht gefunden
     */
    public Optional<WSession> getWithPlayerUrl(String sessionId) {
        return get(sessionId);
    }

    public Optional<WSession> updateStatus(String id, WSessionStatus newStatus) {
        return get(id).map(existing -> {
            existing.setStatus(newStatus);
            existing.touchUpdate();
            Duration newTtl = switch (newStatus) {
                case WAITING -> Duration.ofMinutes(props.getWaitingMinutes());
                case RUNNING -> Duration.ofHours(props.getRunningHours());
                case CLOSED -> Duration.ofMinutes(props.getDeprecatedMinutes());
            };
            existing.setExpireAt(Instant.now().plus(newTtl));
            write(existing, newTtl);

            // Delete player session entry when status changes to CLOSED
            if (newStatus == WSessionStatus.CLOSED) {
                WorldId worldId = WorldId.unchecked(existing.getWorldId());
                String regionId = worldId.getRegionId();
                deletePlayerSession(regionId, existing.getPlayerId());
                log.debug("Deleted player session entry for regionId={} playerId={}", regionId, existing.getPlayerId());
            }

            log.debug("WSession status aktualisiert id={} status={} ttl={}s", id, newStatus, newTtl.toSeconds());
            return existing;
        });
    }

    public Optional<WSession> updatePlayerUrl(String id, String playerUrl) {
        return get(id).map(existing -> {
            existing.setPlayerUrl(playerUrl);
            existing.touchUpdate();
            Duration ttl = switch (existing.getStatus()) {
                case WAITING -> Duration.ofMinutes(props.getWaitingMinutes());
                case RUNNING -> Duration.ofHours(props.getRunningHours());
                case CLOSED -> Duration.ofMinutes(props.getDeprecatedMinutes());
            };
            existing.setExpireAt(Instant.now().plus(ttl));
            write(existing, ttl);
            log.debug("WSession playerUrl aktualisiert id={} playerUrl={}", id, playerUrl);
            return existing;
        });
    }

    public Optional<WSession> updateEntryPoint(String id, String entryPoint) {
        return get(id).map(existing -> {
            existing.setEntryPoint(entryPoint);
            existing.touchUpdate();
            Duration ttl = switch (existing.getStatus()) {
                case WAITING -> Duration.ofMinutes(props.getWaitingMinutes());
                case RUNNING -> Duration.ofHours(props.getRunningHours());
                case CLOSED -> Duration.ofMinutes(props.getDeprecatedMinutes());
            };
            existing.setExpireAt(Instant.now().plus(ttl));
            write(existing, ttl);
            log.debug("WSession entryPoint aktualisiert id={} entryPoint={}", id, entryPoint);
            return existing;
        });
    }

    public Optional<WSession> updateTeleportation(String id, String teleportation) {
        return get(id).map(existing -> {
            existing.setTeleportation(teleportation);
            existing.touchUpdate();
            Duration ttl = switch (existing.getStatus()) {
                case WAITING -> Duration.ofMinutes(props.getWaitingMinutes());
                case RUNNING -> Duration.ofHours(props.getRunningHours());
                case CLOSED -> Duration.ofMinutes(props.getDeprecatedMinutes());
            };
            existing.setExpireAt(Instant.now().plus(ttl));
            write(existing, ttl);
            log.debug("WSession teleportation aktualisiert id={} teleportation={}", id, teleportation);
            return existing;
        });
    }

    public Optional<WSession> updateModelSelector(String id, java.util.List<String> modelSelector) {
        return get(id).map(existing -> {
            existing.setModelSelector(ModelSelector.cleanup(modelSelector)); // remove doubles
            existing.touchUpdate();
            Duration ttl = switch (existing.getStatus()) {
                case WAITING -> Duration.ofMinutes(props.getWaitingMinutes());
                case RUNNING -> Duration.ofHours(props.getRunningHours());
                case CLOSED -> Duration.ofMinutes(props.getDeprecatedMinutes());
            };
            existing.setExpireAt(Instant.now().plus(ttl));
            write(existing, ttl);
            log.debug("WSession modelSelector aktualisiert id={} blocks={}", id,
                    modelSelector != null ? modelSelector.size() - 1 : 0); // -1 for config line
            return existing;
        });
    }

    public boolean delete(String id) {
        return Boolean.TRUE.equals(redis.delete(key(id)));
    }

    private void write(WSession session, Duration ttl) {
        var ops = redis.opsForHash();
        var k = key(session.getId());
        ops.put(k, FIELD_STATUS, session.getStatus().name());
        ops.put(k, FIELD_WORLD, session.getWorldId());
        ops.put(k, FIELD_USER, session.getPlayerId());
        if (session.getPlayerUrl() != null) {
            ops.put(k, FIELD_PLAYER_URL, session.getPlayerUrl());
        }
        if (session.getEntryPoint() != null) {
            ops.put(k, FIELD_ENTRY_POINT, session.getEntryPoint());
        }
        if (session.getTeleportation() != null) {
            ops.put(k, FIELD_TELEPORTATION, session.getTeleportation());
        }
        if (session.getModelSelector() != null) {
            try {
                // Store as JSON array string
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String modelSelectorJson = mapper.writeValueAsString(session.getModelSelector());
                ops.put(k, FIELD_MODEL_SELECTOR, modelSelectorJson);
            } catch (Exception e) {
                log.warn("Failed to serialize modelSelector for session {}: {}", session.getId(), e.getMessage());
            }
        } else {
            ops.delete(k, FIELD_MODEL_SELECTOR);
        }
        ops.put(k, FIELD_ACTOR, session.getActor());
        ops.put(k, FIELD_CREATED, session.getCreatedAt().toString());
        ops.put(k, FIELD_UPDATED, session.getUpdatedAt().toString());
        ops.put(k, FIELD_EXPIRE, session.getExpireAt().toString());
        if (ttl == null || ttl.isNegative() || ttl.isZero()) ttl = Duration.ofSeconds(1);
        redis.expire(k, ttl);
    }

    private String key(String id) { return KEY_PREFIX + "session:" + id; }

    private String randomId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i=0; i<ID_LENGTH; i++) {
            sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Räumt abgelaufene Sessions auf. Verwendet Redis SCAN um Speicher zu schonen.
     * Gibt Anzahl gelöschter Keys zurück. Stoppt wenn cleanupMaxDeletes erreicht.
     * @param cursorStart optionaler Cursor ("0" für neuen Durchgang)
     * @return neuer Cursor ("0" wenn Ende erreicht) und gelöschte Anzahl
     */
    public CleanupResult cleanupExpired(String cursorStart) {
        if (!props.isCleanupEnabled()) return new CleanupResult("0",0);
        int deleted = 0;
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
            .match("wsession:session:*")
            .count(props.getCleanupScanCount())
            .build();
        boolean usedFallback = false;
        try (var connection = redis.getConnectionFactory().getConnection()) {
            Cursor<byte[]> cursor = connection.scan(scanOptions);
            if (cursor != null) {
                while (cursor.hasNext() && deleted < props.getCleanupMaxDeletes()) {
                    String key = new String(cursor.next());
                    deleted += tryDeleteIfExpired(key);
                }
            } else {
                usedFallback = true;
            }
        } catch (Exception e) {
            log.warn("Cleanup Fehler: {}", e.getMessage());
            usedFallback = true; // auf Fallback wechseln
        }
        if (usedFallback) {
            var keys = redis.keys("wsession:session:*");
            if (keys != null) {
                for (String key : keys) {
                    if (deleted >= props.getCleanupMaxDeletes()) break;
                    deleted += tryDeleteIfExpired(key);
                }
            }
        }
        return new CleanupResult("0", deleted);
    }

    private int tryDeleteIfExpired(String key) {
        Object expireStr = redis.opsForHash().get(key, FIELD_EXPIRE);
        if (expireStr == null) return 0;
        try {
            Instant expireAt = Instant.parse(expireStr.toString());
            if (expireAt.isBefore(Instant.now())) {
                return redis.delete(key) ? 1 : 0;
            }
            return 0;
        } catch (Exception e) {
            return redis.delete(key) ? 1 : 0;
        }
    }

    /**
     * Geplante Bereinigung abgelaufener Sessions. Respektiert cleanupEnabled.
     */
    @Scheduled(fixedDelayString = "#{${world.session.cleanup-interval-seconds:60} * 1000}")
    public void scheduledCleanup() {
        if (!props.isCleanupEnabled()) return;
        try {
            var result = cleanupExpired("0");
            if (result.deleted() > 0) {
                log.debug("Scheduled cleanup removed {} expired sessions", result.deleted());
            }
        } catch (Exception e) {
            log.warn("Scheduled cleanup failed: {}", e.getMessage());
        }
    }

    public record CleanupResult(String cursor, int deleted) { }

    // ========================================================================
    // WSessionPosition Management
    // ========================================================================

    private static final String POSITION_KEY_PREFIX = "wsession:pos:"; // Namespace for position
    private static final Duration POSITION_TTL = Duration.ofMinutes(5); // Short TTL for position data

    /**
     * Update or create player position and rotation.
     * Position is stored separately from WSession with short TTL.
     *
     * @param sessionId session ID
     * @param x world x coordinate (optional)
     * @param y world y coordinate (optional)
     * @param z world z coordinate (optional)
     * @param chunkX chunk x coordinate (optional)
     * @param chunkZ chunk z coordinate (optional)
     * @param yaw rotation yaw in degrees (optional)
     * @param pitch rotation pitch in degrees (optional)
     * @return updated position
     */
    public WSessionPosition updatePosition(String sessionId, Double x, Double y, Double z,
                                          Integer chunkX, Integer chunkZ,
                                          Double yaw, Double pitch) {
        // Get existing or create new
        WSessionPosition position = getPosition(sessionId).orElse(
                WSessionPosition.builder().sessionId(sessionId).build()
        );

        // Update position if provided
        if (x != null) position.setX(x);
        if (y != null) position.setY(y);
        if (z != null) position.setZ(z);

        // Update chunk coordinates if provided
        if (chunkX != null) position.setChunkX(chunkX);
        if (chunkZ != null) position.setChunkZ(chunkZ);

        // Update rotation if provided
        if (yaw != null) position.setYaw(yaw);
        if (pitch != null) position.setPitch(pitch);

        position.touchUpdate();

        // Store in Redis
        writePosition(position);

        log.trace("Updated position for session {}: pos=({}, {}, {}), chunk=({}, {}), rot=(yaw:{}, pitch:{})",
                sessionId, x, y, z, chunkX, chunkZ, yaw, pitch);

        return position;
    }

    /**
     * Get player position and rotation.
     *
     * @param sessionId session ID
     * @return position, or empty if not found
     */
    public Optional<WSessionPosition> getPosition(String sessionId) {
        var ops = redis.opsForHash();
        var key = positionKey(sessionId);
        var map = ops.entries(key);

        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }

        try {
            WSessionPosition position = WSessionPosition.builder()
                    .sessionId(sessionId)
                    .x(parseDouble(map.get("x")))
                    .y(parseDouble(map.get("y")))
                    .z(parseDouble(map.get("z")))
                    .chunkX(parseInteger(map.get("chunkX")))
                    .chunkZ(parseInteger(map.get("chunkZ")))
                    .yaw(parseDouble(map.get("yaw")))
                    .pitch(parseDouble(map.get("pitch")))
                    .updatedAt(map.get("updatedAt") != null ?
                            Instant.parse((String) map.get("updatedAt")) : null)
                    .build();

            return Optional.of(position);
        } catch (Exception e) {
            log.warn("Failed to parse position for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delete player position.
     *
     * @param sessionId session ID
     * @return true if deleted
     */
    public boolean deletePosition(String sessionId) {
        return Boolean.TRUE.equals(redis.delete(positionKey(sessionId)));
    }

    /**
     * Write position to Redis.
     */
    private void writePosition(WSessionPosition position) {
        var ops = redis.opsForHash();
        var key = positionKey(position.getSessionId());

        // Store only non-null values
        if (position.getX() != null) ops.put(key, "x", position.getX().toString());
        if (position.getY() != null) ops.put(key, "y", position.getY().toString());
        if (position.getZ() != null) ops.put(key, "z", position.getZ().toString());
        if (position.getChunkX() != null) ops.put(key, "chunkX", position.getChunkX().toString());
        if (position.getChunkZ() != null) ops.put(key, "chunkZ", position.getChunkZ().toString());
        if (position.getYaw() != null) ops.put(key, "yaw", position.getYaw().toString());
        if (position.getPitch() != null) ops.put(key, "pitch", position.getPitch().toString());
        if (position.getUpdatedAt() != null) ops.put(key, "updatedAt", position.getUpdatedAt().toString());

        // Set short TTL
        redis.expire(key, POSITION_TTL);
    }

    private String positionKey(String sessionId) {
        return POSITION_KEY_PREFIX + sessionId;
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===========================
    // EditState Management
    // ===========================

    private static final String EDITSTATE_KEY_PREFIX = "wsession:editstate:";
    private static final Duration EDITSTATE_TTL = Duration.ofHours(24);

    private String editStateKey(String sessionId) {
        return EDITSTATE_KEY_PREFIX + sessionId;
    }

    /**
     * Update or create edit state for a session.
     *
     * @param sessionId session ID
     * @param editState edit state to store
     * @return stored edit state
     */
    public EditState updateEditState(String sessionId, EditState editState) {
        editState.setLastUpdated(Instant.now());
        writeEditState(sessionId, editState);
        log.trace("Updated edit state for session {}", sessionId);
        return editState;
    }

    /**
     * Get edit state for a session.
     *
     * @param sessionId session ID
     * @return edit state, or empty if not found
     */
    public Optional<EditState> getEditState(String sessionId) {
        var ops = redis.opsForHash();
        var key = editStateKey(sessionId);
        var map = ops.entries(key);

        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }

        try {
            EditState editState = EditState.builder()
                    .editMode(parseBoolean(map.get("editMode")))
                    .editAction(map.get("editAction") != null ?
                            de.mhus.nimbus.generated.types.EditAction.valueOf((String) map.get("editAction")) : null)
                    .selectedLayer((String) map.get("selectedLayer"))
                    .layerDataId((String) map.get("layerDataId"))
                    .selectedModelId((String) map.get("selectedModelId"))
                    .modelName((String) map.get("modelName"))
                    .selectedGroup(parseInteger(map.get("selectedGroup")) != null ?
                            parseInteger(map.get("selectedGroup")) : 0)
                    .lastUpdated(map.get("lastUpdated") != null ?
                            Instant.parse((String) map.get("lastUpdated")) : null)
                    .worldId((String) map.get("worldId"))
                    .build();

            return Optional.of(editState);
        } catch (Exception e) {
            log.warn("Failed to parse edit state for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delete edit state for a session.
     *
     * @param sessionId session ID
     * @return true if deleted
     */
    public boolean deleteEditState(String sessionId) {
        return Boolean.TRUE.equals(redis.delete(editStateKey(sessionId)));
    }

    /**
     * Write edit state to Redis.
     */
    private void writeEditState(String sessionId, EditState editState) {
        var ops = redis.opsForHash();
        var key = editStateKey(sessionId);

        // Store all fields (store null as empty string)
        ops.put(key, "editMode", String.valueOf(editState.isEditMode()));

        if (editState.getEditAction() != null) {
            ops.put(key, "editAction", editState.getEditAction().name());
        } else {
            ops.delete(key, "editAction");
        }

        if (editState.getSelectedLayer() != null) {
            ops.put(key, "selectedLayer", editState.getSelectedLayer());
        } else {
            ops.delete(key, "selectedLayer");
        }

        if (editState.getLayerDataId() != null) {
            ops.put(key, "layerDataId", editState.getLayerDataId());
        } else {
            ops.delete(key, "layerDataId");
        }

        if (editState.getSelectedModelId() != null) {
            ops.put(key, "selectedModelId", editState.getSelectedModelId());
        } else {
            ops.delete(key, "selectedModelId");
        }

        if (editState.getModelName() != null) {
            ops.put(key, "modelName", editState.getModelName());
        } else {
            ops.delete(key, "modelName");
        }

        ops.put(key, "selectedGroup", String.valueOf(editState.getSelectedGroup()));

        if (editState.getLastUpdated() != null) {
            ops.put(key, "lastUpdated", editState.getLastUpdated().toString());
        }

        if (editState.getWorldId() != null) {
            ops.put(key, "worldId", editState.getWorldId());
        }

        // Set TTL
        redis.expire(key, EDITSTATE_TTL);

        log.trace("Wrote edit state to Redis: sessionId={}, layer={}, layerDataId={}, modelName={}",
                sessionId, editState.getSelectedLayer(), editState.getLayerDataId(), editState.getModelName());
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) return false;
        return Boolean.parseBoolean(value.toString());
    }

    // ========== Player Session Management ==========

    private static final String PLAYER_SESSION_KEY_PREFIX = "region:";
    private static final Duration PLAYER_SESSION_TTL = Duration.ofHours(24);

    /**
     * Store player session in Redis: region:<regionId>:player:<playerId>
     * Contains sessionId and localIp with 24h TTL.
     *
     * @param regionId region ID
     * @param playerId player ID
     * @param sessionId session ID
     */
    private void storePlayerSession(String regionId, String playerId, String sessionId) {
        String key = playerSessionKey(regionId, playerId);
        var ops = redis.opsForHash();

        ops.put(key, "sessionId", sessionId);
        ops.put(key, "localIp", "local"); // TODO: Get actual local IP if needed

        redis.expire(key, PLAYER_SESSION_TTL);

        log.debug("Stored player session: regionId={}, playerId={}, sessionId={}", regionId, playerId, sessionId);
    }

    /**
     * Delete player session from Redis.
     *
     * @param regionId region ID
     * @param playerId player ID
     * @return true if deleted
     */
    public boolean deletePlayerSession(String regionId, String playerId) {
        String key = playerSessionKey(regionId, playerId);
        Boolean result = redis.delete(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Get player session from Redis.
     *
     * @param regionId region ID
     * @param playerId player ID
     * @return sessionId if found, empty otherwise
     */
    public Optional<String> getPlayerSessionId(String regionId, String playerId) {
        String key = playerSessionKey(regionId, playerId);
        Object sessionId = redis.opsForHash().get(key, "sessionId");
        return Optional.ofNullable(sessionId != null ? sessionId.toString() : null);
    }

    /**
     * Build Redis key for player session.
     */
    private String playerSessionKey(String regionId, String playerId) {
        return PLAYER_SESSION_KEY_PREFIX + regionId + ":player:" + playerId;
    }

    // ========== BlockRegister Management ==========

    private static final String BLOCKREGISTER_KEY_PREFIX = "wsession:blockregister:";
    private static final Duration BLOCKREGISTER_TTL = Duration.ofHours(24);

    private String blockRegisterKey(String sessionId) {
        return BLOCKREGISTER_KEY_PREFIX + sessionId;
    }

    /**
     * Update block register for a session.
     * Stores marked block information for copy/paste operations.
     *
     * @param sessionId session ID
     * @param blockRegister block register data
     * @return updated block register
     */
    public BlockRegister updateBlockRegister(String sessionId, BlockRegister blockRegister) {
        if (blockRegister == null) {
            deleteBlockRegister(sessionId);
            return null;
        }

        writeBlockRegister(sessionId, blockRegister);
        log.debug("BlockRegister updated: sessionId={}", sessionId);
        return blockRegister;
    }

    /**
     * Get block register for a session.
     *
     * @param sessionId session ID
     * @return block register, or empty if not found
     */
    public Optional<BlockRegister> getBlockRegister(String sessionId) {
        var ops = redis.opsForHash();
        var key = blockRegisterKey(sessionId);
        var map = ops.entries(key);

        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }

        try {
            // Parse block JSON
            String blockJson = (String) map.get("block");
            if (blockJson == null || blockJson.isBlank()) {
                log.warn("BlockRegister has no block data: sessionId={}", sessionId);
                return Optional.empty();
            }

            Block block = mapper.readValue(blockJson, Block.class);

            BlockRegister blockRegister = BlockRegister.builder()
                    .block(block)
                    .layer((String) map.get("layer"))
                    .group(parseInteger(map.get("group")))
                    .groupName((String) map.get("groupName"))
                    .readOnly(parseBoolean(map.get("readOnly")))
                    .build();

            return Optional.of(blockRegister);
        } catch (Exception e) {
            log.warn("Failed to parse block register for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delete block register for a session.
     *
     * @param sessionId session ID
     * @return true if deleted
     */
    public boolean deleteBlockRegister(String sessionId) {
        return Boolean.TRUE.equals(redis.delete(blockRegisterKey(sessionId)));
    }

    /**
     * Write block register to Redis.
     */
    private void writeBlockRegister(String sessionId, BlockRegister blockRegister) {
        var ops = redis.opsForHash();
        var key = blockRegisterKey(sessionId);

        try {
            // Store block as JSON (required)
            String blockJson = mapper.writeValueAsString(blockRegister.getBlock());
            ops.put(key, "block", blockJson);

            // Store optional layer
            if (blockRegister.getLayer() != null) {
                ops.put(key, "layer", blockRegister.getLayer());
            } else {
                ops.delete(key, "layer");
            }

            // Store optional group
            if (blockRegister.getGroup() != null) {
                ops.put(key, "group", String.valueOf(blockRegister.getGroup()));
            } else {
                ops.delete(key, "group");
            }

            // Store optional groupName
            if (blockRegister.getGroupName() != null) {
                ops.put(key, "groupName", blockRegister.getGroupName());
            } else {
                ops.delete(key, "groupName");
            }

            // Store optional readOnly
            if (blockRegister.getReadOnly() != null) {
                ops.put(key, "readOnly", String.valueOf(blockRegister.getReadOnly()));
            } else {
                ops.delete(key, "readOnly");
            }

            // Set TTL
            redis.expire(key, BLOCKREGISTER_TTL);

            log.trace("Wrote block register to Redis: sessionId={}, layer={}, group={}",
                    sessionId, blockRegister.getLayer(), blockRegister.getGroup());
        } catch (Exception e) {
            log.error("Failed to write block register: sessionId={}", sessionId, e);
        }
    }
}
