package de.mhus.nimbus.world.shared.world;

/**
 * Thrown when saving an EpochEntity would make another document at the same natural key
 * fully obsolete (empty epoches after pull). This is a safety check to prevent accidental
 * overwrites.
 *
 * See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern
 */
public class EpochOverwriteException extends RuntimeException {

    private final String entityType;
    private final String naturalKey;
    private final String affectedDocumentId;

    public EpochOverwriteException(String entityType, String naturalKey, String affectedDocumentId, String message) {
        super(message);
        this.entityType = entityType;
        this.naturalKey = naturalKey;
        this.affectedDocumentId = affectedDocumentId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getAffectedDocumentId() {
        return affectedDocumentId;
    }
}
