package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.area.Composite;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronizes parameters from HexComposite model (FilledHexGrids) to WHexGrids.
 *
 * This is needed because:
 * 1. HexCompositeBuilder creates FilledHexGrids from the composition model
 * 2. FilledHexGrids are created from PlacedBiomes and Points with their FeatureHexGrids
 * 3. FeatureHexGrids contain parameters (g_village, g_road, g_river, etc.)
 * 4. These parameters need to be synced to the WHexGrids for terrain building
 *
 * IMPORTANT: Synchronisiert nur aus bereits erstellten FilledHexGrids,
 * die im HexComposite-Modell vorhanden sind.
 */
@Slf4j
public class HexGridParameterSync {

    /**
     * Syncs parameters from Area FeatureHexGrids to WHexGrids.
     * Copies road, river, wall parameters from FeatureHexGrids to matching WHexGrids.
     *
     * @param composition The composition with Area features containing FeatureHexGrids
     * @param placementResult The placement result with all placed biomes (including Fillers)
     * @param wHexGrids List of WHexGrids to update
     * @return Number of grids updated
     */
    public int syncParametersToWHexGrids(HexComposition composition,
                                         BiomePlacementResult placementResult,
                                         List<WHexGrid> wHexGrids) {
        log.debug("Starting parameter sync from FeatureHexGrids to WHexGrids");

        // Build index of WHexGrids by coordinate for fast lookup
        Map<String, WHexGrid> wHexGridIndex = new HashMap<>();
        for (WHexGrid wHexGrid : wHexGrids) {
            wHexGridIndex.put(wHexGrid.getPosition(), wHexGrid);
        }

        int updatedCount = 0;

        // Iterate all Area features and sync their FeatureHexGrid parameters
        // Note: Flow parameters (road, river, wall) were already added to Area grids
        // by FlowComposer.convertFlowSegmentsToConfigParts(), so we only need to sync Areas
        // Points are ASPEKTE - they don't have their own HexGrids, they add parameters
        // directly to biome grids, so no sync needed
        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Area area) {
                    updatedCount += syncFeatureHexGrids(area, wHexGridIndex);
                }
                // Points don't need sync - they modify biome grids directly
            }
        }

        // Also check composites
        if (composition.getComposites() != null) {
            for (Composite composite : composition.getComposites()) {
                for (Feature nestedFeature : composite.getFeatures()) {
                    if (nestedFeature instanceof Area area) {
                        updatedCount += syncFeatureHexGrids(area, wHexGridIndex);
                    }
                    // Points don't need sync - they modify biome grids directly
                }
            }
        }

        // CRITICAL: Also sync from PlacedBiomes (includes Filler-Biomes!)
        if (placementResult != null && placementResult.getPlacedBiomes() != null) {
            for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
                Biome biome = placedBiome.getBiome();
                if (biome != null && biome instanceof Area area) {
                    updatedCount += syncFeatureHexGrids(area, wHexGridIndex);
                }
            }
        }

        log.debug("Parameter sync complete: updated {} WHexGrids", updatedCount);
        return updatedCount;
    }

    /**
     * Syncs FeatureHexGrids from a single Area feature to WHexGrids.
     */
    private int syncFeatureHexGrids(Area area, Map<String, WHexGrid> wHexGridIndex) {
        if (area.getHexGrids() == null) {
            return 0;
        }

        int syncedCount = 0;

        for (FeatureHexGrid featureHexGrid : area.getHexGrids()) {
            String coordKey = featureHexGrid.getPositionKey();
            if (coordKey == null) {
                continue;
            }

            // Find matching WHexGrid
            WHexGrid wHexGrid = wHexGridIndex.get(coordKey);
            if (wHexGrid == null) {
                log.debug("No WHexGrid found for coordinate {} (Area: {})", coordKey, area.getName());
                continue;
            }

            // Sync parameters that were added by FlowComposer
            boolean synced = syncFlowParameters(featureHexGrid, wHexGrid, area.getName());
            if (synced) {
                syncedCount++;
            }
        }

        return syncedCount;
    }


    /**
     * Syncs ALL parameters from FeatureHexGrid to WHexGrid.
     *
     * FeatureHexGrids prepare all parameters exactly as needed for WHexGrids
     * (g_builder, village, road, river, wall, structure, etc.).
     * This method copies them 1:1 to the WHexGrid.
     *
     * Special handling for road parameters: if WHexGrid already has a road parameter
     * (e.g., from another source), merge the route arrays instead of overwriting.
     *
     * @return true if any parameters were synced
     */
    private boolean syncFlowParameters(FeatureHexGrid featureHexGrid, WHexGrid wHexGrid, String sourceName) {
        if (featureHexGrid.getParameters() == null || featureHexGrid.getParameters().isEmpty()) {
            return false;
        }

        // Ensure WHexGrid has parameters map
        if (wHexGrid.getParameters() == null) {
            wHexGrid.setParameters(new HashMap<>());
        }

        boolean synced = false;
        int parameterCount = 0;

        // Copy ALL parameters from FeatureHexGrid to WHexGrid
        for (Map.Entry<String, String> entry : featureHexGrid.getParameters().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null) {
                continue;
            }

            String existingValue = wHexGrid.getParameters().get(key);

            // Special handling for "g_road" parameter - merge if existing
            if ("g_road".equals(key)) {
                if (existingValue != null) {
                    // Merge road parameters (existing + new routes)
                    String mergedRoad = mergeRoadParameters(existingValue, value, wHexGrid.getPosition());
                    wHexGrid.getParameters().put("g_road", mergedRoad);
                    log.debug("Merged road parameter to WHexGrid {} (from: {})", wHexGrid.getPosition(), sourceName);
                } else {
                    // No existing road, just set it
                    wHexGrid.getParameters().put("g_road", value);
                    log.debug("Synced road parameter to WHexGrid {} (from: {})", wHexGrid.getPosition(), sourceName);
                }
                parameterCount++;
                synced = true;
            } else {
                // All other parameters: warn if overwriting, then set
                if (existingValue != null && !existingValue.equals(value)) {
                    log.warn("Overwriting parameter '{}' on WHexGrid {} (from: {}) - old: {}, new: {}",
                        key, wHexGrid.getPosition(), sourceName,
                        existingValue.length() > 50 ? existingValue.substring(0, 50) + "..." : existingValue,
                        value.length() > 50 ? value.substring(0, 50) + "..." : value);
                }
                wHexGrid.getParameters().put(key, value);
                parameterCount++;
                synced = true;
            }
        }

        if (synced) {
            log.debug("Synced {} parameters to WHexGrid {} (from: {})",
                parameterCount, wHexGrid.getPosition(), sourceName);
        }

        return synced;
    }

    /**
     * Merges road parameters from existing (e.g., Village) and flow sources.
     * Takes the base config (lx, lz, level, plaza) from existing and adds flow routes.
     *
     * @param existingRoad Existing road JSON (e.g., from Village)
     * @param flowRoad Flow road JSON (from HexGridRoadConfigurator)
     * @param position Grid position for logging
     * @return Merged road JSON
     */
    private String mergeRoadParameters(String existingRoad, String flowRoad, String position) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // Parse both JSONs
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = mapper.readValue(existingRoad, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> flow = mapper.readValue(flowRoad, Map.class);

            // Start with existing (has lx, lz, plazaSize, etc.)
            Map<String, Object> merged = new java.util.HashMap<>(existing);

            // Get route arrays
            @SuppressWarnings("unchecked")
            java.util.List<Object> existingRoute = (java.util.List<Object>) existing.get("route");
            @SuppressWarnings("unchecked")
            java.util.List<Object> flowRoute = (java.util.List<Object>) flow.get("route");

            // Merge routes
            java.util.List<Object> mergedRoute = new java.util.ArrayList<>();
            if (existingRoute != null) {
                mergedRoute.addAll(existingRoute);
            }
            if (flowRoute != null) {
                mergedRoute.addAll(flowRoute);
            }

            merged.put("route", mergedRoute);

            // Return merged JSON
            return mapper.writeValueAsString(merged);

        } catch (Exception e) {
            log.error("Failed to merge road parameters for grid {}: {}", position, e.getMessage());
            // On error, prefer flow road (has the routes we need)
            return flowRoad;
        }
    }
}
