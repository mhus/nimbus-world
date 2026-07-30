package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final MongoTemplate mongoTemplate;

    /**
     * Find document by documentId.
     */
    @Transactional(readOnly = true)
    public Optional<WDocument> findByDocumentId(WorldId worldId, String collection, String documentId) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
        }
        return repository.findByWorldIdAndCollectionAndDocumentId(worldId.getId(), collection, documentId);
    }

    /**
     * Find document by documentId.
     */
    @Transactional(readOnly = true)
    public Optional<WDocument> findByDocumentId(WorldId worldId, String documentId) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
        }
        return repository.findByWorldIdAndDocumentId(worldId.getId(), documentId);
    }

    /**
     * Find document by technical name.
     */
    @Transactional(readOnly = true)
    public Optional<WDocument> findByName(WorldId worldId, String collection, String name) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
        }
        return repository.findFirstByWorldIdAndCollectionAndNameOrderByCreatedAtDesc(worldId.getId(), collection, name);
    }

    /**
     * Find all documents for specific worldId and collection.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByCollection(WorldId worldId, String collection) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
        }
        return repository.findByWorldIdAndCollection(worldId.getId(), collection);
    }

    /**
     * Find all documents for specific worldId.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByWorldId(WorldId worldId) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
        }
        return repository.findByWorldId(worldId.getId());
    }

    /**
     * Find all documents for specific worldId and type.
     */
    @Transactional(readOnly = true)
    public List<WDocument> findByType(WorldId worldId, String type) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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

        // Apply the updater first to get the name
        updater.accept(document);

        // Check for duplicate names - if found, update the existing document instead of creating a duplicate
        if (!Strings.isBlank(document.getName())) {
            Optional<WDocument> existingByName = repository.findFirstByWorldIdAndCollectionAndNameOrderByCreatedAtDesc(
                    worldId.getId(), collection, document.getName());

            if (existingByName.isPresent() && !existingByName.get().getDocumentId().equals(documentId)) {
                log.info("Document with name '{}' already exists in worldId={}, collection={} (documentId={}). " +
                        "Updating existing document instead of creating duplicate.",
                        document.getName(), worldId, collection, existingByName.get().getDocumentId());

                // Use the existing document and apply the updater to it
                document = existingByName.get();
                updater.accept(document);
            }
        }

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
        List<WDocument> toSave = new ArrayList<>();

        // Check for duplicate names and merge with existing documents if found
        for (WDocument doc : documents) {
            WDocument documentToSave = doc;

            if (!Strings.isBlank(doc.getName())) {
                Optional<WDocument> existingByName = repository.findFirstByWorldIdAndCollectionAndNameOrderByCreatedAtDesc(
                        doc.getWorldId(), doc.getCollection(), doc.getName());

                if (existingByName.isPresent() && !existingByName.get().getDocumentId().equals(doc.getDocumentId())) {
                    log.info("Document with name '{}' already exists in worldId={}, collection={} (documentId={}). " +
                            "Updating existing document instead of creating duplicate.",
                            doc.getName(), doc.getWorldId(), doc.getCollection(), existingByName.get().getDocumentId());

                    // Use the existing document and copy data from the new one
                    documentToSave = existingByName.get();
                    // Copy fields from new document to existing one
                    documentToSave.setTitle(doc.getTitle());
                    documentToSave.setLanguage(doc.getLanguage());
                    documentToSave.setFormat(doc.getFormat());
                    documentToSave.setContent(doc.getContent());
                    documentToSave.setType(doc.getType());
                    documentToSave.setMetadata(doc.getMetadata());
                }
            }

            if (documentToSave.getCreatedAt() == null) {
                documentToSave.touchCreate();
            }
            documentToSave.touchUpdate();
            toSave.add(documentToSave);
        }

        List<WDocument> saved = repository.saveAll(toSave);
        log.debug("Saved {} WDocument entities", saved.size());
        return saved;
    }

    /**
     * Update a document.
     */
    @Transactional
    public Optional<WDocument> update(WorldId worldId, String collection, String documentId, Consumer<WDocument> updater) {
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            throw new IllegalArgumentException("worldId must not be a player instance id");
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

    // ========== Bulk World Operations (data ownership) ==========

    /**
     * Delete ALL documents of a world (identified by its raw worldId string,
     * which may also be a collection id). Owner-level bulk operation so callers
     * do not query the WDocument collection directly (data ownership).
     *
     * @return number of deleted documents
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WDocument.class
        );
        log.info("Deleted {} documents for world {}", result.getDeletedCount(), worldId);
        return (int) result.getDeletedCount();
    }

    /**
     * Distinct world IDs that have documents (owner-level; avoids callers
     * querying the WDocument collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WDocument.class, String.class);
    }

    /**
     * Duplicate ALL documents from a source world to a target world. Copies each
     * document as a new entity (fresh create timestamps) preserving all business
     * fields. Owner-level bulk operation so callers do not touch the WDocument
     * repository directly (data ownership).
     *
     * @return number of duplicated documents
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WDocument> sourceDocuments = repository.findByWorldId(sourceWorldId);
        log.info("Found {} documents in source world {}", sourceDocuments.size(), sourceWorldId);

        List<WDocument> targets = new ArrayList<>();
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
            targets.add(target);
        }

        repository.saveAll(targets);
        log.info("Duplicated {} documents from world {} to {}", targets.size(), sourceWorldId, targetWorldId);
        return targets.size();
    }

    /**
     * Repair duplicate WDocument entries (unique: worldId + documentId).
     * Owner-level operation so callers do not access the WDocument collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WDocument.class, "document", worldId,
                doc -> {
                    String documentId = doc.getString("documentId");
                    return documentId != null ? doc.getString("worldId") + "|" + documentId : null;
                }
        );
    }
}
