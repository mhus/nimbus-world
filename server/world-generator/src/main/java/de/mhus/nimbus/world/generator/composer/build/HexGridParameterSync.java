package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
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
     * Syncs parameters from central FeatureHexGrid registry to WHexGrids.
     * Copies road, river, wall parameters from FeatureHexGrids to matching WHexGrids.
     *
     * @param composition The composition with central FeatureHexGrid registry
     * @param placementResult The placement result with all placed biomes (including Fillers)
     * @param wHexGrids List of WHexGrids to update
     * @return Number of grids updated
     */
    public int syncParametersToWHexGrids(HexComposition composition,
                                         BiomePlacementResult placementResult,
                                         List<WHexGrid> wHexGrids) {
        log.debug("Starting parameter sync from central FeatureHexGrid registry to WHexGrids");

        // Build index of WHexGrids by coordinate for fast lookup
        Map<String, WHexGrid> wHexGridIndex = new HashMap<>();
        for (WHexGrid wHexGrid : wHexGrids) {
            wHexGridIndex.put(wHexGrid.getPosition(), wHexGrid);
        }

        int updatedCount = 0;

        // Use central FeatureHexGrid registry (Single Source of Truth)
        // All FeatureHexGrids are managed here, regardless of their source (Biome, Point, Flow, etc.)
        Map<String, FeatureHexGrid> featureHexGridRegistry = composition.getFeatureHexGridRegistry();
        if (featureHexGridRegistry == null || featureHexGridRegistry.isEmpty()) {
            log.warn("No FeatureHexGrids found in central registry");
            return 0;
        }

        log.debug("Syncing parameters from {} FeatureHexGrids in central registry", featureHexGridRegistry.size());

        // Sync all FeatureHexGrids from central registry to WHexGrids
        for (FeatureHexGrid featureHexGrid : featureHexGridRegistry.values()) {
            String coordKey = featureHexGrid.getPositionKey();
            if (coordKey == null) {
                continue;
            }

            // Find matching WHexGrid
            WHexGrid wHexGrid = wHexGridIndex.get(coordKey);
            if (wHexGrid == null) {
                log.debug("No WHexGrid found for coordinate {} (from central registry)", coordKey);
                continue;
            }

            // Sync parameters
            boolean synced = syncFlowParameters(featureHexGrid, wHexGrid, "central-registry");
            if (synced) {
                updatedCount++;
            }
        }

        log.debug("Parameter sync complete: updated {} WHexGrids", updatedCount);
        return updatedCount;
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
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

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
