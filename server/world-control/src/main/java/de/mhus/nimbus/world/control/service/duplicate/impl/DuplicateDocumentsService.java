package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

/**
 * Service to duplicate documents from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDocumentsService implements DuplicateToWorld {

    private final WDocumentRepository documentRepository;

    @Override
    public String name() {
        return "documents";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating documents from world {} to {}", sourceWorldId, targetWorldId);

        List<WDocument> sourceDocuments = documentRepository.findByWorldId(sourceWorldId);
        log.info("Found {} documents in source world {}", sourceDocuments.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WDocument source : sourceDocuments) {
            WDocument target = WDocument.builder()
                    .worldId(targetWorldId)
                    .collection(source.getCollection())
                    .documentId(source.getDocumentId())
                    .name(source.getName())
                    .title(source.getTitle())
                    .language(source.getLanguage())
                    .format(source.getFormat())
                    .content(source.getContent())
                    .summary(source.getSummary())
                    .metadata(source.getMetadata() != null ? new HashMap<>(source.getMetadata()) : null)
                    .parentDocumentId(source.getParentDocumentId())
                    .isMain(source.isMain())
                    .readOnly(source.isReadOnly())
                    .hash(source.getHash())
                    .type(source.getType())
                    .childType(source.getChildType())
                    .build();

            target.touchCreate();
            documentRepository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} documents from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
