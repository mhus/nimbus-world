package de.mhus.nimbus.world.shared.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RRegionRepository extends MongoRepository<RRegion, String> {
    Optional<RRegion> findByName(String name);
//    Optional<RRegion> findByApiUrl(String apiUrl);
    boolean existsByName(String name);

    @Query(value = "{}", fields = "{ '_id': 1 }")
    List<String> findAllIds();

//    Optional<String> getRegionNameById(String regionId);

    Optional<String> getRegionNameByIdAndEnabled(String regionId, boolean enabled);

}
