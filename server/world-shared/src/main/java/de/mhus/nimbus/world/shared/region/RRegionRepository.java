package de.mhus.nimbus.world.shared.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RRegionRepository extends MongoRepository<RRegion, String> {
    Optional<RRegion> findByName(String name);
    //    Optional<RRegion> findByApiUrl(String apiUrl);
    boolean existsByName(String name);

    /**
     * All region ids. Scalar aggregation, same pattern as
     * WDirtyChunkRepository.findDistinctWorldIds(): the pipeline projects a
     * single-field document, which Spring Data maps to the declared scalar
     * return type. The previous @Query(fields="{ '_id': 1 }") form returned
     * List<String> too, but its AOT-generated code did not compile (the
     * generator ignored the field projection), so this form keeps the
     * behavior and makes the repository native-image capable. The projection
     * alias must not be named 'id' - the pipeline field mapper resolves
     * property names, and 'id' would collide with the '_id' exclusion.
     * Verified by RRegionRepositoryIntegrationTest against a real MongoDB.
     */
    @Aggregation(pipeline = {"{ '$project': { 'regionId': '$_id', '_id': 0 } }"})
    List<String> findAllIds();

    //    Optional<String> getRegionNameById(String regionId);

    /**
     * A region by id and enabled state. Derived query on the entity - no
     * aggregation on purpose: Optional<String> + @Aggregation cannot be
     * expressed by the AOT code generator (it would return the bare first
     * element instead of an Optional), and the AOT generator also mishandles
     * scalar @Query projections. The name mapping happens in
     * RRegionService.getRegionNameById.
     */
    Optional<RRegion> findByIdAndEnabled(String id, boolean enabled);
}
