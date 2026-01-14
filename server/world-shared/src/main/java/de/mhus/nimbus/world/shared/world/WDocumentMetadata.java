package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Document metadata without the large content field.
 * Used for efficient list operations where content is not needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("dto")
public class WDocumentMetadata {
    private String documentId;
    private String name;
    private String title;
    private String collection;
    private String language;
    private String format;
    private String summary;
    private Map<String, String> metadata;
    private String parentDocumentId;
    private boolean isMain;
    private String hash;
    private String type;
    private String childType;
    private String worldId;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Create metadata from full document (without content).
     */
    public static WDocumentMetadata fromDocument(WDocument document) {
        return WDocumentMetadata.builder()
                .documentId(document.getDocumentId())
                .name(document.getName())
                .title(document.getTitle())
                .collection(document.getCollection())
                .language(document.getLanguage())
                .format(document.getFormat())
                .summary(document.getSummary())
                .metadata(document.getMetadata())
                .parentDocumentId(document.getParentDocumentId())
                .isMain(document.isMain())
                .hash(document.getHash())
                .type(document.getType())
                .childType(document.getChildType())
                .worldId(document.getWorldId())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
