package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service for managing WDocument entities.
 * Documents can exist in worlds or world collections, but not in world instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WDocumentService {

    private final WDocumentRepository repository;

    /**
     * Find document by documentId.
     */
    @Transactional(readOnly = true)
    public Optional<WDocument> findByDocumentId(WorldId worldId, String collection, String documentId) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.findByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId);
    }

    /**
     * Find document by technical name.
     */
    @Transactional(readOnly = true)
    public Optional<WDocument> findByName(WorldId worldId, String collection, String name) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.findByWorldIdAndCollectionAndName(worldId.getId(), collection, name);
    }

    /**
     * Find all documents for specific worldId and collection.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByCollection(WorldId worldId, String collection) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.findByWorldIdAndCollection(worldId.getId(), collection);
    }

    /**
     * Find all documents for specific worldId.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByWorldId(WorldId worldId) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.findByWorldId(worldId.getId());
    }

    /**
     * Find all documents for specific worldId and type.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByType(WorldId worldId, String type) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.findByWorldIdAndType(worldId.getId(), type);
    }

    /**
     * Lookup documents from multiple sources:
     * - Search in the specified worldId and collection
     * - If worldId is not a collection, also search in the region collection
     * - Also search in '@shared:collection'
     * All results are merged and returned as one collection.
     *
     * @param worldId The world identifier
     * @param collection The collection name
     * @return List of all documents from all sources
     */
    @Transactional(readOnly = true)
    public List<WDocument> lookupDocuments(WorldId worldId, String collection) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        if (Strings.isBlank(collection)) {
            throw new IllegalArgumentException("collection required");
        }

        Set<String> uniqueIds = new HashSet<>();
        List<WDocument> results = new ArrayList<>();

        // 1. Search in the specified worldId
        List<WDocument> worldDocs = repository.findByWorldIdAndCollection(worldId.getId(), collection);
        for (WDocument doc : worldDocs) {
            String key = doc.getWorldId() + ":" + doc.getCollection() + ":" + doc.getDocumentId();
            if (uniqueIds.add(key)) {
                results.add(doc);
            }
        }
        log.debug("Found {} documents in worldId={}, collection={}", worldDocs.size(), worldId, collection);

        // 2. If worldId is not a collection, also search in the region collection
        if (!worldId.isCollection()) {
            WorldId regionCollection = worldId.toRegionCollection();
            List<WDocument> regionDocs = repository.findByWorldIdAndCollection(regionCollection.getId(), collection);
            for (WDocument doc : regionDocs) {
                String key = doc.getWorldId() + ":" + doc.getCollection() + ":" + doc.getDocumentId();
                if (uniqueIds.add(key)) {
                    results.add(doc);
                }
            }
            log.debug("Found {} documents in region collection={}, collection={}", regionDocs.size(), regionCollection, collection);
        }

        // 3. Search in '@shared:collection'
        WorldId sharedCollection = WorldId.of(WorldId.COLLECTION_SHARED, collection)
                .orElseThrow(() -> new IllegalArgumentException("Invalid shared collection: " + collection));
        List<WDocument> sharedDocs = repository.findByWorldIdAndCollection(sharedCollection.getId(), collection);
        for (WDocument doc : sharedDocs) {
            String key = doc.getWorldId() + ":" + doc.getCollection() + ":" + doc.getDocumentId();
            if (uniqueIds.add(key)) {
                results.add(doc);
            }
        }
        log.debug("Found {} documents in shared collection={}, collection={}", sharedDocs.size(), sharedCollection, collection);

        log.debug("Total documents found: {} (from {} unique sources)", results.size(), uniqueIds.size());
        return results;
    }

    /**
     * Save or update a document.
     */
    @Transactional
    public WDocument save(WorldId worldId, String collection, String documentId, Consumer<WDocument> updater) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(collection)) {
            throw new IllegalArgumentException("collection required");
        }
        if (Strings.isBlank(documentId)) {
            throw new IllegalArgumentException("documentId required");
        }
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }

        WDocument document = repository.findByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId)
                .orElseGet(() -> {
                    WDocument neu = WDocument.builder()
                            .worldId(worldId.getId())
                            .collection(collection)
                            .documentId(documentId)
                            .build();
                    neu.touchCreate();
                    log.debug("Creating new WDocument: worldId={}, collection={}, documentId={}", worldId, collection, documentId);
                    return neu;
                });

        updater.accept(document);
        document.touchUpdate();

        WDocument saved = repository.save(document);
        log.debug("Saved WDocument: worldId={}, collection={}, documentId={}", worldId, collection, documentId);
        return saved;
    }

    /**
     * Save all documents.
     */
    @Transactional
    public List<WDocument> saveAll(List<WDocument> documents) {
        documents.forEach(doc -> {
            if (doc.getCreatedAt() == null) {
                doc.touchCreate();
            }
            doc.touchUpdate();
        });
        List<WDocument> saved = repository.saveAll(documents);
        log.debug("Saved {} WDocument entities", saved.size());
        return saved;
    }

    /**
     * Update a document.
     */
    @Transactional
    public Optional<WDocument> update(WorldId worldId, String collection, String documentId, Consumer<WDocument> updater) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }

        return repository.findByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId)
                .map(document -> {
                    updater.accept(document);
                    document.touchUpdate();
                    WDocument saved = repository.save(document);
                    log.debug("Updated WDocument: worldId={}, collection={}, documentId={}", worldId, collection, documentId);
                    return saved;
                });
    }

    /**
     * Delete a document.
     */
    @Transactional
    public boolean delete(WorldId worldId, String collection, String documentId) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }

        return repository.findByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId)
                .map(document -> {
                    repository.delete(document);
                    log.debug("Deleted WDocument: worldId={}, collection={}, documentId={}", worldId, collection, documentId);
                    return true;
                }).orElse(false);
    }

    /**
     * Check if a document exists.
     */
    @Transactional(readOnly = true)
    public boolean exists(WorldId worldId, String collection, String documentId) {
        if (worldId.isInstance()) {
            throw new IllegalArgumentException("worldId must not be an instance id");
        }
        return repository.existsByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId);
    }

    // ========== Metadata Methods (without content) ==========

    /**
     * Find all documents metadata for specific worldId and collection (without content).
     */
    @Transactional(readOnly = true)
    public List<WDocumentMetadata> findMetadataByCollection(WorldId worldId, String collection) {
        return findByCollection(worldId, collection).stream()
                .map(WDocumentMetadata::fromDocument)
                .collect(Collectors.toList());
    }

    /**
     * Find all documents metadata for specific worldId (without content).
     */
    @Transactional(readOnly = true)
    public List<WDocumentMetadata> findMetadataByWorldId(WorldId worldId) {
        return findByWorldId(worldId).stream()
                .map(WDocumentMetadata::fromDocument)
                .collect(Collectors.toList());
    }

    /**
     * Find all documents metadata for specific worldId and type (without content).
     */
    @Transactional(readOnly = true)
    public List<WDocumentMetadata> findMetadataByType(WorldId worldId, String type) {
        return findByType(worldId, type).stream()
                .map(WDocumentMetadata::fromDocument)
                .collect(Collectors.toList());
    }

    /**
     * Lookup documents metadata from multiple sources (without content).
     * Same as lookupDocuments() but returns only metadata without the large content field.
     */
    @Transactional(readOnly = true)
    public List<WDocumentMetadata> lookupDocumentsMetadata(WorldId worldId, String collection) {
        return lookupDocuments(worldId, collection).stream()
                .map(WDocumentMetadata::fromDocument)
                .collect(Collectors.toList());
    }
}
