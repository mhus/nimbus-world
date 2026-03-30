package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WLogicRuleRepository extends MongoRepository<WLogicRule, String> {

    List<WLogicRule> findByWorldIdAndEnabledTrue(String worldId);

    List<WLogicRule> findByWorldIdAndAffectedInAndEnabledTrue(String worldId, List<String> affectedFlags);

    Optional<WLogicRule> findByWorldIdAndName(String worldId, String name);

    List<WLogicRule> findByWorldId(String worldId);

    void deleteByWorldId(String worldId);
}
