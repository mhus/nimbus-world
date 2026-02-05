package de.mhus.nimbus.world.generator.composer.village;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.point.Direction;
import de.mhus.nimbus.world.generator.composer.flow.StreetSegment;
import de.mhus.nimbus.world.generator.composer.pathfinding.EdgeSide;
import de.mhus.nimbus.world.generator.composer.pathfinding.HexCoord;
import de.mhus.nimbus.world.generator.composer.pathfinding.HexNodeType;
import de.mhus.nimbus.world.generator.composer.pathfinding.HexPath;
import de.mhus.nimbus.world.generator.composer.pathfinding.VillageHexPathfinder;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * VillageDesigner generates concrete village layouts from district and place definitions.
 *
 * This designer implements a 6-step process:
 * 1. Position Districts as separate grids
 * 2. Arrange places in each District based on slot size
 * 3. Find and assign buildings from BuildingIndex with size matching
 * 4. Draw streets through village connecting all districts and connection points
 * 5. Position buildings at street places
 * 6. Fill remaining areas with free places or empty buildings
 */
@Slf4j
public class VillageDesigner {

    private static final double OVERSIZE_TOLERANCE = 0.15; // 15% oversize tolerance

    // Divider to slot count mapping
    private static final Map<Integer, Integer> DIVIDER_TO_SLOTS = Map.of(
        1, 1,   // BIG: 1 slot
        3, 7,   // MEDIUM: 7 slots (but we use 3 from DistrictSlotSize)
        5, 19,  // SMALL: 19 slots (but we use 5 from DistrictSlotSize)
        7, 37   // TINY: 37 slots (but we use 7 from DistrictSlotSize)
    );

    private final BuildingIndex buildingIndex;
    private final Random random;

    /**
     * Creates a new VillageDesigner with the given building index.
     *
     * @param buildingIndex Index of available buildings
     */
    public VillageDesigner(BuildingIndex buildingIndex) {
        this.buildingIndex = buildingIndex;
        this.random = new Random();
    }

    /**
     * Designs a village layout from districts and places.
     *
     * @param village The village feature with districts and places
     * @param hexGridSize The size of each hex grid
     * @return VillageDesignResult with positioned districts and buildings
     */
    public VillageDesignResult design(Village village, int hexGridSize) {
        log.info("Designing village: {} (style: {}, hexGridSize: {})",
            village.getName(), village.getStyle(), hexGridSize);

        VillageDesignResult result = new VillageDesignResult();
        result.setVillage(village);
        result.setDistrictGrids(new ArrayList<>());
        result.setErrors(new ArrayList<>());

        try {
            // Step 1: Position Districts as separate grids
            log.debug("Step 1: Positioning districts");
            List<DistrictGrid> districtGrids = positionDistricts(village);
            result.setDistrictGrids(districtGrids);

            // Step 2: Arrange places in each District
            log.debug("Step 2: Arranging places in districts");
            for (DistrictGrid districtGrid : districtGrids) {
                arrangePlacesInDistrict(districtGrid, village, hexGridSize);
            }

            // Step 3: Find and assign buildings from BuildingIndex
            log.debug("Step 3: Assigning buildings to places");
            for (DistrictGrid districtGrid : districtGrids) {
                assignBuildingsToPlaces(districtGrid, village, hexGridSize);
            }

            // Step 4: Draw streets through village
            log.debug("Step 4: Drawing streets");
            drawStreets(districtGrids, village, hexGridSize);

            // Step 5: Position buildings at street places
            log.debug("Step 5: Positioning buildings");
            for (DistrictGrid districtGrid : districtGrids) {
                positionBuildings(districtGrid, village, hexGridSize);
            }

            // Step 6: Fill remaining areas
            log.debug("Step 6: Filling remaining areas");
            for (DistrictGrid districtGrid : districtGrids) {
                fillRemainingAreas(districtGrid, village, hexGridSize);
            }

            result.setSuccess(true);
            log.info("Village design completed: {} districts, {} total places",
                districtGrids.size(),
                districtGrids.stream().mapToInt(d -> d.getPlacedPlaces().size()).sum());

        } catch (Exception e) {
            result.setSuccess(false);
            result.getErrors().add("Village design failed: " + e.getMessage());
            log.error("Village design failed for '{}'", village.getName(), e);
        }

        return result;
    }

    /**
     * Step 1: Position Districts as separate grids.
     * Resolves relative positions (direction + anchorDistrict) to absolute grid positions.
     */
    private List<DistrictGrid> positionDistricts(Village village) {
        List<DistrictGrid> districtGrids = new ArrayList<>();

        if (village.getDistricts() == null || village.getDistricts().isEmpty()) {
            log.warn("Village '{}' has no districts defined", village.getName());
            return districtGrids;
        }

        // Resolve relative positions to absolute positions
        Map<String, HexVector2> districtPositions = resolveDistrictPositions(village.getDistricts());

        for (District district : village.getDistricts()) {
            HexVector2 gridPosition = districtPositions.get(district.getName());
            if (gridPosition == null) {
                log.warn("District '{}' could not be positioned, skipping", district.getName());
                continue;
            }

            DistrictGrid districtGrid = DistrictGrid.builder()
                .district(district)
                .gridPosition(gridPosition)
                .placedPlaces(new ArrayList<>())
                .streets(new ArrayList<>())
                .build();

            districtGrids.add(districtGrid);
            log.debug("Positioned district '{}' at grid [{},{}] with {} slot(s)",
                district.getName(),
                gridPosition.getQ(),
                gridPosition.getR(),
                district.getSlots() != null ? district.getSlots().getSlotCount() : 0);
        }

        return districtGrids;
    }

    /**
     * Resolves relative district positions to absolute hex coordinates.
     * Districts without direction/anchor are placed at origin (0,0).
     * Districts with direction are positioned relative to their anchor.
     */
    public static Map<String, HexVector2> resolveDistrictPositions(List<District> districts) {
        Map<String, HexVector2> positions = new HashMap<>();

        // First pass: Position center district(s) - those without direction
        for (District district : districts) {
            if (district.getDirection() == null) {
                positions.put(district.getName(), TypeUtil.hexVector2(0, 0));
                log.debug("District '{}' positioned at center (0,0)", district.getName());
            }
        }

        // Second pass: Position districts relative to anchors
        int maxIterations = districts.size() * 2; // Prevent infinite loops
        int iteration = 0;
        boolean changed = true;

        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;

            for (District district : districts) {
                // Skip if already positioned
                if (positions.containsKey(district.getName())) {
                    continue;
                }

                // Need both direction and anchor
                if (district.getDirection() == null) {
                    continue; // Already handled in first pass
                }

                String anchorName = district.getAnchorDistrict();
                HexVector2 anchorPos = positions.get(anchorName);

                if (anchorPos == null) {
                    // Anchor not yet positioned, try next iteration
                    continue;
                }

                // Calculate position based on direction from anchor
                HexVector2 newPos = applyDirection(anchorPos, district.getDirection());
                positions.put(district.getName(), newPos);
                changed = true;

                log.debug("District '{}' positioned {} of '{}' at [{},{}]",
                    district.getName(), district.getDirection(),
                    anchorName, newPos.getQ(), newPos.getR());
            }
        }

        // Warn about unpositioned districts
        for (District district : districts) {
            if (!positions.containsKey(district.getName())) {
                log.warn("District '{}' could not be positioned (missing anchor '{}'?)",
                    district.getName(), district.getAnchorDistrict());
            }
        }

        return positions;
    }

    /**
     * Applies a direction to a hex position to get the neighbor position.
     * IMPORTANT: Uses FLAT-TOP hexagon orientation for local hex grids.
     * The 6 direct neighbors for flat-top are: N, NE, SE, S, SW, NW
     * (E and W are not direct neighbors in flat-top orientation)
     */
    private static HexVector2 applyDirection(HexVector2 from, Direction direction) {
        int q = from.getQ();
        int r = from.getR();

        // Flat-top hexagon neighbors (6 directions)
        return switch (direction) {
            case N -> TypeUtil.hexVector2(q, r - 1);      // North
            case NE -> TypeUtil.hexVector2(q + 1, r - 1);  // NorthEast
            case SE -> TypeUtil.hexVector2(q + 1, r);      // SouthEast
            case S -> TypeUtil.hexVector2(q, r + 1);      // South
            case SW -> TypeUtil.hexVector2(q - 1, r + 1);  // SouthWest
            case NW -> TypeUtil.hexVector2(q - 1, r);      // NorthWest

            // E and W are not direct neighbors in flat-top, approximate with diagonal
            case E -> TypeUtil.hexVector2(q + 1, r);      // Same as SE
            case W -> TypeUtil.hexVector2(q - 1, r);      // Same as NW

            default -> from; // Stay at same position
        };
    }

    /**
     * Step 2: Arrange places in each District based on slot size.
     * Distributes the places within the grid according to the district's slot configuration.
     */
    private void arrangePlacesInDistrict(DistrictGrid districtGrid, Village village, int hexGridSize) {
        District district = districtGrid.getDistrict();

        if (district.getPlaces() == null || district.getPlaces().isEmpty()) {
            log.debug("District '{}' has no places to arrange", district.getName());
            return;
        }

        District.DistrictSlotSize slotSize = district.getSlots();
        if (slotSize == null) {
            log.warn("District '{}' has no slot size defined, using MEDIUM", district.getName());
            slotSize = District.DistrictSlotSize.MEDIUM;
        }

        int divider = getDividerFromSlotSize(slotSize);
        int availableSlots = slotSize.getSlotCount();

        log.debug("Arranging {} places in district '{}' (divider: {}, slots: {})",
            district.getPlaces().size(),
            district.getName(),
            divider,
            availableSlots);

        // Validate: Check if there are too many places for available slots
        if (district.getPlaces().size() > availableSlots) {
            String error = String.format(
                "Not enough space in district '%s': %d places but only %d slots available (slot size: %s, divider: %d)",
                district.getName(),
                district.getPlaces().size(),
                availableSlots,
                slotSize,
                divider);
            log.error(error);
            throw new IllegalStateException(error);
        }

        // For BIG districts (divider 1): Single large central building, no internal arrangement needed
        if (divider == 1) {
            if (district.getPlaces().isEmpty()) {
                log.warn("BIG district '{}' has no places defined", district.getName());
                return;
            }

            if (district.getPlaces().size() > 1) {
                String error = String.format(
                    "BIG district '%s' can only have 1 place, but %d places are defined",
                    district.getName(),
                    district.getPlaces().size());
                log.error(error);
                throw new IllegalStateException(error);
            }

            // Single place in center
            Place place = district.getPlaces().getFirst();
            PlacedPlace placedPlace = PlacedPlace.builder()
                .place(place)
                .localX(hexGridSize / 2)
                .localZ(hexGridSize / 2)
                .divider(divider)
                .slotIndex(0)
                .build();
            districtGrid.getPlacedPlaces().add(placedPlace);
            log.debug("Placed single large place '{}' at center", place.getName());
            return;
        }

        // For other districts: Distribute places across hexagonal slots
        List<HexVector2> slotPositions = getHexagonalSlotPositions(divider);

        if (slotPositions.size() < district.getPlaces().size()) {
            String error = String.format(
                "Not enough hexagonal slots for district '%s': %d places but only %d slots for divider %d",
                district.getName(),
                district.getPlaces().size(),
                slotPositions.size(),
                divider);
            log.error(error);
            throw new IllegalStateException(error);
        }

        int slotIndex = 0;
        for (Place place : district.getPlaces()) {
            HexVector2 hexPos = slotPositions.get(slotIndex);

            // Convert hex coordinates to cartesian
            int hexSlotSize = hexGridSize / divider;
            HexLocalPosition hexLocalPos = new HexLocalPosition(hexPos, divider, hexSlotSize);
            Vector2Int relativePos = HexLocalUtil.toHexGridLocalCenter(hexLocalPos);
            int localX = hexGridSize / 2 + relativePos.getX();
            int localZ = hexGridSize / 2 + relativePos.getZ();

            PlacedPlace placedPlace = PlacedPlace.builder()
                .place(place)
                .hexQ(hexPos.getQ())
                .hexR(hexPos.getR())
                .relativePosition(relativePos)
                .localX(localX)
                .localZ(localZ)
                .divider(divider)
                .slotIndex(slotIndex)
                .build();

            districtGrid.getPlacedPlaces().add(placedPlace);
            slotIndex++;

            log.debug("Placed '{}' at hex slot <{};{}> (divider {})",
                place.getName(), hexPos.getQ(), hexPos.getR(), divider);
        }
    }

    /**
     * Returns hexagonal slot positions for a given divider.
     *
     * Spec:
     * - Divider 1: 1 slot at <0;0>
     * - Divider 3: 7 slots (center + ring 1)
     * - Divider 5: 19 slots (center + ring 1 + ring 2)
     * - Divider 7: 37 slots (center + ring 1 + ring 2 + ring 3)
     */
    private List<HexVector2> getHexagonalSlotPositions(int divider) {
        List<HexVector2> positions = new ArrayList<>();

        // Center position
        positions.add(TypeUtil.hexVector2(0, 0));

        // Calculate number of rings based on divider
        int rings = switch (divider) {
            case 1 -> 0; // Only center
            case 3 -> 1; // Center + ring 1 = 7 slots
            case 5 -> 2; // Center + ring 1 + ring 2 = 19 slots
            case 7 -> 3; // Center + ring 1 + ring 2 + ring 3 = 37 slots
            default -> {
                log.warn("Unknown divider {}, using rings=0", divider);
                yield 0;
            }
        };

        // Add hexagonal rings around center
        for (int ring = 1; ring <= rings; ring++) {
            positions.addAll(getHexRing(0, 0, ring));
        }

        log.debug("Generated {} hexagonal slot positions for divider {}", positions.size(), divider);
        return positions;
    }

    /**
     * Returns all hex positions in a ring around a center position.
     * Ring radius 1 gives 6 positions, radius 2 gives 12 positions, etc.
     */
    private List<HexVector2> getHexRing(int centerQ, int centerR, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        // Start at position directly north of center
        int q = centerQ;
        int r = centerR - radius;

        // Direction vectors for FLAT-TOP hex neighbors (clockwise from N)
        // The 6 directions are: SE, S, SW, NW, N, NE
        int[][] directions = {
            {1, 0},    // SE (move along q)
            {0, 1},    // S  (move along r)
            {-1, 1},   // SW (move along -q, +r)
            {-1, 0},   // NW (move along -q)
            {0, -1},   // N  (move along -r)
            {1, -1}    // NE (move along +q, -r)
        };

        // Walk around the ring clockwise
        for (int side = 0; side < 6; side++) {
            for (int step = 0; step < radius; step++) {
                ring.add(TypeUtil.hexVector2(q, r));
                q += directions[side][0];
                r += directions[side][1];
            }
        }

        return ring;
    }

    /**
     * Step 3: Find and assign buildings from BuildingIndex with size matching.
     */
    private void assignBuildingsToPlaces(DistrictGrid districtGrid, Village village, int hexGridSize) {
        for (PlacedPlace placedPlace : districtGrid.getPlacedPlaces()) {
            Place place = placedPlace.getPlace();

            // Only process BuildingPlace
            if (!(place instanceof BuildingPlace buildingPlace)) {
                continue;
            }

            String style = buildingPlace.getStyle() != null ? buildingPlace.getStyle() : village.getStyle();
            String kind = buildingPlace.getKind();

            if (kind == null || kind.isBlank()) {
                log.warn("BuildingPlace '{}' has no kind defined", place.getName());
                continue;
            }

            // Find matching buildings
            List<BuildingDefinition> candidates = buildingIndex.findBuildings(style, kind);
            if (candidates.isEmpty()) {
                log.warn("No buildings found for style '{}' and kind '{}'", style, kind);
                continue;
            }

            // Calculate size range for this place
            int divider = placedPlace.getDivider();
            int maxSize = calculateMaxBuildingSize(divider, hexGridSize);
            int maxSizeWithTolerance = (int) (maxSize * (1 + OVERSIZE_TOLERANCE));

            // Filter buildings by size
            List<BuildingDefinition> matchingBuildings = new ArrayList<>();
            for (BuildingDefinition building : candidates) {
                if (building.getDimensions() != null) {
                    Vector3Int dims = building.getDimensions();
                    int buildingSize = Math.max(dims.getX(), dims.getZ());

                    if (buildingSize <= maxSizeWithTolerance) {
                        matchingBuildings.add(building);
                    }
                }
            }

            // Select random building from matching candidates
            if (!matchingBuildings.isEmpty()) {
                BuildingDefinition selected = matchingBuildings.get(random.nextInt(matchingBuildings.size()));
                placedPlace.setBuildingId(selected.getBuildingId());

                // Mark as oversized if necessary
                if (selected.getDimensions() != null) {
                    Vector3Int dims = selected.getDimensions();
                    int buildingSize = Math.max(dims.getX(), dims.getZ());
                    placedPlace.setOversized(buildingSize > maxSize);
                }

                log.debug("Assigned building '{}' to place '{}'{}",
                    selected.getBuildingId(),
                    place.getName(),
                    placedPlace.isOversized() ? " (OVERSIZED)" : "");
            } else {
                log.warn("No buildings found that fit size {} (with {}% tolerance) for place '{}'",
                    maxSize, (int)(OVERSIZE_TOLERANCE * 100), place.getName());
            }
        }
    }

    /**
     * Step 4: Draw streets through village connecting districts and connection points.
     * Uses A* pathfinding over hex graph for optimal street routing.
     */
    private void drawStreets(List<DistrictGrid> districtGrids, Village village, int hexGridSize) {
        log.debug("Drawing streets across {} districts", districtGrids.size());

        // Step 1: Build hex coordinate graph for pathfinding
        log.debug("Building hex graph for pathfinding...");
        Map<String, HexCoord> hexGraph = buildHexGraph(districtGrids, hexGridSize);

        // Build a map of all connection points across all districts
        Map<HexVector2, List<ConnectionPoint>> connectionPointsByDistrict = new HashMap<>();
        List<ConnectionPoint> allConnectionPoints = new ArrayList<>();

        for (DistrictGrid districtGrid : districtGrids) {
            List<ConnectionPoint> districtConnectionPoints = new ArrayList<>();

            for (PlacedPlace placedPlace : districtGrid.getPlacedPlaces()) {
                if (placedPlace.isConnectionPoint()) {
                    ConnectionPoint cp = ConnectionPoint.builder()
                        .districtGrid(districtGrid)
                        .placedPlace(placedPlace)
                        .districtPosition(districtGrid.getGridPosition())
                        .localX(placedPlace.getLocalX())
                        .localZ(placedPlace.getLocalZ())
                        .build();

                    districtConnectionPoints.add(cp);
                    allConnectionPoints.add(cp);

                    log.info("CONNECTION_POINT: district='{}' name='{}' hex=<{};{}> local=({},{})",
                        districtGrid.getName(), placedPlace.getPlace().getName(),
                        placedPlace.getHexQ(), placedPlace.getHexR(),
                        placedPlace.getLocalX(), placedPlace.getLocalZ());
                }
            }

            connectionPointsByDistrict.put(districtGrid.getGridPosition(), districtConnectionPoints);
            log.info("District '{}' at [{},{}] has {} connection point(s)",
                districtGrid.getName(),
                districtGrid.getGridPosition().getQ(),
                districtGrid.getGridPosition().getR(),
                districtConnectionPoints.size());
        }

        if (allConnectionPoints.isEmpty()) {
            log.debug("No connection points defined, skipping street generation");
            return;
        }

        // Phase 1: Connect all districts (inter-district streets)
        // TODO: Replace with pathfinding version in future
        connectDistrictBoundaries(districtGrids, connectionPointsByDistrict, village, hexGridSize);

        // Phase 2: Connect internal connection points within each district using pathfinding
        for (DistrictGrid districtGrid : districtGrids) {
            District district = districtGrid.getDistrict();

            // Skip internal streets for BIG districts (divider 1)
            if (district.getSlots() == District.DistrictSlotSize.BIG) {
                log.debug("Skipping internal streets for BIG district '{}'", district.getName());
                continue;
            }

            connectInternalConnectionPointsWithPathfinding(districtGrid, village, hexGridSize, hexGraph);
        }

        log.debug("Street generation completed");
    }

    /**
     * Connects districts by finding adjacent districts and creating streets between them.
     * Considers the entire village as one cohesive city map.
     */
    private void connectDistrictBoundaries(List<DistrictGrid> districtGrids,
                                          Map<HexVector2, List<ConnectionPoint>> connectionPointsByDistrict,
                                          Village village,
                                          int hexGridSize) {
        log.debug("Connecting district boundaries");

        // For each district, find its neighbors and connect them
        for (DistrictGrid districtGrid : districtGrids) {
            HexVector2 pos = districtGrid.getGridPosition();

            // Check all 6 hex directions for neighbors (flat-top orientation)
            List<HexVector2> neighborDirections = List.of(
                TypeUtil.hexVector2(pos.getQ() + 1, pos.getR()),     // SE
                TypeUtil.hexVector2(pos.getQ() + 1, pos.getR() - 1), // NE
                TypeUtil.hexVector2(pos.getQ(), pos.getR() - 1),     // N
                TypeUtil.hexVector2(pos.getQ() - 1, pos.getR()),     // NW
                TypeUtil.hexVector2(pos.getQ() - 1, pos.getR() + 1), // SW
                TypeUtil.hexVector2(pos.getQ(), pos.getR() + 1)      // S
            );

            for (HexVector2 neighborPos : neighborDirections) {
                // Check if neighbor exists
                DistrictGrid neighborGrid = findDistrictByPosition(districtGrids, neighborPos);
                if (neighborGrid == null) {
                    continue;
                }

                // Get connection points from both districts
                List<ConnectionPoint> currentPoints = connectionPointsByDistrict.get(pos);
                List<ConnectionPoint> neighborPoints = connectionPointsByDistrict.get(neighborPos);

                if (currentPoints == null || currentPoints.isEmpty() ||
                    neighborPoints == null || neighborPoints.isEmpty()) {
                    log.debug("No connection points between districts [{},{}] and [{},{}]",
                        pos.getQ(), pos.getR(), neighborPos.getQ(), neighborPos.getR());
                    continue;
                }

                // Find the closest connection points between districts and connect them
                connectClosestPointsAcrossBoundary(
                    districtGrid, neighborGrid,
                    currentPoints, neighborPoints,
                    village, hexGridSize);
            }
        }
    }

    /**
     * Connects the closest connection points across a district boundary.
     * The street must align at the edge of both grids.
     */
    private void connectClosestPointsAcrossBoundary(DistrictGrid district1,
                                                     DistrictGrid district2,
                                                     List<ConnectionPoint> points1,
                                                     List<ConnectionPoint> points2,
                                                     Village village,
                                                     int hexGridSize) {
        // Find closest pair
        ConnectionPoint closest1 = null;
        ConnectionPoint closest2 = null;
        double minDistance = Double.MAX_VALUE;

        for (ConnectionPoint cp1 : points1) {
            for (ConnectionPoint cp2 : points2) {
                double dist = calculateDistance(cp1, cp2, hexGridSize);
                if (dist < minDistance) {
                    minDistance = dist;
                    closest1 = cp1;
                    closest2 = cp2;
                }
            }
        }

        if (closest1 == null) {
            return;
        }

        // Calculate edge position where the street crosses the boundary
        HexVector2 pos1 = district1.getGridPosition();
        HexVector2 pos2 = district2.getGridPosition();

        // Determine which edge (calculate direction)
        int dq = pos2.getQ() - pos1.getQ();
        int dr = pos2.getR() - pos1.getR();

        // Calculate edge coordinates for both districts
        EdgeConnection edge = calculateEdgeConnection(
            closest1.getLocalX(), closest1.getLocalZ(),
            closest2.getLocalX(), closest2.getLocalZ(),
            dq, dr, hexGridSize);

        // Add street segment in district 1 (from connection point to edge)
        StreetSegment segment1 = StreetSegment.builder()
            .fromX(closest1.getLocalX())
            .fromZ(closest1.getLocalZ())
            .toX(edge.getEdge1X())
            .toZ(edge.getEdge1Z())
            .width(4)
            .type("street")
            .level(village.getBaseLevel())
            .build();
        district1.getStreets().add(segment1);

        // Add street segment in district 2 (from edge to connection point)
        StreetSegment segment2 = StreetSegment.builder()
            .fromX(edge.getEdge2X())
            .fromZ(edge.getEdge2Z())
            .toX(closest2.getLocalX())
            .toZ(closest2.getLocalZ())
            .width(4)
            .type("street")
            .level(village.getBaseLevel())
            .build();
        district2.getStreets().add(segment2);

        log.debug("Connected districts [{},{}] and [{},{}] via edge at [{},{}/{}] <-> [{},{}/{}]",
            pos1.getQ(), pos1.getR(), pos2.getQ(), pos2.getR(),
            edge.getEdge1X(), edge.getEdge1Z(), closest1.getPlace().getName(),
            edge.getEdge2X(), edge.getEdge2Z(), closest2.getPlace().getName());
    }

    /**
     * Connects all internal connection points within a district using A* pathfinding.
     * Uses minimum spanning tree strategy to connect all points optimally.
     */
    private void connectInternalConnectionPointsWithPathfinding(
            DistrictGrid districtGrid,
            Village village,
            int hexGridSize,
            Map<String, HexCoord> hexGraph) {

        // Find all connection point HexCoords in this district
        List<HexCoord> connectionCoords = hexGraph.values().stream()
                .filter(coord -> coord.getDistrictName().equals(districtGrid.getName()))
                .filter(coord -> coord.getType() == HexNodeType.CONNECTION_POINT)
                .toList();

        if (connectionCoords.size() < 2) {
            log.debug("District '{}' has {} connection point(s), skipping internal connections",
                    districtGrid.getName(), connectionCoords.size());
            return;
        }

        log.debug("Connecting {} internal connection points in district '{}' using pathfinding",
                connectionCoords.size(), districtGrid.getName());

        // Minimum spanning tree approach: connect each unconnected point to the nearest connected point
        Set<HexCoord> connected = new HashSet<>();
        connected.add(connectionCoords.getFirst()); // Start with first point

        VillageHexPathfinder pathfinder = new VillageHexPathfinder();
        int pathsCreated = 0;

        while (connected.size() < connectionCoords.size()) {
            // Find closest unconnected point to any connected point
            HexCoord closestUnconnected = null;
            HexCoord closestConnected = null;
            double minDistance = Double.MAX_VALUE;

            for (HexCoord unconnected : connectionCoords) {
                if (connected.contains(unconnected)) continue;

                for (HexCoord conn : connected) {
                    double dist = unconnected.cartesianDistance(conn);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestUnconnected = unconnected;
                        closestConnected = conn;
                    }
                }
            }

            if (closestUnconnected == null) {
                log.warn("Could not find unconnected point in district '{}'", districtGrid.getName());
                break;
            }

            // Find path between the two points
            HexPath path = pathfinder.findPath(closestConnected, closestUnconnected);

            if (path != null) {
                // Convert path to street segments
                List<StreetSegment> segments = convertHexPathToSegments(path, village, hexGridSize);
                districtGrid.getStreets().addAll(segments);
                pathsCreated++;

                log.debug("Created path in '{}': {} -> {} ({} segments)",
                        districtGrid.getName(),
                        closestConnected.getConnectionPointName(),
                        closestUnconnected.getConnectionPointName(),
                        segments.size());
            } else {
                log.warn("Could not find path between connection points in district '{}'",
                        districtGrid.getName());
            }

            // Mark as connected
            connected.add(closestUnconnected);
        }

        log.info("District '{}': Created {} paths connecting {} connection points",
                districtGrid.getName(), pathsCreated, connectionCoords.size());
    }

    /**
     * Builds a hex coordinate graph for pathfinding across all districts.
     * Converts all local hex coordinates to HexCoord objects with cartesian X,Z.
     *
     * @param districtGrids List of district grids
     * @param hexGridSize Size of each hex grid
     * @return Map of coordinate key to HexCoord
     */
    private Map<String, HexCoord> buildHexGraph(List<DistrictGrid> districtGrids, int hexGridSize) {
        Map<String, HexCoord> hexGraph = new HashMap<>();

        // Step 1: Convert all local hex centers to HexCoord objects
        for (DistrictGrid districtGrid : districtGrids) {
            District district = districtGrid.getDistrict();
            int divider = getDividerFromSlotSize(district.getSlots() != null ?
                    district.getSlots() : District.DistrictSlotSize.MEDIUM);

            // Skip BIG districts (divider=1) - no internal streets
            if (divider == 1) {
                log.debug("Skipping hex graph for BIG district '{}' (divider=1)", district.getName());
                continue;
            }

            int hexRange = (divider - 1) / 2;
            int hexSlotSize = hexGridSize / divider;

            log.debug("Building hex graph for district '{}' (divider={}, range={})",
                    district.getName(), divider, hexRange);

            // Create HexCoord for each local hex cell
            for (int q = -hexRange; q <= hexRange; q++) {
                for (int r = -hexRange; r <= hexRange; r++) {
                    // Skip if outside hexagonal shape
                    if (Math.abs(q + r) > hexRange) {
                        continue;
                    }

                    // Convert hex (q,r) to cartesian (x,z)
                    HexLocalPosition hexPos = new HexLocalPosition(
                            TypeUtil.hexVector2(q, r), divider, hexSlotSize);
                    Vector2Int relativePos = HexLocalUtil.toHexGridLocalCenter(hexPos);

                    // Convert from relative to absolute (within grid)
                    int x = hexGridSize / 2 + relativePos.getX();
                    int z = hexGridSize / 2 + relativePos.getZ();

                    HexCoord coord = new HexCoord(districtGrid, q, r, x, z);
                    hexGraph.put(coord.getKey(), coord);
                }
            }

            // Mark occupied cells and connection points
            for (PlacedPlace placedPlace : districtGrid.getPlacedPlaces()) {
                String key = String.format("%s:%d,%d", districtGrid.getName(),
                        placedPlace.getHexQ(), placedPlace.getHexR());
                HexCoord coord = hexGraph.get(key);
                if (coord != null) {
                    if (placedPlace.isConnectionPoint()) {
                        coord.markConnectionPoint(placedPlace.getName());
                    } else if (placedPlace.getBuildingId() != null) {
                        coord.markOccupied();
                    }
                }
            }
        }

        // Step 2: Link neighbors within districts
        for (HexCoord coord : hexGraph.values()) {
            linkHexNeighbors(coord, hexGraph);
        }

        // Step 3: Link neighbors across district boundaries
        // Find HexCoords in adjacent districts that are close (for cross-district pathfinding)
        for (HexCoord coord : hexGraph.values()) {
            linkCrossDistrictNeighbors(coord, hexGraph, hexGridSize);
        }

        log.info("Built hex graph with {} coordinates", hexGraph.size());
        return hexGraph;
    }

    /**
     * Links a hex coordinate to its 6 neighbors within the same district.
     */
    private void linkHexNeighbors(HexCoord coord, Map<String, HexCoord> hexGraph) {
        // Flat-top hexagon neighbors: N, NE, SE, S, SW, NW
        int[][] directions = {
                {0, -1},   // N
                {1, -1},   // NE
                {1, 0},    // SE
                {0, 1},    // S
                {-1, 1},   // SW
                {-1, 0}    // NW
        };

        for (int[] dir : directions) {
            int nq = coord.getLocalQ() + dir[0];
            int nr = coord.getLocalR() + dir[1];
            String neighborKey = String.format("%s:%d,%d", coord.getDistrictName(), nq, nr);

            HexCoord neighbor = hexGraph.get(neighborKey);
            if (neighbor != null) {
                coord.addNeighbor(neighbor);
            }
        }
    }

    /**
     * Converts a HexPath to StreetSegments.
     * When path crosses district boundaries, calculates edge positions dynamically.
     *
     * @param path The hex path from pathfinding
     * @param village Village for base level
     * @param hexGridSize Size of hex grid
     * @return List of street segments
     */
    private List<StreetSegment> convertHexPathToSegments(HexPath path, Village village, int hexGridSize) {
        List<StreetSegment> segments = new ArrayList<>();

        if (path.getCoords().isEmpty()) {
            return segments;
        }

        // Walk through path and create segments
        for (int i = 0; i < path.getCoords().size() - 1; i++) {
            HexCoord from = path.getCoords().get(i);
            HexCoord to = path.getCoords().get(i + 1);

            // Check if crossing district boundary
            if (!from.getDistrictName().equals(to.getDistrictName())) {
                // District crossing - need to calculate edge position
                segments.addAll(createCrossingSegments(from, to, village, hexGridSize));
            } else {
                // Same district - direct segment
                StreetSegment segment = StreetSegment.builder()
                        .fromX(from.getX())
                        .fromZ(from.getZ())
                        .toX(to.getX())
                        .toZ(to.getZ())
                        .width(3)
                        .type("street")
                        .level(village.getBaseLevel())
                        .build();
                segments.add(segment);

                log.debug("STREET_SEGMENT: district='{}' FROM=({},{}) TO=({},{}) type='street'",
                        from.getDistrictName(), from.getX(), from.getZ(), to.getX(), to.getZ());
            }
        }

        return segments;
    }

    /**
     * Creates street segments for crossing district boundary.
     * Calculates edge position between the two districts and splits into two segments.
     *
     * @param from HexCoord in first district
     * @param to HexCoord in second district
     * @param village Village for base level
     * @param hexGridSize Size of hex grid
     * @return List with 2 segments (one per district)
     */
    private List<StreetSegment> createCrossingSegments(HexCoord from, HexCoord to,
                                                        Village village, int hexGridSize) {
        List<StreetSegment> segments = new ArrayList<>();

        // Calculate direction between districts
        int dq = to.getDistrictQ() - from.getDistrictQ();
        int dr = to.getDistrictR() - from.getDistrictR();

        // Calculate edge positions using existing calculateEdgeConnection method
        EdgeConnection edge = calculateEdgeConnection(
                from.getX(), from.getZ(),
                to.getX(), to.getZ(),
                dq, dr, hexGridSize);

        // Segment 1: from -> edge in district 1
        StreetSegment segment1 = StreetSegment.builder()
                .fromX(from.getX())
                .fromZ(from.getZ())
                .toX(edge.getEdge1X())
                .toZ(edge.getEdge1Z())
                .width(4) // Slightly wider for inter-district
                .type("street")
                .level(village.getBaseLevel())
                .build();
        segments.add(segment1);

        // Segment 2: edge -> to in district 2
        StreetSegment segment2 = StreetSegment.builder()
                .fromX(edge.getEdge2X())
                .fromZ(edge.getEdge2Z())
                .toX(to.getX())
                .toZ(to.getZ())
                .width(4)
                .type("street")
                .level(village.getBaseLevel())
                .build();
        segments.add(segment2);

        log.debug("STREET_CROSSING: '{}' ({},{}) -> EDGE ({},{}) | EDGE ({},{}) -> '{}' ({},{})",
                from.getDistrictName(), from.getX(), from.getZ(),
                edge.getEdge1X(), edge.getEdge1Z(),
                edge.getEdge2X(), edge.getEdge2Z(),
                to.getDistrictName(), to.getX(), to.getZ());

        return segments;
    }

    /**
     * Checks if (dq, dr) represents an adjacent hex direction.
     */
    private boolean isAdjacentHexDirection(int dq, int dr) {
        return (dq == 0 && dr == -1) ||  // N
                (dq == 1 && dr == -1) ||  // NE
                (dq == 1 && dr == 0) ||   // SE
                (dq == 0 && dr == 1) ||   // S
                (dq == -1 && dr == 1) ||  // SW
                (dq == -1 && dr == 0);    // NW
    }

    /**
     * Links a hex coordinate to neighbors in adjacent districts.
     * Finds HexCoords in neighboring districts that are geometrically close.
     */
    private void linkCrossDistrictNeighbors(HexCoord coord, Map<String, HexCoord> hexGraph, int hexGridSize) {
        // Get all HexCoords from neighboring districts
        List<HexCoord> candidateNeighbors = hexGraph.values().stream()
                .filter(other -> !other.getDistrictName().equals(coord.getDistrictName()))
                .filter(other -> isDistrictAdjacent(coord.getDistrict(), other.getDistrict()))
                .toList();

        if (candidateNeighbors.isEmpty()) {
            return;
        }

        // Find the closest HexCoords in adjacent districts
        // Link if cartesian distance is within threshold (about 1.5 * hex size)
        double threshold = hexGridSize * 1.5;

        List<HexCoord> closeNeighbors = candidateNeighbors.stream()
                .filter(other -> coord.cartesianDistance(other) < threshold)
                .sorted((a, b) -> Double.compare(coord.cartesianDistance(a), coord.cartesianDistance(b)))
                .limit(3) // Max 3 cross-district neighbors
                .toList();

        for (HexCoord neighbor : closeNeighbors) {
            coord.addNeighbor(neighbor);
            log.debug("Linked cross-district: {} -> {} (distance: {})",
                    coord.getKey(), neighbor.getKey(), (int)coord.cartesianDistance(neighbor));
        }
    }

    /**
     * Checks if two districts are adjacent in the hex grid.
     */
    private boolean isDistrictAdjacent(DistrictGrid district1, DistrictGrid district2) {
        int dq = district2.getGridPosition().getQ() - district1.getGridPosition().getQ();
        int dr = district2.getGridPosition().getR() - district1.getGridPosition().getR();
        return isAdjacentHexDirection(dq, dr);
    }

    /**
     * Gets edge side from direction vector.
     */
    private EdgeSide getEdgeSideFromDirection(int dq, int dr) {
        if (dq == 0 && dr == -1) return EdgeSide.N;
        if (dq == 1 && dr == -1) return EdgeSide.NE;
        if (dq == 1 && dr == 0) return EdgeSide.SE;
        if (dq == 0 && dr == 1) return EdgeSide.S;
        if (dq == -1 && dr == 1) return EdgeSide.SW;
        if (dq == -1 && dr == 0) return EdgeSide.NW;
        return EdgeSide.N; // default
    }

    /**
     * Calculates the edge connection points where a street crosses between two adjacent districts.
     */
    private EdgeConnection calculateEdgeConnection(int local1X, int local1Z,
                                                    int local2X, int local2Z,
                                                    int dq, int dr,
                                                    int hexGridSize) {
        // Determine which edge we're crossing based on direction (FLAT-TOP orientation)
        // Flat-top hexagons have N/S horizontal edges and NE/NW/SE/SW diagonal corners
        int edge1X, edge1Z, edge2X, edge2Z;

        if (dq == 1 && dr == 0) { // SE - diagonal corner (bottom-right to top-left)
            edge1X = hexGridSize; // Right edge of grid 1
            edge1Z = hexGridSize; // Bottom of grid 1
            edge2X = 0; // Left edge of grid 2
            edge2Z = 0; // Top of grid 2
        } else if (dq == -1 && dr == 0) { // NW - diagonal corner (top-left to bottom-right)
            edge1X = 0; // Left edge of grid 1
            edge1Z = 0; // Top of grid 1
            edge2X = hexGridSize; // Right edge of grid 2
            edge2Z = hexGridSize; // Bottom of grid 2
        } else if (dq == 0 && dr == -1) { // N - horizontal edge (top)
            edge1X = local1X;
            edge1Z = 0; // Top edge of grid 1
            edge2X = local2X;
            edge2Z = hexGridSize; // Bottom edge of grid 2
        } else if (dq == 0 && dr == 1) { // S - horizontal edge (bottom)
            edge1X = local1X;
            edge1Z = hexGridSize; // Bottom edge of grid 1
            edge2X = local2X;
            edge2Z = 0; // Top edge of grid 2
        } else if (dq == 1 && dr == -1) { // NE - diagonal corner (top-right to bottom-left)
            edge1X = hexGridSize; // Right of grid 1
            edge1Z = 0; // Top of grid 1
            edge2X = 0; // Left of grid 2
            edge2Z = hexGridSize; // Bottom of grid 2
        } else if (dq == -1 && dr == 1) { // SW - diagonal corner (bottom-left to top-right)
            edge1X = 0; // Left of grid 1
            edge1Z = hexGridSize; // Bottom of grid 1
            edge2X = hexGridSize; // Right of grid 2
            edge2Z = 0; // Top of grid 2
        } else {
            // Default fallback
            edge1X = local1X;
            edge1Z = local1Z;
            edge2X = local2X;
            edge2Z = local2Z;
        }

        return EdgeConnection.builder()
            .edge1X(edge1X)
            .edge1Z(edge1Z)
            .edge2X(edge2X)
            .edge2Z(edge2Z)
            .build();
    }

    /**
     * Finds a district by its grid position.
     */
    private DistrictGrid findDistrictByPosition(List<DistrictGrid> districtGrids, HexVector2 position) {
        return districtGrids.stream()
            .filter(d -> d.getGridPosition().getQ() == position.getQ() &&
                        d.getGridPosition().getR() == position.getR())
            .findFirst()
            .orElse(null);
    }

    /**
     * Calculates distance between two connection points (considering their district positions).
     */
    private double calculateDistance(ConnectionPoint cp1, ConnectionPoint cp2, int hexGridSize) {
        // Calculate absolute positions in the city map
        int abs1X = cp1.getDistrictPosition().getQ() * hexGridSize + cp1.getLocalX();
        int abs1Z = cp1.getDistrictPosition().getR() * hexGridSize + cp1.getLocalZ();
        int abs2X = cp2.getDistrictPosition().getQ() * hexGridSize + cp2.getLocalX();
        int abs2Z = cp2.getDistrictPosition().getR() * hexGridSize + cp2.getLocalZ();

        int dx = abs2X - abs1X;
        int dz = abs2Z - abs1Z;

        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Step 5: Position buildings at street places.
     * Orients buildings based on the nearest street and validates positions.
     */
    private void positionBuildings(DistrictGrid districtGrid, Village village, int hexGridSize) {
        log.debug("Positioning buildings in district '{}'", districtGrid.getName());

        List<StreetSegment> streets = districtGrid.getStreets();
        if (streets.isEmpty()) {
            log.debug("No streets in district '{}', buildings will use default rotation", districtGrid.getName());
        }

        // For each building place, find the nearest street and orient the building towards it
        for (PlacedPlace placedPlace : districtGrid.getPlacedPlaces()) {
            // Only process BuildingPlace with assigned buildings
            if (!(placedPlace.getPlace() instanceof BuildingPlace)) {
                continue;
            }

            if (placedPlace.getBuildingId() == null) {
                log.debug("Building place '{}' has no building assigned, skipping", placedPlace.getName());
                continue;
            }

            // Find nearest street
            StreetSegment nearestStreet = findNearestStreet(placedPlace, streets);
            if (nearestStreet != null) {
                // Calculate rotation based on street direction
                int rotation = calculateBuildingRotation(placedPlace, nearestStreet);
                placedPlace.setRotation(rotation);

                log.debug("Building '{}' oriented with rotation {} towards nearest street",
                    placedPlace.getName(), rotation);
            } else {
                // No street nearby, use default rotation (0 = facing north)
                placedPlace.setRotation(0);
                log.debug("Building '{}' uses default rotation (no nearby street)",
                    placedPlace.getName());
            }
        }

        log.debug("Positioned {} buildings in district '{}'",
            districtGrid.getPlacedPlaces().stream()
                .filter(p -> p.getPlace() instanceof BuildingPlace && p.getBuildingId() != null)
                .count(),
            districtGrid.getName());
    }

    /**
     * Finds the nearest street segment to a placed place.
     */
    private StreetSegment findNearestStreet(PlacedPlace placedPlace, List<StreetSegment> streets) {
        if (streets.isEmpty()) {
            return null;
        }

        StreetSegment nearest = null;
        double minDistance = Double.MAX_VALUE;

        int placeX = placedPlace.getLocalX();
        int placeZ = placedPlace.getLocalZ();

        for (StreetSegment street : streets) {
            // Calculate distance from place to street segment
            double distance = distanceToSegment(
                placeX, placeZ,
                street.getFromX(), street.getFromZ(),
                street.getToX(), street.getToZ());

            if (distance < minDistance) {
                minDistance = distance;
                nearest = street;
            }
        }

        return nearest;
    }

    /**
     * Calculates the distance from a point to a line segment.
     */
    private double distanceToSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        // Vector from segment start to point
        double dx = px - x1;
        double dz = pz - z1;

        // Vector of the segment
        double segDx = x2 - x1;
        double segDz = z2 - z1;

        // Length squared of segment
        double segLengthSq = segDx * segDx + segDz * segDz;

        if (segLengthSq == 0) {
            // Segment is a point
            return Math.sqrt(dx * dx + dz * dz);
        }

        // Project point onto segment (parametric t)
        double t = (dx * segDx + dz * segDz) / segLengthSq;
        t = Math.max(0, Math.min(1, t)); // Clamp to [0, 1]

        // Closest point on segment
        double closestX = x1 + t * segDx;
        double closestZ = z1 + t * segDz;

        // Distance from point to closest point on segment
        double distX = px - closestX;
        double distZ = pz - closestZ;

        return Math.sqrt(distX * distX + distZ * distZ);
    }

    /**
     * Calculates the rotation for a building to face the nearest street.
     * Returns rotation in degrees: 0 (north), 90 (east), 180 (south), 270 (west)
     */
    private int calculateBuildingRotation(PlacedPlace placedPlace, StreetSegment street) {
        int placeX = placedPlace.getLocalX();
        int placeZ = placedPlace.getLocalZ();

        // Find the closest point on the street
        double dx = street.getToX() - street.getFromX();
        double dz = street.getToZ() - street.getFromZ();

        double segLengthSq = dx * dx + dz * dz;
        if (segLengthSq == 0) {
            return 0; // Street is a point, default rotation
        }

        // Project place onto street
        double t = ((placeX - street.getFromX()) * dx + (placeZ - street.getFromZ()) * dz) / segLengthSq;
        t = Math.max(0, Math.min(1, t));

        double closestX = street.getFromX() + t * dx;
        double closestZ = street.getFromZ() + t * dz;

        // Calculate direction from building to street
        double dirX = closestX - placeX;
        double dirZ = closestZ - placeZ;

        // Calculate angle in degrees
        double angle = Math.toDegrees(Math.atan2(dirX, dirZ));

        // Normalize to 0-360
        if (angle < 0) {
            angle += 360;
        }

        // Round to nearest 90 degrees
        int rotation = (int) (Math.round(angle / 90.0) * 90) % 360;

        return rotation;
    }

    /**
     * Step 6: Fill remaining areas with free places or empty buildings.
     * This step adds additional decoration and fills gaps in the district layout.
     *
     * Rules:
     * - Slots not near streets: Always become free places (park, garden)
     * - Slots near streets: Based on buildingTendency, become buildings (house) or free places (plaza, square)
     */
    private void fillRemainingAreas(DistrictGrid districtGrid, Village village, int hexGridSize) {
        log.debug("Filling remaining areas in district '{}'", districtGrid.getName());

        // Check if filling is enabled
        if (!village.isFillEmptySlots()) {
            log.debug("Empty slot filling is disabled for village '{}'", village.getName());
            return;
        }

        // Calculate slot utilization
        District district = districtGrid.getDistrict();
        District.DistrictSlotSize slotSize = district.getSlots();
        if (slotSize == null) {
            log.warn("District '{}' has no slot size defined, skipping filling", district.getName());
            return;
        }

        int availableSlots = slotSize.getSlotCount();
        int usedSlots = districtGrid.getPlacedPlaces().size();
        int emptySlots = availableSlots - usedSlots;

        // Get target fill rate (default 0.75 = 75%)
        double targetFillRate = district.getFillRate() != null ? district.getFillRate() : 0.75;
        double currentOccupancy = (double) usedSlots / availableSlots;

        log.info("District '{}': {}/{} slots used ({}%), target: {}%",
            district.getName(), usedSlots, availableSlots,
            String.format("%.1f", currentOccupancy * 100),
            String.format("%.1f", targetFillRate * 100));

        // Check if already at or above target occupancy
        if (currentOccupancy >= targetFillRate) {
            log.debug("District '{}' already at target occupancy ({}% >= {}%), no filling needed",
                district.getName(),
                String.format("%.1f", currentOccupancy * 100),
                String.format("%.1f", targetFillRate * 100));
            return;
        }

        // Calculate how many additional slots to fill
        int targetSlots = (int) Math.ceil(availableSlots * targetFillRate);
        int slotsToFill = targetSlots - usedSlots;

        if (slotsToFill <= 0) {
            return;
        }

        log.info("District '{}': Will fill {} additional slots (target: {}/{} = {}%)",
            district.getName(), slotsToFill, targetSlots, availableSlots,
            String.format("%.1f", targetFillRate * 100));

        // Get divider for this district
        int divider = getDividerFromSlotSize(slotSize);

        // Calculate which slots are already used
        List<Integer> usedSlotIndices = districtGrid.getPlacedPlaces().stream()
            .map(PlacedPlace::getSlotIndex)
            .toList();

        // Collect empty slots with their distance to nearest street
        List<HexVector2> allSlotPositions = getHexagonalSlotPositions(divider);
        List<EmptySlotCandidate> candidates = new ArrayList<>();

        for (int slotIndex = 0; slotIndex < allSlotPositions.size(); slotIndex++) {
            // Skip if slot is already used
            if (usedSlotIndices.contains(slotIndex)) {
                continue;
            }

            // Get hexagonal position for this slot
            HexVector2 hexPos = allSlotPositions.get(slotIndex);

            // Calculate hex slot pixel size
            int hexSlotSize = hexGridSize / divider;

            // Create HexLocalPosition for distance calculation
            HexLocalPosition hexLocalPos = new HexLocalPosition(hexPos, divider, hexSlotSize);
            Vector2Int relativePos = HexLocalUtil.toHexGridLocalCenter(hexLocalPos);
            int localX = hexGridSize / 2 + relativePos.getX();
            int localZ = hexGridSize / 2 + relativePos.getZ();

            // Check distance to nearest street
            double distanceToStreet = calculateDistanceToNearestStreet(
                localX, localZ, districtGrid.getStreets());

            boolean isNearStreet = distanceToStreet < 50.0; // Within 50 blocks of a street

            // Add to candidates list (don't place yet)
            candidates.add(new EmptySlotCandidate(
                slotIndex, hexPos, localX, localZ, distanceToStreet, isNearStreet
            ));
        }

        log.info("Collected {} empty slot candidates for district '{}'", candidates.size(), district.getName());

        // Sort candidates by distance to street (nearest first)
        candidates.sort(Comparator.comparingDouble(EmptySlotCandidate::getDistanceToStreet));

        // Fill only the required number of slots from sorted candidates
        int filledCount = 0;
        int buildingCount = 0;
        int freeCount = 0;

        for (int i = 0; i < Math.min(slotsToFill, candidates.size()); i++) {
            EmptySlotCandidate candidate = candidates.get(i);

            // Decide what to place based on distance to street
            Place newPlace;
            if (candidate.isNearStreet()) {
                // Near street: Use buildingTendency to decide
                double roll = random.nextDouble();
                if (roll < village.getBuildingTendency()) {
                    // Create building place (house)
                    newPlace = BuildingPlace.builder()
                        .name("filled-house-" + candidate.getSlotIndex())
                        .kind("house")
                        .connectionPoint(false)
                        .build();
                    buildingCount++;
                } else {
                    // Create free place (plaza or square)
                    FreePlace.FreeKind kind = random.nextBoolean() ?
                        FreePlace.FreeKind.PLAZA : FreePlace.FreeKind.SQUARE;
                    newPlace = FreePlace.builder()
                        .name("filled-" + kind.name().toLowerCase() + "-" + candidate.getSlotIndex())
                        .kind(kind)
                        .connectionPoint(false)
                        .build();
                    freeCount++;
                }
            } else {
                // Far from street: Always create free place (park or garden)
                FreePlace.FreeKind kind = random.nextBoolean() ?
                    FreePlace.FreeKind.PARK : FreePlace.FreeKind.GARDEN;
                newPlace = FreePlace.builder()
                    .name("filled-" + kind.name().toLowerCase() + "-" + candidate.getSlotIndex())
                    .kind(kind)
                    .connectionPoint(false)
                    .build();
                freeCount++;
            }

            // Create PlacedPlace with hexagonal and cartesian coordinates
            PlacedPlace placedPlace = PlacedPlace.builder()
                .place(newPlace)
                .hexQ(candidate.getHexPos().getQ())
                .hexR(candidate.getHexPos().getR())
                .localX(candidate.getLocalX())
                .localZ(candidate.getLocalZ())
                .divider(divider)
                .slotIndex(candidate.getSlotIndex())
                .rotation(0)
                .build();

            districtGrid.getPlacedPlaces().add(placedPlace);
            filledCount++;

            log.debug("Filled slot {} with {} '{}' (distance to street: {:.1f}, near: {})",
                candidate.getSlotIndex(),
                newPlace instanceof BuildingPlace ? "building" : "free place",
                newPlace instanceof BuildingPlace ? ((BuildingPlace) newPlace).getKind() :
                    ((FreePlace) newPlace).getKind(),
                candidate.getDistanceToStreet(),
                candidate.isNearStreet());
        }

        log.info("Area filling completed for district '{}': filled {} slots ({} buildings, {} free places)",
            district.getName(), filledCount, buildingCount, freeCount);

        // Step 3b: Assign buildings to newly created BuildingPlaces
        if (buildingCount > 0) {
            log.debug("Assigning buildings to {} newly filled building places", buildingCount);
            assignBuildingsToPlaces(districtGrid, village, hexGridSize);
        }
    }

    /**
     * Calculates the distance from a point to the nearest street.
     * Returns Double.MAX_VALUE if no streets exist.
     */
    private double calculateDistanceToNearestStreet(int x, int z, List<StreetSegment> streets) {
        if (streets == null || streets.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double minDistance = Double.MAX_VALUE;
        for (StreetSegment street : streets) {
            double distance = distanceToSegment(
                x, z,
                street.getFromX(), street.getFromZ(),
                street.getToX(), street.getToZ());
            minDistance = Math.min(minDistance, distance);
        }

        return minDistance;
    }

    /**
     * Calculates the maximum building size for a given divider.
     *
     * @param divider The place divider (1, 3, 5, or 7)
     * @param hexGridSize The size of the hex grid
     * @return Maximum building diameter
     */
    private int calculateMaxBuildingSize(int divider, int hexGridSize) {
        if (divider <= 0) {
            return hexGridSize;
        }
        return hexGridSize / divider;
    }

    /**
     * Gets the divider value from a DistrictSlotSize.
     */
    private int getDividerFromSlotSize(District.DistrictSlotSize slotSize) {
        return switch (slotSize) {
            case BIG -> 1;
            case MEDIUM -> 3;
            case SMALL -> 5;
            case TINY -> 7;
            default -> 3; // Default to MEDIUM
        };
    }

    /**
     * Helper class for collecting empty slot candidates with their distance to nearest street.
     * Used for sorting and filling slots in priority order (nearest to street first).
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class EmptySlotCandidate {
        private final int slotIndex;
        private final HexVector2 hexPos;
        private final int localX;
        private final int localZ;
        private final double distanceToStreet;
        private final boolean isNearStreet;
    }
}
