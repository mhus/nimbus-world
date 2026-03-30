package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WLogicFlagRepository extends MongoRepository<WLogicFlag, String> {

    Optional<WLogicFlag> findByWorldIdAndFlagName(String worldId, String flagName);

    List<WLogicFlag> findByWorldId(String worldId);

    void deleteByWorldId(String worldId);
}
