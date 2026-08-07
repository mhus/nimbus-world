package de.mhus.nimbus.world.generator.composer.build;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.area.Composite;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.Continent;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureStatus;
import de.mhus.nimbus.world.generator.composer.flow.Flow;
import de.mhus.nimbus.world.generator.composer.flow.River;
import de.mhus.nimbus.world.generator.composer.flow.Road;
import de.mhus.nimbus.world.generator.composer.flow.Wall;
import de.mhus.nimbus.world.generator.composer.town.Town;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
// PRIVATE on purpose: a public all-args constructor is picked up by Jackson 3 as a properties-based
// creator, which bypasses the no-args constructor and therefore all @Builder.Default values —
// featureHexGridRegistry would come out null. Only the builder needs this constructor.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HexComposition implements BuildFeature {

    private String compositionId;
    private String name;
    private String title;
    private String worldId;

    /**
     * The epoch this composition generates content for.
     * All created WHexGrids will be assigned to this epoch.
     */
    @Builder.Default
    private int epoch = 0;

    /**
     * The parent epoch from which this composition's epoch was derived.
     * Used for tracking epoch lineage when creating new epochs.
     * Null means this is the initial/base epoch.
     */
    private Integer parentEpoch;

    // NEW: Feature-based list (replaces biomes + villages)
    private List<Feature> features;

    // Continent definitions for filling gaps between biomes
    private List<Continent> continents;

    /**
     * Central registry for all FeatureHexGrids across all features.
     * Prevents duplicate grids at the same coordinate.
     * Key: coordinate string "q;r"
     * Value: FeatureHexGrid
     */
    // Central registry for all FeatureHexGrids (single source of truth during composition)
    // Map version for internal use (fast lookup by coordinate)
    @JsonIgnore
    @Builder.Default
    private Map<String, de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid> featureHexGridRegistry = new HashMap<>();

    // List version for JSON export (Jackson has issues with Map<String, FeatureHexGrid>)
    // This is populated before export
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid> featureHexGrids;

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

    /**
     * Gets or creates a FeatureHexGrid for the given coordinate from the central registry.
     * Prevents duplicate grids at the same coordinate.
     *
     * @param coordinate The hex coordinate
     * @return The FeatureHexGrid (existing or newly created)
     */
    public de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid getOrCreateFeatureHexGrid(
            de.mhus.nimbus.generated.types.HexVector2 coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("Coordinate cannot be null");
        }

        String key = TypeUtil.toStringHexCoord(coordinate);
        // Via the accessor, not the field: it guarantees a non-null registry even for instances that
        // were built without it (e.g. deserialized by a creator that skipped the default).
        return getFeatureHexGridRegistry().computeIfAbsent(key, k ->
            de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid.builder()
                .coordinate(coordinate)
                .build()
        );
    }

    /**
     * Gets a FeatureHexGrid for the given coordinate from the central registry.
     *
     * @param coordinate The hex coordinate
     * @return The FeatureHexGrid, or null if not found
     */
    public de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid getFeatureHexGrid(
            de.mhus.nimbus.generated.types.HexVector2 coordinate) {
        if (coordinate == null) {
            return null;
        }

        String key = TypeUtil.toStringHexCoord(coordinate);
        return getFeatureHexGridRegistry().get(key);
    }

    /**
     * Gets the central FeatureHexGrid registry.
     * This is the single source of truth for all FeatureHexGrids in the composition.
     *
     * @return The central registry map (never null)
     */
    @JsonIgnore
    public Map<String, de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid> getFeatureHexGridRegistry() {
        if (featureHexGridRegistry == null) {
            featureHexGridRegistry = new ConcurrentHashMap<>();
        }
        return featureHexGridRegistry;
    }

    @JsonIgnore
    public int getTotalBiomeCount() {
        return features != null ? features.size() : 0;
    }

    @JsonIgnore
    public String getDisplayTitle() {
        return title != null ? title : name;
    }

    // Helper methods to access features by type

    @JsonIgnore
    public List<Biome> getBiomes() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Biome)
            .map(f -> (Biome) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Town> getVillages() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Town)
            .map(f -> (Town) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Composite> getComposites() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Composite)
            .map(f -> (Composite) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Flow> getFlows() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Flow)
            .map(f -> (Flow) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Road> getRoads() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof Road)
            .map(f -> (Road) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<River> getRivers() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
            .filter(f -> f instanceof River)
            .map(f -> (River) f)
            .collect(Collectors.toList());
    }

    @JsonIgnore
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
            .build()
            .compose();
    }
}
