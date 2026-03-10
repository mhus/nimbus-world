package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WBlockType entries (unique: worldId + blockId).
 */
@Service
@RequiredArgsConstructor
public class BlockTypeResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "blocktype";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_blocktypes", worldId.getId(), name(),
                doc -> {
                    Object blockId = doc.get("blockId");
                    return blockId != null ? doc.getString("worldId") + "|" + blockId : null;
                }
        );
    }
}
