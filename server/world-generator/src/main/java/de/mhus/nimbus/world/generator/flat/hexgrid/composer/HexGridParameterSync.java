package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronizes parameters from FeatureHexGrids to WHexGrids.
 *
 * This is needed because:
 * 1. BiomeComposer creates WHexGrids early (for HexGridFiller)
 * 2. FlowComposer adds road/river parameters to FeatureHexGrids later
 * 3. These parameters need to be copied to the existing WHexGrids
 */
@Slf4j
public class HexGridParameterSync {

    /**
     * Syncs parameters from Area FeatureHexGrids to WHexGrids.
     * Copies road, river, wall parameters from FeatureHexGrids to matching WHexGrids.
     *
     * @param composition The composition with Area features containing FeatureHexGrids
     * @param wHexGrids List of WHexGrids to update
     * @return Number of grids updated
     */
    public int syncParametersToWHexGrids(HexComposition composition, List<WHexGrid> wHexGrids) {
        log.info("Starting parameter sync from FeatureHexGrids to WHexGrids");

        // Build index of WHexGrids by coordinate for fast lookup
        Map<String, WHexGrid> wHexGridIndex = new HashMap<>();
        for (WHexGrid wHexGrid : wHexGrids) {
            wHexGridIndex.put(wHexGrid.getPosition(), wHexGrid);
        }

        int updatedCount = 0;

        // Iterate all Area features and sync their FeatureHexGrid parameters
        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Area area) {
                    updatedCount += syncFeatureHexGrids(area, wHexGridIndex);
                }
            }
        }

        // Also check composites
        if (composition.getComposites() != null) {
            for (Composite composite : composition.getComposites()) {
                for (Feature nestedFeature : composite.getFeatures()) {
                    if (nestedFeature instanceof Area area) {
                        updatedCount += syncFeatureHexGrids(area, wHexGridIndex);
                    }
                }
            }
        }

        log.info("Parameter sync complete: updated {} WHexGrids", updatedCount);
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
    private boolean syncFlowParameters(FeatureHexGrid featureHexGrid, WHexGrid wHexGrid, String areaName) {
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

            // Special handling for "road" parameter - merge if existing
            if ("road".equals(key)) {
                String existingRoad = wHexGrid.getParameters().get("road");
                if (existingRoad != null) {
                    // Merge road parameters (existing + new routes)
                    String mergedRoad = mergeRoadParameters(existingRoad, value, wHexGrid.getPosition());
                    wHexGrid.getParameters().put("road", mergedRoad);
                    log.debug("Merged road parameter to WHexGrid {} (Area: {})", wHexGrid.getPosition(), areaName);
                } else {
                    // No existing road, just set it
                    wHexGrid.getParameters().put("road", value);
                    log.debug("Synced road parameter to WHexGrid {} (Area: {})", wHexGrid.getPosition(), areaName);
                }
                parameterCount++;
                synced = true;
            } else {
                // All other parameters: copy 1:1
                wHexGrid.getParameters().put(key, value);
                parameterCount++;
                synced = true;
            }
        }

        if (synced) {
            log.debug("Synced {} parameters to WHexGrid {} (Area: {})",
                parameterCount, wHexGrid.getPosition(), areaName);
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
