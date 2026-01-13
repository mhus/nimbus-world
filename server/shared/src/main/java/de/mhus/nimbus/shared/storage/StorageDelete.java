package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * MongoDB entity for scheduled deletion of storage data.
 * Enables soft-delete with configurable delay (default 5 minutes) before actual cleanup.
 * This allows ongoing read operations to complete safely before data is removed.
 */
@Document(collection = "storage_delete")
@ActualSchemaVersion("1.0.0")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageDelete {

    /**
     * MongoDB document ID (auto-generated ObjectId).
     */
    @Id
    private String id;

    /**
     * UUID of StorageData to delete (references StorageData.uuid field).
     * All chunks with this UUID will be deleted by the cleanup scheduler.
     */
    @Indexed
    private String storageId;

    /**
     * Scheduled deletion timestamp (typically now + 5 minutes).
     * The cleanup scheduler processes entries where deletedAt <= current time.
     */
    @Indexed
    private Date deletedAt;
}
