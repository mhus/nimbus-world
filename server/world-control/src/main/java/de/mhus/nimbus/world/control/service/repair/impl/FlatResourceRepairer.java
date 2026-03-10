package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WFlat entries (unique: worldId + layerDataId + flatId).
 */
@Service
@RequiredArgsConstructor
public class FlatResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_flats", worldId.getId(), name(),
                doc -> {
                    String layerDataId = doc.getString("layerDataId");
                    String flatId = doc.getString("flatId");
                    if (flatId == null) return null;
                    return doc.getString("worldId") + "|" + (layerDataId != null ? layerDataId : "") + "|" + flatId;
                }
        );
    }
}
