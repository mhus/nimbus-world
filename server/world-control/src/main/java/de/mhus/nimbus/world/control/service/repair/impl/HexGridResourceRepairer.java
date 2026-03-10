package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WHexGrid entries (unique: worldId + position).
 */
@Service
@RequiredArgsConstructor
public class HexGridResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "hexgrid";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_hexgrids", worldId.getId(), name(),
                doc -> {
                    String position = doc.getString("position");
                    return position != null ? doc.getString("worldId") + "|" + position : null;
                }
        );
    }
}
