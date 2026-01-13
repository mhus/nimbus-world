package de.mhus.nimbus.world.shared.region;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RCharacterRepository extends MongoRepository<RCharacter, String> {
    Optional<RCharacter> findByUserIdAndRegionIdAndName(String userId, String regionId, String name);
    boolean existsByUserIdAndRegionIdAndName(String userId, String regionId, String name);
    List<RCharacter> findByUserIdAndRegionId(String userId, String regionId);
    List<RCharacter> findByRegionId(String regionId);
}
