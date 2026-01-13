package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * MongoDB entity for chunked storage data.
 * Large files are split into 512KB chunks for efficient storage and streaming.
 * Each chunk is stored as a separate document.
 */
@Document(collection = "storage_data")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "uuid_index_idx", def = "{ 'uuid': 1, 'index': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageData implements Identifiable {

    /**
     * MongoDB document ID (auto-generated ObjectId).
     */
    @Id
    private String id;

    /**
     * Logical storage identifier (UUID), returned to clients as storageId.
     * Multiple chunks share the same UUID to form a complete file.
     */
    @Indexed
    private String uuid;

    /**
     * Original file path for reference.
     */
    private String path;

    /**
     * World identifier for multi-world support.
     */
    private String worldId;

    /**
     * Chunk index (0-based, sequential).
     * Together with uuid forms a unique constraint.
     */
    private int index;

    /**
     * Chunk binary data (maximum 512KB by default).
     */
    private byte[] data;

    /**
     * Schema of the stored data. Default is none.
     * e.g. "block"
     */
    private String schema;

    /**
     * Version of the schema. Defsult is '0'
     * e.g. "1.0.0"
     */
    private String schemaVersion;

    /**
     * Indicates the last chunk in the sequence.
     * Used to mark end of file and store total size.
     */
    @Indexed
    private boolean isFinal;

    /**
     * Total file size in bytes.
     * Only set in the final chunk (where isFinal=true).
     */
    private long size;

    /**
     * Chunk creation timestamp (auto-populated by @EnableMongoAuditing).
     */
    @CreatedDate
    private Date createdAt;
}
