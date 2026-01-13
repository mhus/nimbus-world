package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WItemType entities.
 */
@Repository
public interface WItemTypeRepository extends MongoRepository<WItemType, String> {

    Optional<WItemType> findByWorldIdAndItemType(String worldId, String itemType);

    List<WItemType> findByWorldId(String worldId);

    List<WItemType> findByWorldIdAndEnabled(String worldId, boolean enabled);

    boolean existsByWorldIdAndItemType(String worldId, String itemType);

    void deleteByWorldIdAndItemType(String worldId, String itemType);
}
