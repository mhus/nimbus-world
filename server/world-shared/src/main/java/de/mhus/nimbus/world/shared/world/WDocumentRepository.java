package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WDocument entities.
 */
@Repository
public interface WDocumentRepository extends MongoRepository<WDocument, String> {

    Optional<WDocument> findByWorldIdAndCollectionAndDocumentId(String worldId, String collection, String documentId);

    Optional<WDocument> findByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    List<WDocument> findByWorldId(String worldId);

    List<WDocument> findByWorldIdAndCollection(String worldId, String collection);

    List<WDocument> findByWorldIdAndType(String worldId, String type);

    List<WDocument> findByWorldIdAndCollectionAndType(String worldId, String collection, String type);

    boolean existsByWorldIdAndCollectionAndDocumentId(String worldId, String collection, String documentId);

    void deleteByWorldIdAndCollectionAndDocumentId(String worldId, String collection, String documentId);
}
