package de.mhus.nimbus.world.shared.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RUserItemsRepository extends MongoRepository<RUserItems, String> {

    List<RUserItems> findByUserIdAndRegionId(String userId, String regionId);

    List<RUserItems> findByUserId(String userId);

    Optional<RUserItems> findByUserIdAndRegionIdAndItemId(String userId, String regionId, String itemId);

    void deleteByUserIdAndRegionIdAndItemId(String userId, String regionId, String itemId);

    void deleteByUserIdAndRegionId(String userId, String regionId);

    boolean existsByUserIdAndRegionIdAndItemId(String userId, String regionId, String itemId);
}
