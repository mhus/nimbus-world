package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WBackdrop entries (unique: worldId + backdropId).
 */
@Service
@RequiredArgsConstructor
public class BackdropResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "backdrop";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_backdrops", worldId.getId(), name(),
                doc -> {
                    String backdropId = doc.getString("backdropId");
                    return backdropId != null ? doc.getString("worldId") + "|" + backdropId : null;
                }
        );
    }
}
