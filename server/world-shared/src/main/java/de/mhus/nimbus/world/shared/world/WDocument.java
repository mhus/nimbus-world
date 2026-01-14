package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB Entity for documents in the world.
 * Documents can exist in worlds or world collections, but not in world instances.
 * They have no prefix.
 */
@Document(collection = "w_documents")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_collection_documentId_idx", def = "{ 'worldId': 1, 'collection': 1, 'documentId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WDocument implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this document exists.
     * Can be a world ID or a collection ID.
     */
    @Indexed
    private String worldId;

    /**
     * Collection name for grouping documents.
     * Examples: 'lore', 'quests', 'npcs', etc.
     */
    @Indexed
    private String collection;

    /**
     * Unique document identifier (UUID).
     * Combined with worldId and collection forms a unique constraint.
     */
    private String documentId;

    /**
     * Internal technical name (optional).
     * Used for technical purposes.
     */
    private String name;

    /**
     * Display name for the document.
     */
    private String title;

    /**
     * Language code for the document (e.g., 'de', 'en').
     */
    private String language;

    /**
     * Format of the document content (e.g., 'markdown', 'plaintext').
     */
    private String format;

    /**
     * Content of the document.
     */
    private String content;

    /**
     * Short summary of the document.
     */
    private String summary;

    /**
     * Additional metadata for the document.
     */
    private Map<String, String> metadata;

    /**
     * UUID of the parent document (for hierarchies like translations).
     */
    private String parentDocumentId;

    /**
     * Whether this document is a main document (e.g., for a page)
     * or a sub-document (e.g., translation, version).
     */
    @Builder.Default
    private boolean isMain = true;

    /**
     * Content hash for change detection.
     */
    private String hash;

    /**
     * Document type (e.g., 'lore', 'quest', 'npc').
     */
    @Indexed
    private String type;

    /**
     * Child type (e.g., 'translation', 'version').
     */
    private String childType;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Initialize timestamps for new document.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
