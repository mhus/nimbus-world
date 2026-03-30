package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicFlag;
import de.mhus.nimbus.world.shared.world.WLogicFlagRepository;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateLogicRulesService implements DuplicateToWorld {

    private final WLogicRuleRepository ruleRepository;
    private final WLogicFlagRepository flagRepository;

    @Override
    public String name() {
        return "logic-rules";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating logic rules from world {} to {}", sourceWorldId, targetWorldId);

        // Duplicate rules
        List<WLogicRule> sourceRules = ruleRepository.findByWorldId(sourceWorldId);
        int ruleCount = 0;
        for (WLogicRule source : sourceRules) {
            WLogicRule target = WLogicRule.builder()
                    .worldId(targetWorldId)
                    .name(source.getName())
                    .description(source.getDescription())
                    .rulePackage(source.getRulePackage())
                    .affected(source.getAffected() != null ? new ArrayList<>(source.getAffected()) : null)
                    .spelCondition(source.getSpelCondition())
                    .effects(source.getEffects() != null ? new ArrayList<>(source.getEffects()) : null)
                    .epoches(source.getEpoches() != null ? new ArrayList<>(source.getEpoches()) : null)
                    .enabled(source.isEnabled())
                    .priority(source.getPriority())
                    .testFlags(source.getTestFlags())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            ruleRepository.save(target);
            ruleCount++;
        }

        // Duplicate flag definitions
        List<WLogicFlag> sourceFlags = flagRepository.findByWorldId(sourceWorldId);
        int flagCount = 0;
        for (WLogicFlag source : sourceFlags) {
            WLogicFlag target = WLogicFlag.builder()
                    .worldId(targetWorldId)
                    .flagName(source.getFlagName())
                    .defaultValue(source.getDefaultValue())
                    .type(source.getType())
                    .description(source.getDescription())
                    .autoCreated(source.isAutoCreated())
                    .createdAt(Instant.now())
                    .build();
            flagRepository.save(target);
            flagCount++;
        }

        log.info("Duplicated {} logic rules and {} flag definitions from {} to {}",
                ruleCount, flagCount, sourceWorldId, targetWorldId);
    }
}
