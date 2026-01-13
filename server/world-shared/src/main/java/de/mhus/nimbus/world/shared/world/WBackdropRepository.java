package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WBackdrop entities.
 */
@Repository
public interface WBackdropRepository extends MongoRepository<WBackdrop, String> {

    Optional<WBackdrop> findByWorldIdAndBackdropId(String worldId, String backdropId);

    List<WBackdrop> findByWorldId(String worldId);

    List<WBackdrop> findByWorldIdAndEnabled(String worldId, boolean enabled);

    boolean existsByWorldIdAndBackdropId(String worldId, String backdropId);

    void deleteByWorldIdAndBackdropId(String worldId, String backdropId);
}
