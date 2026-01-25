package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HexComposition implements BuildFeature {

    private String compositionId;
    private String name;
    private String title;
    private String worldId;

    // NEW: Feature-based list (replaces biomes + villages)
    private List<Feature> features;

    // Continent definitions for filling gaps between biomes
    private List<Continent> continents;

    @Builder.Default
    private String version = "1.0.0";
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String description;
    private Map<String, String> metadata;
    private FeatureStatus status;
    private String errorMessage;

    public void initialize() {
        if (compositionId == null || compositionId.isBlank()) {
            compositionId = UUID.randomUUID().toString();
        }
        if (name == null || name.isBlank()) {
            name = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;

        // Initialize all features
        if (features != null) {
            features.forEach(Feature::initialize);
        }
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public int getTotalBiomeCount() {
        return features != null ? features.size() : 0;
    }

    public String getDisplayTitle() {
        return title != null ? title : name;
    }

    // Helper methods to access features by type

    public List<Biome> getBiomes() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Biome)
            .map(f -> (Biome) f)
            .collect(Collectors.toList());
    }

    public List<Village> getVillages() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Village)
            .map(f -> (Village) f)
            .collect(Collectors.toList());
    }

    public List<Town> getTowns() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Town)
            .map(f -> (Town) f)
            .collect(Collectors.toList());
    }

    public List<Composite> getComposites() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Composite)
            .map(f -> (Composite) f)
            .collect(Collectors.toList());
    }

    public List<Flow> getFlows() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Flow)
            .map(f -> (Flow) f)
            .collect(Collectors.toList());
    }

    public List<Road> getRoads() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Road)
            .map(f -> (Road) f)
            .collect(Collectors.toList());
    }

    public List<River> getRivers() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof River)
            .map(f -> (River) f)
            .collect(Collectors.toList());
    }

    public List<Wall> getWalls() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Wall)
            .map(f -> (Wall) f)
            .collect(Collectors.toList());
    }

    /**
     * Builds this composition using the HexCompositeBuilder.
     * Implements BuildFeature interface.
     *
     * @param context Build context with parameters (worldId, seed, repository, etc.)
     * @return CompositionResult with all build results and statistics
     */
    @Override
    public CompositionResult build(BuildContext context) {
        return HexCompositeBuilder.builder()
            .composition(this)
            .worldId(context.getWorldId())
            .seed(context.getSeed())
            .fillGaps(context.isFillGaps())
            .oceanBorderRings(context.getOceanBorderRings())
            .repository(context.getRepository())
            .generateWHexGrids(context.isGenerateWHexGrids())
            .build()
            .compose();
    }
}
