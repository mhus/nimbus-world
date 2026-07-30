package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate documents from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDocumentsService implements DuplicateToWorld {

    private final WDocumentService documentService;

    @Override
    public String name() {
        return "documents";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating documents from world {} to {}", sourceWorldId, targetWorldId);
        int duplicatedCount = documentService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} documents from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
