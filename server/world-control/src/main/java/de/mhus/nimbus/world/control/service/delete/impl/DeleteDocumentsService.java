package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteDocumentsService implements DeleteWorldResources {

    private final WDocumentService documentService;

    @Override
    public String name() {
        return "documents";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting documents for world {}", worldId);
        int deleted = documentService.deleteAllByWorldId(worldId);
        log.info("Deleted {} documents for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return documentService.findDistinctWorldIds();
    }
}
