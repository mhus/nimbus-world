package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.world.DuplicateRepairResult;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WAnything entries (unique: worldId + collection + name).
 * Delegates to the owner service which has data ownership over WAnything.
 */
@Service
@RequiredArgsConstructor
public class AnythingResourceRepairer implements ResourceRepairer {

    private final WAnythingService anythingService;

    @Override
    public String name() {
        return "anything";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        DuplicateRepairResult result = anythingService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
