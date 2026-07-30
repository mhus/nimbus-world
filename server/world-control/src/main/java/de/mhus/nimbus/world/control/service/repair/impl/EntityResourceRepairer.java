package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.world.DuplicateRepairResult;
import de.mhus.nimbus.world.shared.world.WEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WEntity entries (unique: worldId + entityId).
 * Delegates to the owner service which has data ownership over WEntity.
 */
@Service
@RequiredArgsConstructor
public class EntityResourceRepairer implements ResourceRepairer {

    private final WEntityService entityService;

    @Override
    public String name() {
        return "entity";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        DuplicateRepairResult result = entityService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
