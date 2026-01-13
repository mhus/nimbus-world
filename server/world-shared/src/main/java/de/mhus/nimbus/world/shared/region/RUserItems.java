package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Entity for storing user items per region.
 * Items can be labeled (e.g., 'equipped', 'backpack', 'stash1', etc.) and searched by labels.
 */
@Document(collection = "user_items")
@ActualSchemaVersion("1.0.0")
@CompoundIndex(def = "{userId:1, regionId:1, itemId:1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RUserItems {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String regionId;

    private String itemId;
    private Integer amount;
    private String texture;
    private String name;
    private Set<String> labels; // e.g., 'equipped', 'backpack', 'stash1'

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public RUserItems(String userId, String regionId, String itemId, Integer amount, String texture, String name, Set<String> labels) {
        this.userId = userId;
        this.regionId = regionId;
        this.itemId = itemId;
        this.amount = amount;
        this.texture = texture;
        this.name = name;
        this.labels = labels;
    }

    public Set<String> getLabels() {
        if (labels == null) labels = new HashSet<>();
        return labels;
    }

    public void addLabel(String label) {
        if (label != null && !label.isBlank()) {
            getLabels().add(label);
        }
    }

    public void removeLabel(String label) {
        if (labels != null) {
            labels.remove(label);
        }
    }

    public boolean hasLabel(String label) {
        return labels != null && labels.contains(label);
    }

    public boolean hasAnyLabel(Collection<String> labelsToCheck) {
        if (labels == null || labelsToCheck == null) return false;
        return labelsToCheck.stream().anyMatch(labels::contains);
    }

    public boolean hasAllLabels(Collection<String> labelsToCheck) {
        if (labels == null) return labelsToCheck == null || labelsToCheck.isEmpty();
        if (labelsToCheck == null) return true;
        return labels.containsAll(labelsToCheck);
    }
}
