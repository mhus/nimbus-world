package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.world.DuplicateRepairResult;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WLogicRule entries (unique: worldId + name).
 * Delegates to the owner service which has data ownership over WLogicRule.
 */
@Service
@RequiredArgsConstructor
public class LogicRuleResourceRepairer implements ResourceRepairer {

    private final WLogicRuleService logicRuleService;

    @Override
    public String name() {
        return "logic-rule";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        DuplicateRepairResult result = logicRuleService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
