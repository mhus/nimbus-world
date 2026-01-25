package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 2: Assembles road={} JSON parameters on FeatureHexGrids from collected RoadConfigParts.
 *
 * Workflow:
 * 1. Phase 1: Everyone (Roads, Villages, etc.) adds RoadConfigParts to Area FeatureHexGrids
 * 2. Phase 2 (this class): Assembles all RoadConfigParts into final road={} JSON
 *
 * Architecture:
 * - Flow features have their own FeatureHexGrids (metadata only: coordinates + FlowSegments)
 * - Area features (Biomes/Structures) have the actual FeatureHexGrids for terrain generation
 * - RoadConfigParts are added to Area FeatureHexGrids in Phase 1
 * - This configurator reads RoadConfigParts and builds road={} JSON
 */
@Slf4j
public class HexGridRoadConfigurator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Index for fast lookup of Area grids by coordinate.
     * Maps coordinate key to GridEntry (Area + FeatureHexGrid).
     * Provides validation for overlapping grids and missing grids.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class GridIndex {
        private final Map<String, GridEntry> index;
        private int overlappingGridCount;
        private List<String> overlappingCoordinates;

        /**
         * Creates a GridIndex from a composition by collecting all Area FeatureHexGrids.
         */
        public static GridIndex build(HexComposition composition, BiomePlacementResult placementResult) {
            Map<String, GridEntry> index = new HashMap<>();
            int overlappingCount = 0;
            List<String> overlapping = new ArrayList<>();

            // Collect from all features in composition
            if (composition.getFeatures() != null) {
                for (Feature feature : composition.getFeatures()) {
                    if (feature instanceof Area area) {
                        int overlaps = indexAreaGrids(area, index, overlapping);
                        overlappingCount += overlaps;
                    }
                }
            }

            // Collect from composites
            if (composition.getComposites() != null) {
                for (Composite composite : composition.getComposites()) {
                    for (Feature nestedFeature : composite.getFeatures()) {
                        if (nestedFeature instanceof Area area) {
                            int overlaps = indexAreaGrids(area, index, overlapping);
                            overlappingCount += overlaps;
                        }
                    }
                }
            }

            // CRITICAL: Also collect from PlacedBiomes (includes Filler-Biomes!)
            if (placementResult != null && placementResult.getPlacedBiomes() != null) {
                for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
                    Biome biome = placedBiome.getBiome();
                    if (biome != null && biome instanceof Area area) {
                        int overlaps = indexAreaGrids(area, index, overlapping);
                        overlappingCount += overlaps;
                    }
                }
            }

            GridIndex gridIndex = new GridIndex(index, overlappingCount, overlapping);

            // Log warnings if overlaps found
            if (overlappingCount > 0) {
                log.warn("Found {} overlapping Area grids at coordinates: {}",
                    overlappingCount, String.join(", ", overlapping));
            }

            return gridIndex;
        }

        private static int indexAreaGrids(Area area, Map<String, GridEntry> index,
                                         List<String> overlapping) {
            if (area.getHexGrids() == null) {
                return 0;
            }

            int overlapCount = 0;

            for (FeatureHexGrid hexGrid : area.getHexGrids()) {
                String coordKey = hexGrid.getPositionKey();
                if (coordKey == null) {
                    continue;
                }

                // Check for overlaps (multiple areas at same coordinate)
                if (index.containsKey(coordKey)) {
                    GridEntry existing = index.get(coordKey);
                    log.debug("Area grid overlap at {}: {} vs {} (using latest)",
                        coordKey, existing.getArea().getName(), area.getName());
                    overlapping.add(coordKey);
                    overlapCount++;
                }

                index.put(coordKey, new GridEntry(area, hexGrid));
            }

            return overlapCount;
        }

        /**
         * Gets the GridEntry at a specific coordinate.
         * @param coordKey The coordinate key (e.g., "0:0")
         * @return GridEntry or null if not found
         */
        public GridEntry get(String coordKey) {
            return index.get(coordKey);
        }

        /**
         * Checks which coordinates from the given set are missing in the index.
         * @param coordinates Set of coordinate keys to check
         * @return List of coordinates that are missing in the index
         */
        public List<String> findMissingCoordinates(Set<String> coordinates) {
            List<String> missing = new ArrayList<>();
            for (String coord : coordinates) {
                if (!index.containsKey(coord)) {
                    missing.add(coord);
                }
            }
            return missing;
        }

        /**
         * Returns the number of indexed grids.
         */
        public int size() {
            return index.size();
        }

        /**
         * Returns all coordinate keys in the index.
         */
        public Set<String> keySet() {
            return index.keySet();
        }
    }

    /**
     * Entry in GridIndex containing Area and FeatureHexGrid.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class GridEntry {
        private Area area;
        private FeatureHexGrid featureHexGrid;
    }

    @Data
    @Builder
    public static class RoadConfigurationResult {
        private int totalGrids;
        private int configuredGrids;
        private int skippedGrids;
        private int totalSegments;
        private boolean success;
        private String errorMessage;
        private List<String> errors;
    }

    /**
     * Phase 2: Assembles road={} parameters on Area FeatureHexGrids from RoadConfigParts.
     * Called after Phase 1 where everyone (Roads, Villages, etc.) has added their RoadConfigParts.
     *
     * @param composition The composition with all features and their FeatureHexGrids
     * @return Result with statistics (for logging only)
     */
    public RoadConfigurationResult configureRoads(HexComposition composition, BiomePlacementResult placementResult) {
        log.info("Starting Phase 2: road configuration from RoadConfigParts");

        List<String> errors = new ArrayList<>();
        int totalGrids = 0;
        int configuredGrids = 0;
        int skippedGrids = 0;
        int totalParts = 0;

        try {
            // Build GridIndex for fast Area grid lookup (includes Filler-Biomes from placementResult)
            GridIndex gridIndex = GridIndex.build(composition, placementResult);
            totalGrids = gridIndex.size();

            log.info("Built GridIndex with {} Area grids (including Filler-Biomes)", totalGrids);

            // Iterate over all Area grids and assemble RoadConfigParts
            for (String coordKey : gridIndex.keySet()) {
                GridEntry gridEntry = gridIndex.get(coordKey);
                if (gridEntry == null) {
                    continue;
                }

                FeatureHexGrid areaGrid = gridEntry.getFeatureHexGrid();
                Area area = gridEntry.getArea();

                try {
                    boolean configured = false;

                    // Check if there's already a road parameter (from Village)
                    String existingRoad = areaGrid.getParameters().get("road");

                    // Assemble road={} from RoadConfigParts (if any) OR existing road parameter
                    if (areaGrid.hasRoadConfigParts() || existingRoad != null) {
                        int partCount = areaGrid.hasRoadConfigParts() ? areaGrid.getRoadConfigParts().size() : 0;
                        String roadJson = buildRoadJsonFromParts(areaGrid.getRoadConfigParts(), areaGrid, existingRoad);
                        areaGrid.addParameter("road", roadJson);
                        configured = true;
                        totalParts += partCount;
                        log.info("Assembled road={} from {} parts + existing={} at {} (area: {})",
                            roadJson,
                            partCount,
                            existingRoad != null,
                            coordKey,
                            area.getName());
                    }

                    // Assemble river={} from RiverConfigParts
                    if (areaGrid.hasRiverConfigParts()) {
                        int riverPartCount = areaGrid.getRiverConfigParts().size();
                        String riverJson = buildRiverJsonFromParts(areaGrid.getRiverConfigParts(), areaGrid);
                        areaGrid.addParameter("river", riverJson);
                        configured = true;
                        totalParts += riverPartCount;
                        log.debug("Assembled river={} from {} parts at {} (area: {})",
                            riverJson.length(), riverPartCount, coordKey, area.getName());
                    }

                    // Assemble wall={} from WallConfigParts
                    if (areaGrid.hasWallConfigParts()) {
                        int wallPartCount = areaGrid.getWallConfigParts().size();
                        String wallJson = buildWallJsonFromParts(areaGrid.getWallConfigParts(), areaGrid);
                        areaGrid.addParameter("wall", wallJson);
                        configured = true;
                        totalParts += wallPartCount;
                        log.debug("Assembled wall={} from {} parts at {} (area: {})",
                            wallJson.length(), wallPartCount, coordKey, area.getName());
                    }

                    if (configured) {
                        configuredGrids++;
                    } else {
                        skippedGrids++;
                    }

                } catch (Exception e) {
                    errors.add("Grid " + coordKey + ": " + e.getMessage());
                    log.error("Failed to configure grid: {}", coordKey, e);
                    skippedGrids++;
                }
            }

            log.info("Road configuration complete: configured={}/{}, parts={}, skipped={}",
                configuredGrids, totalGrids, totalParts, skippedGrids);

            return RoadConfigurationResult.builder()
                .totalGrids(totalGrids)
                .configuredGrids(configuredGrids)
                .skippedGrids(skippedGrids)
                .totalSegments(totalParts)
                .success(errors.isEmpty())
                .errors(errors)
                .build();

        } catch (Exception e) {
            log.error("Road configuration failed", e);
            return RoadConfigurationResult.builder()
                .totalGrids(totalGrids)
                .configuredGrids(configuredGrids)
                .skippedGrids(skippedGrids)
                .totalSegments(totalParts)
                .success(false)
                .errorMessage(e.getMessage())
                .errors(errors)
                .build();
        }
    }

    /**
     * Builds road={} JSON configuration from RoadConfigParts and existing road parameter.
     *
     * Format as expected by RoadBuilder (see RoadBuilder.java line 23-44):
     * {
     *   "level": 95,           // Center level (required)
     *   "route": [             // Array of route entries (required)
     *     {
     *       "side": "W",       // EITHER side (NE, NW, SE, SW, E, W)
     *       "width": 3,        // Width in blocks
     *       "level": 95,       // Road level
     *       "type": "street"   // Road type (street, trail, etc.)
     *     },
     *     {
     *       "lx": 130,         // OR absolute position lx/lz
     *       "lz": 140,
     *       "width": 3,
     *       "level": 95,
     *       "type": "street"
     *     }
     *   ],
     *   "lx": 256,             // OPTIONAL - center x (default: flat.getSizeX()/2)
     *   "lz": 256,             // OPTIONAL - center z (default: flat.getSizeZ()/2)
     *   "plazaSize": 30,       // OPTIONAL - plaza at center
     *   "plazaMaterial": "street"  // OPTIONAL - plaza material
     * }
     *
     * @param roadParts List of RoadConfigParts from flows
     * @param grid The FeatureHexGrid
     * @param existingRoad Existing road parameter (from Village), may be null
     * @return JSON string for road parameter
     */
    private String buildRoadJsonFromParts(List<RoadConfigPart> roadParts, FeatureHexGrid grid, String existingRoad) {
        try {
            Map<String, Object> roadConfig = new HashMap<>();

            // Parse existing road parameter if present (from Village)
            Map<String, Object> existingRoadConfig = null;
            if (existingRoad != null && !existingRoad.isEmpty() && !existingRoad.equals("{}")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(existingRoad, Map.class);
                    existingRoadConfig = parsed;
                    log.debug("Parsed existing road parameter at {}: {}", grid.getPositionKey(), existingRoad);
                } catch (Exception e) {
                    log.warn("Failed to parse existing road parameter at {}: {}", grid.getPositionKey(), e.getMessage());
                }
            }

            // Separate CENTER parts from ROUTE parts
            RoadConfigPart centerPart = roadParts != null ? roadParts.stream()
                .filter(p -> p.getPartType() == RoadConfigPart.PartType.CENTER)
                .findFirst()
                .orElse(null) : null;

            List<RoadConfigPart> routeParts = roadParts != null ? roadParts.stream()
                .filter(p -> p.getPartType() == RoadConfigPart.PartType.ROUTE)
                .collect(Collectors.toList()) : new ArrayList<>();

            // Priority: CENTER part > existing road config > default
            // Add CENTER configuration (plaza, lx, lz, level)
            if (centerPart != null) {
                // Use CENTER part
                if (centerPart.getCenterLevel() != null) {
                    roadConfig.put("level", centerPart.getCenterLevel());
                }
                if (centerPart.getCenterLx() != null) {
                    roadConfig.put("lx", centerPart.getCenterLx());
                }
                if (centerPart.getCenterLz() != null) {
                    roadConfig.put("lz", centerPart.getCenterLz());
                }
                if (centerPart.getPlazaSize() != null) {
                    roadConfig.put("plazaSize", centerPart.getPlazaSize());
                }
                if (centerPart.getPlazaMaterial() != null) {
                    roadConfig.put("plazaMaterial", centerPart.getPlazaMaterial());
                }
            } else if (existingRoadConfig != null) {
                // Use existing road config (from Village)
                if (existingRoadConfig.containsKey("level")) {
                    roadConfig.put("level", existingRoadConfig.get("level"));
                }
                if (existingRoadConfig.containsKey("lx")) {
                    roadConfig.put("lx", existingRoadConfig.get("lx"));
                }
                if (existingRoadConfig.containsKey("lz")) {
                    roadConfig.put("lz", existingRoadConfig.get("lz"));
                }
                if (existingRoadConfig.containsKey("plazaSize")) {
                    roadConfig.put("plazaSize", existingRoadConfig.get("plazaSize"));
                }
                if (existingRoadConfig.containsKey("plazaMaterial")) {
                    roadConfig.put("plazaMaterial", existingRoadConfig.get("plazaMaterial"));
                }
            } else {
                // No CENTER part or existing config - calculate level from route parts
                Integer baseLevel = routeParts.stream()
                    .map(RoadConfigPart::getLevel)
                    .filter(level -> level != null)
                    .findFirst()
                    .orElse(95);
                roadConfig.put("level", baseLevel);
            }

            // Build route array from ROUTE parts
            List<Map<String, Object>> route = new ArrayList<>();
            Set<String> addedSides = new HashSet<>();

            for (RoadConfigPart part : routeParts) {
                Map<String, Object> entry = new HashMap<>();

                // Side-based routing
                if (part.getSide() != null) {
                    String sideKey = part.getSide().name();
                    // Skip duplicates
                    if (addedSides.contains(sideKey)) {
                        continue;
                    }
                    entry.put("side", sideKey);
                    addedSides.add(sideKey);
                }
                // Position-based routing
                else if (part.getRouteLx() != null && part.getRouteLz() != null) {
                    entry.put("lx", part.getRouteLx());
                    entry.put("lz", part.getRouteLz());
                }
                else {
                    log.warn("RoadConfigPart has neither side nor lx/lz at grid {}", grid.getPositionKey());
                    continue;
                }

                // Add common route fields
                if (part.getWidth() != null) {
                    entry.put("width", part.getWidth());
                }
                if (part.getLevel() != null) {
                    entry.put("level", part.getLevel());
                }
                if (part.getType() != null) {
                    entry.put("type", part.getType());
                }

                route.add(entry);
            }

            if (!route.isEmpty()) {
                roadConfig.put("route", route);
            }

            // Convert to JSON string
            return objectMapper.writeValueAsString(roadConfig);

        } catch (Exception e) {
            log.error("Failed to build road JSON from parts for grid {}", grid.getPositionKey(), e);
            return "{}";
        }
    }

    /**
     * Builds river={} JSON configuration from RiverConfigParts.
     *
     * Format:
     * {
     *   "from": [
     *     {"side": "NW", "width": 30, "depth": 10, "level": 90}
     *   ],
     *   "to": [
     *     {"side": "SW", "width": 50, "depth": 2, "level": 80}
     *   ],
     *   "groupId": "river-1234"
     * }
     *
     * @param riverParts List of RiverConfigParts
     * @param grid The FeatureHexGrid
     * @return JSON string for river parameter
     */
    private String buildRiverJsonFromParts(List<RiverConfigPart> riverParts, FeatureHexGrid grid) {
        try {
            Map<String, Object> riverConfig = new HashMap<>();

            // Separate FROM and TO parts
            List<RiverConfigPart> fromParts = riverParts.stream()
                .filter(p -> p.getPartType() == RiverConfigPart.PartType.FROM)
                .collect(Collectors.toList());

            List<RiverConfigPart> toParts = riverParts.stream()
                .filter(p -> p.getPartType() == RiverConfigPart.PartType.TO)
                .collect(Collectors.toList());

            // Build from array
            List<Map<String, Object>> fromArray = new ArrayList<>();
            Set<String> addedFromSides = new HashSet<>();
            String groupId = null;

            for (RiverConfigPart part : fromParts) {
                if (part.getSide() == null) continue;

                String sideKey = part.getSide().name();
                if (addedFromSides.contains(sideKey)) continue;

                Map<String, Object> entry = new HashMap<>();
                entry.put("side", sideKey);
                if (part.getWidth() != null) {
                    entry.put("width", part.getWidth());
                }
                if (part.getDepth() != null) {
                    entry.put("depth", part.getDepth());
                }
                if (part.getLevel() != null) {
                    entry.put("level", part.getLevel());
                }

                fromArray.add(entry);
                addedFromSides.add(sideKey);

                // Capture groupId from first part
                if (groupId == null && part.getGroupId() != null) {
                    groupId = part.getGroupId();
                }
            }

            // Build to array
            List<Map<String, Object>> toArray = new ArrayList<>();
            Set<String> addedToSides = new HashSet<>();

            for (RiverConfigPart part : toParts) {
                if (part.getSide() == null) continue;

                String sideKey = part.getSide().name();
                if (addedToSides.contains(sideKey)) continue;

                Map<String, Object> entry = new HashMap<>();
                entry.put("side", sideKey);
                if (part.getWidth() != null) {
                    entry.put("width", part.getWidth());
                }
                if (part.getDepth() != null) {
                    entry.put("depth", part.getDepth());
                }
                if (part.getLevel() != null) {
                    entry.put("level", part.getLevel());
                }

                toArray.add(entry);
                addedToSides.add(sideKey);

                // Capture groupId if not yet set
                if (groupId == null && part.getGroupId() != null) {
                    groupId = part.getGroupId();
                }
            }

            // Add arrays to config
            if (!fromArray.isEmpty()) {
                riverConfig.put("from", fromArray);
            }
            if (!toArray.isEmpty()) {
                riverConfig.put("to", toArray);
            }
            if (groupId != null) {
                riverConfig.put("groupId", groupId);
            }

            // Convert to JSON string
            return objectMapper.writeValueAsString(riverConfig);

        } catch (Exception e) {
            log.error("Failed to build river JSON from parts for grid {}", grid.getPositionKey(), e);
            return "{}";
        }
    }

    /**
     * Builds wall={} JSON configuration from WallConfigParts.
     *
     * Format:
     * {
     *   "segments": [
     *     {"side": "NE", "height": 10, "width": 3, "level": 95, "material": "stone"}
     *   ],
     *   "gates": [
     *     {"side": "W", "position": 256, "width": 20}
     *   ]
     * }
     *
     * @param wallParts List of WallConfigParts
     * @param grid The FeatureHexGrid
     * @return JSON string for wall parameter
     */
    private String buildWallJsonFromParts(List<WallConfigPart> wallParts, FeatureHexGrid grid) {
        try {
            Map<String, Object> wallConfig = new HashMap<>();

            // Separate SIDE and GATE parts
            List<WallConfigPart> sideParts = wallParts.stream()
                .filter(p -> p.getPartType() == WallConfigPart.PartType.SIDE)
                .collect(Collectors.toList());

            List<WallConfigPart> gateParts = wallParts.stream()
                .filter(p -> p.getPartType() == WallConfigPart.PartType.GATE)
                .collect(Collectors.toList());

            // Build segments array
            List<Map<String, Object>> segments = new ArrayList<>();
            Set<String> addedSides = new HashSet<>();

            for (WallConfigPart part : sideParts) {
                if (part.getSide() == null) continue;

                String sideKey = part.getSide().name();
                if (addedSides.contains(sideKey)) continue;

                Map<String, Object> entry = new HashMap<>();
                entry.put("side", sideKey);
                if (part.getHeight() != null) {
                    entry.put("height", part.getHeight());
                }
                if (part.getWidth() != null) {
                    entry.put("width", part.getWidth());
                }
                if (part.getLevel() != null) {
                    entry.put("level", part.getLevel());
                }
                if (part.getMaterial() != null) {
                    entry.put("material", part.getMaterial());
                }

                segments.add(entry);
                addedSides.add(sideKey);
            }

            // Build gates array
            List<Map<String, Object>> gates = new ArrayList<>();

            for (WallConfigPart part : gateParts) {
                if (part.getGateSide() == null) continue;

                Map<String, Object> entry = new HashMap<>();
                entry.put("side", part.getGateSide().name());
                if (part.getGatePosition() != null) {
                    entry.put("position", part.getGatePosition());
                }
                if (part.getGateWidth() != null) {
                    entry.put("width", part.getGateWidth());
                }

                gates.add(entry);
            }

            // Add arrays to config
            if (!segments.isEmpty()) {
                wallConfig.put("segments", segments);
            }
            if (!gates.isEmpty()) {
                wallConfig.put("gates", gates);
            }

            // Convert to JSON string
            return objectMapper.writeValueAsString(wallConfig);

        } catch (Exception e) {
            log.error("Failed to build wall JSON from parts for grid {}", grid.getPositionKey(), e);
            return "{}";
        }
    }


    /**
     * Calculates road level based on grid terrain height + offset.
     *
     * @param terrainLevel The base terrain level of the grid
     * @param segment The flow segment
     * @return Calculated road level
     */
    private int calculateRoadLevel(int terrainLevel, FlowSegment segment) {
        // Roads are "on top" of terrain
        // Use segment level if provided, otherwise terrain + default offset
        if (segment.getLevel() != null) {
            return segment.getLevel();
        }

        // Default: terrain level + 1 (roads slightly above ground)
        return terrainLevel + 1;
    }
}
