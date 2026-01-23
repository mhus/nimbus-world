package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@GenerateTypeScript("dto")
public class HexComposition {

    private String name;
    private String title;
    private String worldId;
    private List<BiomeDefinition> biomes;
    private List<VillageDefinition> villages;
    @Builder.Default
    private String version = "1.0.0";
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String description;
    private Map<String, String> metadata;
    private String status;
    private String errorMessage;

    public void initialize() {
        if (name == null || name.isBlank()) {
            name = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public int getTotalBiomeCount() {
        int count = 0;
        if (biomes != null) {
            count += biomes.size();
        }
        if (villages != null) {
            count += villages.size();
        }
        return count;
    }

    public String getDisplayTitle() {
        return title != null ? title : name;
    }
}
