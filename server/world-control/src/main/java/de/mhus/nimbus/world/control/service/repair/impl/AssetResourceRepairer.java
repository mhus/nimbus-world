package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import de.mhus.nimbus.world.shared.world.AssetRepairResult;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Repair implementation for assets.
 * Finds and removes duplicate entries (e.g., with and without _schema field),
 * ensures uniqueness by worldId + path, and removes orphaned storage references.
 * Delegates to the owner service which has data ownership over SAsset and the
 * associated storage.
 */
@Service
@RequiredArgsConstructor
public class AssetResourceRepairer implements ResourceRepairer {

    private final SAssetService assetService;

    @Override
    public String name() {
        return "asset";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        AssetRepairResult result = assetService.repairDuplicates(worldId.getId());
        return new ResourceRepairService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
