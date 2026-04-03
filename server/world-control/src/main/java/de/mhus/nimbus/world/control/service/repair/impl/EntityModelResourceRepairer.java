package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WEntityModel entries (unique: worldId + modelId).
 */
@Service
@RequiredArgsConstructor
public class EntityModelResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "entitymodel";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_entity_models", worldId.getId(), name(),
                doc -> {
                    String modelId = doc.getString("name");
                    return modelId != null ? doc.getString("worldId") + "|" + modelId : null;
                }
        );
    }
}
