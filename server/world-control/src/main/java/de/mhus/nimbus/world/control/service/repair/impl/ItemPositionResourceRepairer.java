package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.world.DuplicateRepairResult;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WItemPosition entries (unique: worldId + itemId).
 * Delegates to the owner service which has data ownership over WItemPosition.
 */
@Service
@RequiredArgsConstructor
public class ItemPositionResourceRepairer implements ResourceRepairer {

    private final WItemPositionService itemPositionService;

    @Override
    public String name() {
        return "itemposition";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        DuplicateRepairResult result = itemPositionService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
