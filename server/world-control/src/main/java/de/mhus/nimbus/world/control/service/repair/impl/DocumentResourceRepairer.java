package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WDocument entries (unique: worldId + documentId).
 */
@Service
@RequiredArgsConstructor
public class DocumentResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "document";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_documents", worldId.getId(), name(),
                doc -> {
                    String documentId = doc.getString("documentId");
                    return documentId != null ? doc.getString("worldId") + "|" + documentId : null;
                }
        );
    }
}
