package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WLease entities.
 */
@Repository
public interface WLeaseRepository extends MongoRepository<WLease, String> {

    Optional<WLease> findByLeaseId(String leaseId);

    List<WLease> findByWorldIdAndPlayerId(String worldId, String playerId);

    List<WLease> findByWorldIdAndPlayerIdAndType(String worldId, String playerId, String type);

    Optional<WLease> findByWorldIdAndPlayerIdAndTypeAndResourceId(String worldId, String playerId, String type, String resourceId);

    void deleteByLeaseId(String leaseId);

    void deleteByWorldIdAndPlayerId(String worldId, String playerId);

    void deleteByWorldId(String worldId);
}
