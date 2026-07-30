package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.world.DuplicateRepairResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WFlat entries (unique: worldId + layerDataId + flatId).
 * Delegates to the owner service which has data ownership over WFlat.
 */
@Service
@RequiredArgsConstructor
public class FlatResourceRepairer implements ResourceRepairer {

    private final WFlatService flatService;

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        DuplicateRepairResult result = flatService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
