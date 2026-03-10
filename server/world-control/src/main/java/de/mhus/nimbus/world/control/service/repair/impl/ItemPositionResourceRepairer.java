package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WItemPosition entries (unique: worldId + itemId).
 */
@Service
@RequiredArgsConstructor
public class ItemPositionResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "itemposition";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_item_positions", worldId.getId(), name(),
                doc -> {
                    String itemId = doc.getString("itemId");
                    return itemId != null ? doc.getString("worldId") + "|" + itemId : null;
                }
        );
    }
}
