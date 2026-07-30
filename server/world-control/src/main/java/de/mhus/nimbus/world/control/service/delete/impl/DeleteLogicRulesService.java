package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteLogicRulesService implements DeleteWorldResources {

    private final WLogicRuleService logicRuleService;

    @Override
    public String name() {
        return "logic-rules";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting logic rules and flags for world {}", worldId);
        int deleted = logicRuleService.deleteAllByWorldId(worldId);
        log.info("Deleted {} logic documents (rules + flag definitions) for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return logicRuleService.findDistinctWorldIds();
    }
}
