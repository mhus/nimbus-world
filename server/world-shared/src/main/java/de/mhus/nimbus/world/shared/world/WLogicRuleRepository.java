package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WLogicRuleRepository extends MongoRepository<WLogicRule, String> {

    // --- EPOCH-UNFILTERED: use for editor/admin context only ---

    List<WLogicRule> findByWorldIdAndEnabledTrue(String worldId);

    List<WLogicRule> findByWorldIdAndAffectedInAndEnabledTrue(String worldId, List<String> affectedFlags);

    Optional<WLogicRule> findByWorldIdAndName(String worldId, String name);

    List<WLogicRule> findByWorldId(String worldId);

    List<WLogicRule> findByWorldIdAndRulePackage(String worldId, String rulePackage);

    void deleteByWorldId(String worldId);

    // --- EPOCH-AWARE: use for gameplay/logic machine context ---

    List<WLogicRule> findByWorldIdAndEnabledTrueAndEpochesContaining(String worldId, int epoch);

    List<WLogicRule> findByWorldIdAndAffectedInAndEnabledTrueAndEpochesContaining(
            String worldId, List<String> affectedFlags, int epoch);
}
