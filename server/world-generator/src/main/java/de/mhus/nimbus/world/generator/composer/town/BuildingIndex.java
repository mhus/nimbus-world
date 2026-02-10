package de.mhus.nimbus.world.generator.composer.town;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BuildingIndex provides access to building definitions indexed by style and kind.
 *
 * The index allows the village builder to search for appropriate buildings
 * based on the village style and building kind (house, tavern, shop, etc.).
 *
 * Buildings are loaded from external sources (files, databases, etc.) and
 * indexed for fast lookup.
 */
@Slf4j
public class BuildingIndex {

    /**
     * Internal storage for building definitions.
     * Key: style -> kind -> List of BuildingDefinitions
     */
    private final Map<String, Map<String, List<BuildingDefinition>>> buildings = new HashMap<>();

    /**
     * Creates a new BuildingIndex.
     * Initially empty - buildings must be added via addBuilding() or loaded from external sources.
     */
    public BuildingIndex() {
        // Initialize empty index
        log.debug("Created new BuildingIndex");
    }

    /**
     * Finds buildings matching the specified style and kind.
     *
     * Returns all building definitions that match both the style and kind.
     * If no exact matches are found, returns an empty list.
     *
     * @param style Building style (e.g., "medieval", "modern", "fantasy")
     * @param kind Building kind (e.g., "house", "tavern", "shop")
     * @return List of matching BuildingDefinitions (may be empty)
     */
    public List<BuildingDefinition> findBuildings(String style, String kind) {
        if (style == null || kind == null) {
            log.warn("Cannot search with null style or kind");
            return new ArrayList<>();
        }

        // Normalize style and kind to lowercase for case-insensitive matching
        String normalizedStyle = style.toLowerCase();
        String normalizedKind = kind.toLowerCase();

        // Check if style exists in index
        Map<String, List<BuildingDefinition>> styleMap = buildings.get(normalizedStyle);
        if (styleMap == null) {
            log.debug("No buildings found for style: {}", style);
            return new ArrayList<>();
        }

        // Check if kind exists for this style
        List<BuildingDefinition> kindList = styleMap.get(normalizedKind);
        if (kindList == null) {
            log.debug("No buildings found for style: {}, kind: {}", style, kind);
            return new ArrayList<>();
        }

        log.debug("Found {} building(s) for style: {}, kind: {}", kindList.size(), style, kind);
        return new ArrayList<>(kindList); // Return copy to prevent external modification
    }

    /**
     * Finds buildings matching the specified style, regardless of kind.
     *
     * @param style Building style (e.g., "medieval", "modern")
     * @return List of all BuildingDefinitions with this style
     */
    public List<BuildingDefinition> findBuildingsByStyle(String style) {
        if (style == null) {
            log.warn("Cannot search with null style");
            return new ArrayList<>();
        }

        String normalizedStyle = style.toLowerCase();
        Map<String, List<BuildingDefinition>> styleMap = buildings.get(normalizedStyle);

        if (styleMap == null) {
            log.debug("No buildings found for style: {}", style);
            return new ArrayList<>();
        }

        // Flatten all kinds into a single list
        return styleMap.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    /**
     * Adds a building definition to the index.
     *
     * The building is indexed by its style and kind for fast lookup.
     * If a building with the same ID already exists, it will be replaced.
     *
     * @param building Building definition to add
     */
    public void addBuilding(BuildingDefinition building) {
        if (building == null) {
            log.warn("Cannot add null building");
            return;
        }

        if (building.getStyle() == null || building.getKind() == null) {
            log.warn("Cannot add building without style or kind: {}", building.getBuildingId());
            return;
        }

        String normalizedStyle = building.getStyle().toLowerCase();
        String normalizedKind = building.getKind().toLowerCase();

        // Ensure style map exists
        buildings.putIfAbsent(normalizedStyle, new HashMap<>());
        Map<String, List<BuildingDefinition>> styleMap = buildings.get(normalizedStyle);

        // Ensure kind list exists
        styleMap.putIfAbsent(normalizedKind, new ArrayList<>());
        List<BuildingDefinition> kindList = styleMap.get(normalizedKind);

        // Remove existing building with same ID (if any)
        kindList.removeIf(b -> building.getBuildingId().equals(b.getBuildingId()));

        // Add the building
        kindList.add(building);

        log.debug("Added building to index: {} (style={}, kind={})",
            building.getBuildingId(), building.getStyle(), building.getKind());
    }

    /**
     * Loads multiple buildings into the index.
     *
     * @param buildingList List of building definitions to add
     */
    public void loadBuildings(List<BuildingDefinition> buildingList) {
        if (buildingList == null) {
            log.warn("Cannot load null building list");
            return;
        }

        int loaded = 0;
        for (BuildingDefinition building : buildingList) {
            addBuilding(building);
            loaded++;
        }

        log.info("Loaded {} building definitions into index", loaded);
    }

    /**
     * Gets the total number of buildings in the index.
     *
     * @return Total count of all buildings across all styles and kinds
     */
    public int getTotalBuildingCount() {
        return buildings.values().stream()
            .flatMap(styleMap -> styleMap.values().stream())
            .mapToInt(List::size)
            .sum();
    }

    /**
     * Clears all buildings from the index.
     */
    public void clear() {
        buildings.clear();
        log.debug("Cleared building index");
    }
}
