package de.mhus.nimbus.world.control.service.repair.impl;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Repairs duplicate WAnything entries (unique: worldId + collection + name).
 */
@Service
@RequiredArgsConstructor
public class AnythingResourceRepairer implements ResourceRepairer {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "anything";
    }

    @Override
    public ResourceRepairService.ProcessResult repair(WorldId worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, "w_anything", worldId.getId(), name(),
                doc -> {
                    String collection = doc.getString("collection");
                    String docName = doc.getString("name");
                    if (docName == null) return null;
                    return doc.getString("worldId") + "|" + (collection != null ? collection : "") + "|" + docName;
                }
        );
    }
}
