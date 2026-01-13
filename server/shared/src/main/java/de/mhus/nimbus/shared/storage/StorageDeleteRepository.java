package de.mhus.nimbus.shared.storage;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Repository for scheduled storage deletions.
 * Used by the cleanup scheduler to process soft-deleted storage data.
 */
@Repository
public interface StorageDeleteRepository extends MongoRepository<StorageDelete, String> {

    /**
     * Find all deletion entries scheduled before or at the given timestamp.
     * Used by the cleanup scheduler to identify storage data ready for deletion.
     *
     * @param timestamp Current time or cleanup threshold
     * @return List of StorageDelete entries ready for processing
     */
    List<StorageDelete> findByDeletedAtLessThanEqual(Date timestamp);
}
