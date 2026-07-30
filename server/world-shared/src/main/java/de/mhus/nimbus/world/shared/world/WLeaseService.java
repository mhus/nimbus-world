package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing WLease entities.
 * Uses atomic MongoDB operations for thread-safe lease management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLeaseService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final WLeaseRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Acquire a lease for a player to access a resource.
     * If a lease for the same (worldId, playerId, type, resourceId) already exists,
     * it is atomically updated (renewed). Otherwise a new lease is created.
     *
     * @param worldId    World identifier
     * @param playerId   Player identifier
     * @param type       Lease type (e.g. "crafting-station", "dialog")
     * @param resourceId Resource being accessed (entityId, chestName, etc.)
     * @param title      Optional display title
     * @param leaseData  Data needed by the widget
     * @return the acquired lease
     */
    public WLease acquire(String worldId, String playerId, String type, String resourceId, String title, Map<String, Object> leaseData) {
        return acquire(worldId, playerId, type, resourceId, title, leaseData, DEFAULT_TTL);
    }

    /**
     * Acquire a lease with custom TTL.
     */
    public WLease acquire(String worldId, String playerId, String type, String resourceId, String title, Map<String, Object> leaseData, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(playerId)
                .and("type").is(type)
                .and("resourceId").is(resourceId));

        Update update = new Update()
                .set("title", title)
                .set("leaseData", leaseData)
                .set("expiresAt", expiresAt)
                .setOnInsert("worldId", worldId)
                .setOnInsert("playerId", playerId)
                .setOnInsert("type", type)
                .setOnInsert("resourceId", resourceId)
                .setOnInsert("leaseId", UUID.randomUUID().toString())
                .setOnInsert("createdAt", now);

        WLease lease = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                WLease.class
        );

        log.debug("Acquired lease: worldId={}, playerId={}, type={}, resourceId={}, leaseId={}",
                worldId, playerId, type, resourceId, lease.getLeaseId());
        return lease;
    }

    /**
     * Find a lease by its leaseId.
     */
    public Optional<WLease> findByLeaseId(String leaseId) {
        return repository.findByLeaseId(leaseId);
    }

    /**
     * Find all leases for a player in a world with a specific type.
     */
    public List<WLease> findByWorldIdAndPlayerIdAndType(String worldId, String playerId, String type) {
        return repository.findByWorldIdAndPlayerIdAndType(worldId, playerId, type);
    }

    /**
     * Validate a lease: checks that leaseId exists, matches worldId and playerId,
     * and optionally matches the expected type.
     *
     * @return the lease if valid, empty otherwise
     */
    public Optional<WLease> validate(String leaseId, String worldId, String playerId, String expectedType) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId)
                .and("worldId").is(worldId));

        WLease lease = mongoTemplate.findOne(query, WLease.class);
        if (lease == null) return Optional.empty();

        // Reject expired leases explicitly: the MongoDB TTL reaper only removes
        // documents periodically, so an expired lease may still be present here.
        if (lease.getExpiresAt() == null || !lease.getExpiresAt().isAfter(Instant.now())) {
            return Optional.empty();
        }

        // Validate player (supports multiple formats like "userId", "@userId", "userId:xxx")
        String lpid = lease.getPlayerId();
        if (!lpid.equals(playerId)
                && !lpid.startsWith(playerId + ":")
                && !lpid.equals("@" + playerId)
                && !lpid.startsWith("@" + playerId + ":")) {
            return Optional.empty();
        }

        if (expectedType != null && !expectedType.equals(lease.getType())) {
            return Optional.empty();
        }

        return Optional.of(lease);
    }

    /**
     * Atomically set a single key in leaseData.
     */
    public boolean setLeaseDataValue(String leaseId, String key, Object value) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId));
        Update update = new Update()
                .set("leaseData." + key, value);

        var result = mongoTemplate.updateFirst(query, update, WLease.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically set multiple keys in leaseData.
     */
    public boolean setLeaseDataValues(String leaseId, Map<String, Object> values) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId));
        Update update = new Update();

        for (var entry : values.entrySet()) {
            update.set("leaseData." + entry.getKey(), entry.getValue());
        }

        var result = mongoTemplate.updateFirst(query, update, WLease.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically increment a numeric value in leaseData.
     */
    public boolean incLeaseDataValue(String leaseId, String key, int delta) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId));
        Update update = new Update()
                .inc("leaseData." + key, delta);

        var result = mongoTemplate.updateFirst(query, update, WLease.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically replace the entire leaseData map.
     */
    public boolean replaceLeaseData(String leaseId, Map<String, Object> leaseData) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId));
        Update update = new Update()
                .set("leaseData", leaseData);

        var result = mongoTemplate.updateFirst(query, update, WLease.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Release (delete) a lease by leaseId.
     */
    public boolean release(String leaseId) {
        Query query = new Query(Criteria.where("leaseId").is(leaseId));
        var result = mongoTemplate.remove(query, WLease.class);
        if (result.getDeletedCount() > 0) {
            log.debug("Released lease: leaseId={}", leaseId);
            return true;
        }
        return false;
    }

    /**
     * Release all leases for a player in a world.
     */
    public void releaseByWorldIdAndPlayerId(String worldId, String playerId) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(playerId));
        var result = mongoTemplate.remove(query, WLease.class);
        log.debug("Released {} leases: worldId={}, playerId={}", result.getDeletedCount(), worldId, playerId);
    }

    /**
     * Release all leases for a world (instance cleanup).
     */
    public void releaseByWorldId(String worldId) {
        Query query = new Query(Criteria.where("worldId").is(worldId));
        var result = mongoTemplate.remove(query, WLease.class);
        log.info("Released {} leases for worldId={}", result.getDeletedCount(), worldId);
    }
}
