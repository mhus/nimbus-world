package de.mhus.nimbus.world.shared.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RCharacterRepository extends MongoRepository<RCharacter, String> {
    Optional<RCharacter> findByUserIdAndRegionIdAndName(String userId, String regionId, String name);

    boolean existsByUserIdAndRegionIdAndName(String userId, String regionId, String name);

    List<RCharacter> findByUserIdAndRegionId(String userId, String regionId);

    List<RCharacter> findByRegionId(String regionId);

    Optional<RCharacter> findByRegionIdAndName(String regionId, String name);
}
