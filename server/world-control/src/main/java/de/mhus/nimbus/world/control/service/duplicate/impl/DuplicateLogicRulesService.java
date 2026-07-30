package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateLogicRulesService implements DuplicateToWorld {

    private final WLogicRuleService logicRuleService;

    @Override
    public String name() {
        return "logic-rules";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating logic rules from world {} to {}", sourceWorldId, targetWorldId);
        logicRuleService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
