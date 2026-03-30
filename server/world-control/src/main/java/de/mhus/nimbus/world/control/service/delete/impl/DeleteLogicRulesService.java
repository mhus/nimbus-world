package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WLogicFlag;
import de.mhus.nimbus.world.shared.world.WLogicFlagRepository;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteLogicRulesService implements DeleteWorldResources {

    private final WLogicRuleRepository ruleRepository;
    private final WLogicFlagRepository flagRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "logic-rules";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting logic rules and flags for world {}", worldId);

        List<WLogicRule> rules = ruleRepository.findByWorldId(worldId);
        ruleRepository.deleteAll(rules);

        List<WLogicFlag> flags = flagRepository.findByWorldId(worldId);
        flagRepository.deleteAll(flags);

        log.info("Deleted {} logic rules and {} flag definitions for world {}",
                rules.size(), flags.size(), worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();

        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(), "worldId", WLogicRule.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(), "worldId", WLogicFlag.class, String.class));

        return worldIds.stream().sorted().toList();
    }
}
