package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WLogicStateDefRepository extends MongoRepository<WLogicStateDef, String> {

    Optional<WLogicStateDef> findByWorldIdAndName(String worldId, String name);

    List<WLogicStateDef> findByWorldId(String worldId);

    void deleteByWorldId(String worldId);
}
