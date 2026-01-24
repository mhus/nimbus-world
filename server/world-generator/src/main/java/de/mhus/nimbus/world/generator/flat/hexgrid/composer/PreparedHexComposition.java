package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prepared version of HexComposition with all abstract values converted to concrete ranges.
 * This is the working model for the composition algorithm.
 */
@Data
public class PreparedHexComposition {
    private HexComposition original;

    // Feature-based list
    private List<PreparedFeature> preparedFeatures;

    // Helper methods to access prepared features by type

    public List<PreparedBiome> getPreparedBiomes() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedBiome)
            .map(f -> (PreparedBiome) f)
            .collect(Collectors.toList());
    }

    public List<PreparedVillage> getPreparedVillages() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedVillage)
            .map(f -> (PreparedVillage) f)
            .collect(Collectors.toList());
    }

    public List<PreparedTown> getPreparedTowns() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedTown)
            .map(f -> (PreparedTown) f)
            .collect(Collectors.toList());
    }

    public List<PreparedComposite> getPreparedComposites() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedComposite)
            .map(f -> (PreparedComposite) f)
            .collect(Collectors.toList());
    }

    public List<PreparedFlow> getPreparedFlows() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedFlow)
            .map(f -> (PreparedFlow) f)
            .collect(Collectors.toList());
    }

    public List<PreparedRoad> getPreparedRoads() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedRoad)
            .map(f -> (PreparedRoad) f)
            .collect(Collectors.toList());
    }

    public List<PreparedRiver> getPreparedRivers() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedRiver)
            .map(f -> (PreparedRiver) f)
            .collect(Collectors.toList());
    }

    public List<PreparedWall> getPreparedWalls() {
        if (preparedFeatures == null) {
            return new ArrayList<>();
        }
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedWall)
            .map(f -> (PreparedWall) f)
            .collect(Collectors.toList());
    }

    // Convenience aliases for shorter method names

    public List<PreparedBiome> getBiomes() {
        return getPreparedBiomes();
    }

    public List<PreparedRoad> getRoads() {
        return getPreparedRoads();
    }

    public List<PreparedRiver> getRivers() {
        return getPreparedRivers();
    }

    public List<PreparedWall> getWalls() {
        return getPreparedWalls();
    }
}
