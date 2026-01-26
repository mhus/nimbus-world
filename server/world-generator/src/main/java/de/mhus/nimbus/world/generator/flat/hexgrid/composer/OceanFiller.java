package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.ast.TypeCode;

import java.util.*;

/**
 * Fills gaps between disconnected terrain regions with ocean connections.
 *
 * Simplified Algorithm:
 * 1. FloodFill to group all connected grids (group1, group2, etc.)
 * 2. Find center of each group
 * 3. Connect all group centers with straight lines
 * 4. Only place ocean where no grid exists yet
 * 5. Skip biomes marked as 'decoupled'
 *
 * Also provides fillFlowGaps() to fill ocean where flows cross empty space.
 */
@Slf4j
public class OceanFiller {

    /**
     * Fills ocean connections between disconnected terrain regions.
     *
     * @param composition The composition to fill
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result with all PlacedBiomes
     * @return Number of ocean connection biomes added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.info("Starting OceanFiller - ensuring all regions are connected");

        int biomesAdded = 0;

        // Step 1: Build coordinate set for all non-decoupled biomes
        Set<String> connectedCoords = new HashSet<>();

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            boolean isDecoupled = "true".equals(placed.getBiome().getParameters().get("decoupled"));

            if (!isDecoupled) {
                for (HexVector2 coord : placed.getCoordinates()) {
                    connectedCoords.add(TypeUtil.toStringHexCoord(coord));
                }
            }
        }

        log.info("Found {} connected grids to process", connectedCoords.size());

        if (connectedCoords.isEmpty()) {
            log.info("No connected grids to process");
            return 0;
        }

        // Step 2: FloodFill to find all connected regions
        List<Set<String>> groups = groupConnectedRegions(connectedCoords);

        log.info("Found {} connected groups", groups.size());

        if (groups.size() <= 1) {
            log.info("All terrain is already connected");
            return 0;
        }

        // Step 3: Find center of each group
        List<HexVector2> groupCenters = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Set<String> group = groups.get(i);
            HexVector2 center = findGroupCenter(group);
            groupCenters.add(center);
            log.info("Group {} has {} grids, center: {}", i + 1, group.size(), TypeUtil.toStringHexCoord(center));
        }

        // Step 4: Connect all groups with each other (fully meshed topology)
        // Connect every group with every other group
        int connectionId = 0;

        for (int i = 0; i < groupCenters.size(); i++) {
            for (int j = i + 1; j < groupCenters.size(); j++) {
                HexVector2 centerA = groupCenters.get(i);
                HexVector2 centerB = groupCenters.get(j);

                log.info("Creating ocean line from group {} ({}) to group {} ({})",
                    i + 1, TypeUtil.toStringHexCoord(centerA), j + 1, TypeUtil.toStringHexCoord(centerB));

                // Create straight line between centers
                List<HexVector2> linePath = createStraightLine(centerA, centerB, existingCoords);

                if (!linePath.isEmpty()) {
                    // Create ocean biome for connection
                    Biome oceanBiome = new Biome();
                    oceanBiome.setName("ocean-connection-" + (i + 1) + "-to-" + (j + 1));
                    oceanBiome.setType(BiomeType.OCEAN);

                    // Mark as filler
                    if (oceanBiome.getParameters() == null) {
                        oceanBiome.setParameters(new HashMap<>());
                    }
                    oceanBiome.getParameters().put("filler", "true");
                    oceanBiome.getParameters().put("fillerType", "ocean");
                    oceanBiome.getParameters().put("connectionId", String.valueOf(connectionId));
                    oceanBiome.getParameters().put("fromGroup", String.valueOf(i + 1));
                    oceanBiome.getParameters().put("toGroup", String.valueOf(j + 1));

                    // Apply defaults
                    oceanBiome.applyDefaults();

                    // Configure with line coordinates
                    oceanBiome.configureHexGrids(linePath);

                    // Create PlacedBiome
                    PlacedBiome placedOcean = new PlacedBiome();
                    placedOcean.setBiome(oceanBiome);
                    placedOcean.setCenter(linePath.get(0));
                    placedOcean.setCoordinates(new ArrayList<>(linePath));
                    placedOcean.setActualSize(linePath.size());

                    placementResult.getPlacedBiomes().add(placedOcean);

                    // Add to existingCoords
                    for (HexVector2 coord : linePath) {
                        existingCoords.add(TypeUtil.toStringHexCoord(coord));
                    }

                    biomesAdded++;
                    connectionId++;

                    log.info("Created ocean connection with {} grids", linePath.size());
                } else {
                    log.info("No ocean connection needed (regions already connected)");
                }
            }
        }

        log.info("OceanFiller added {} ocean connection biomes", biomesAdded);

        return biomesAdded;
    }

    /**
     * Groups all coordinates into connected regions using FloodFill.
     *
     * @param allCoords All coordinate keys to group
     * @return List of groups (sets of connected coordinates)
     */
    private List<Set<String>> groupConnectedRegions(Set<String> allCoords) {
        List<Set<String>> groups = new ArrayList<>();
        Set<String> unmarked = new HashSet<>(allCoords);

        int groupNumber = 1;

        while (!unmarked.isEmpty()) {
            // Take first unmarked coordinate
            String start = unmarked.iterator().next();

            // FloodFill to mark all connected coordinates
            Set<String> group = new HashSet<>();
            Queue<String> queue = new LinkedList<>();

            queue.add(start);
            unmarked.remove(start);
            group.add(start);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                HexVector2 coord = TypeUtil.parseHexCoord(current);

                // Check all 6 neighbors
                for (HexVector2 neighbor : getNeighbors(coord)) {
                    String neighborKey = TypeUtil.toStringHexCoord(neighbor);

                    if (unmarked.contains(neighborKey)) {
                        queue.add(neighborKey);
                        unmarked.remove(neighborKey);
                        group.add(neighborKey);
                    }
                }
            }

            groups.add(group);
            log.debug("Marked group {} with {} grids", groupNumber, group.size());
            groupNumber++;
        }

        // Sort groups by size (largest first)
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        return groups;
    }

    /**
     * Finds the center coordinate of a group.
     *
     * @param group Set of coordinate keys
     * @return Center coordinate (average position)
     */
    private HexVector2 findGroupCenter(Set<String> group) {
        int sumQ = 0;
        int sumR = 0;

        for (String coordKey : group) {
            HexVector2 coord = TypeUtil.parseHexCoord(coordKey);
            sumQ += coord.getQ();
            sumR += coord.getR();
        }

        int avgQ = sumQ / group.size();
        int avgR = sumR / group.size();

        return HexVector2.builder()
            .q(avgQ)
            .r(avgR)
            .build();
    }

    /**
     * Creates a straight line between two hex coordinates.
     * Only includes coordinates that don't exist yet.
     *
     * Uses linear interpolation in cubic hex coordinates.
     *
     * @param start Start coordinate
     * @param end End coordinate
     * @param existingCoords Coordinates to skip (already exist)
     * @return List of new coordinates forming the line
     */
    private List<HexVector2> createStraightLine(HexVector2 start, HexVector2 end,
                                                 Set<String> existingCoords) {
        List<HexVector2> line = new ArrayList<>();

        int distance = hexDistance(start, end);

        if (distance == 0) {
            return line; // Same coordinate
        }

        // Convert axial to cube coordinates for interpolation
        int startX = start.getQ();
        int startZ = start.getR();
        int startY = -startX - startZ;

        int endX = end.getQ();
        int endZ = end.getR();
        int endY = -endX - endZ;

        // Linear interpolation
        for (int i = 0; i <= distance; i++) {
            double t = (double) i / distance;

            double x = startX + (endX - startX) * t;
            double y = startY + (endY - startY) * t;
            double z = startZ + (endZ - startZ) * t;

            // Round to nearest hex coordinate
            HexVector2 coord = cubeRound(x, y, z);
            String key = TypeUtil.toStringHexCoord(coord);

            // Only add if not already existing
            if (!existingCoords.contains(key)) {
                line.add(coord);
            }
        }

        return line;
    }

    /**
     * Rounds cube coordinates to nearest hex coordinate.
     */
    private HexVector2 cubeRound(double x, double y, double z) {
        int rx = (int) Math.round(x);
        int ry = (int) Math.round(y);
        int rz = (int) Math.round(z);

        double xDiff = Math.abs(rx - x);
        double yDiff = Math.abs(ry - y);
        double zDiff = Math.abs(rz - z);

        if (xDiff > yDiff && xDiff > zDiff) {
            rx = -ry - rz;
        } else if (yDiff > zDiff) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }

        // Convert back to axial (q, r)
        return HexVector2.builder()
            .q(rx)
            .r(rz)
            .build();
    }

    /**
     * Calculates hex distance between two coordinates.
     */
    private int hexDistance(HexVector2 a, HexVector2 b) {
        int dq = Math.abs(a.getQ() - b.getQ());
        int dr = Math.abs(a.getR() - b.getR());
        int ds = Math.abs((a.getQ() + a.getR()) - (b.getQ() + b.getR()));
        return (dq + dr + ds) / 2;
    }

    /**
     * Gets all 6 neighbors of a hex coordinate.
     */
    private List<HexVector2> getNeighbors(HexVector2 coord) {
        List<HexVector2> neighbors = new ArrayList<>();

        // Hex directions (axial coordinates)
        int[][] directions = {
            {0, -1},  // N
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0}   // W
        };

        for (int[] dir : directions) {
            neighbors.add(HexVector2.builder()
                .q(coord.getQ() + dir[0])
                .r(coord.getR() + dir[1])
                .build());
        }

        return neighbors;
    }

    /**
     * Fills ocean grids where flows cross empty space.
     * This is called AFTER FlowComposer, so we know which grids flows pass through.
     * For any flow grid that doesn't exist yet, we create an ocean filler grid.
     *
     * @param composition The composition with all flows
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result to add new ocean PlacedBiomes
     * @return Number of ocean grids added
     */
    public int fillFlowGaps(HexComposition composition,
                            Set<String> existingCoords,
                            BiomePlacementResult placementResult) {
        log.info("Starting OceanFiller.fillFlowGaps to fill grids crossed by flows");

        // Collect all coordinates that flows pass through
        Set<String> flowCoords = new HashSet<>();

        // Collect from direct flows
        if (composition.getRoads() != null) {
            for (Road road : composition.getRoads()) {
                collectFlowCoordinates(road, flowCoords);
            }
        }
        if (composition.getRivers() != null) {
            for (River river : composition.getRivers()) {
                collectFlowCoordinates(river, flowCoords);
            }
        }
        if (composition.getWalls() != null) {
            for (Wall wall : composition.getWalls()) {
                collectFlowCoordinates(wall, flowCoords);
            }
        }

        // TODO: Also collect from composites if needed

        log.info("Found {} grids crossed by flows", flowCoords.size());

        // Find flow coordinates that are NOT yet filled
        List<HexVector2> unfilledCoords = new ArrayList<>();
        for (String coordKey : flowCoords) {
            if (!existingCoords.contains(coordKey)) {
                HexVector2 coord = TypeUtil.parseHexCoord(coordKey);
                unfilledCoords.add(coord);
                existingCoords.add(coordKey); // Mark as occupied
            }
        }

        log.info("Found {} unfilled grids that need ocean filler", unfilledCoords.size());

        if (unfilledCoords.isEmpty()) {
            log.info("All flow grids are already filled");
            return 0;
        }

        // Create ocean biome for unfilled flow grids
        Biome oceanBiome = new Biome();
        oceanBiome.setName("ocean-flow-gaps");
        oceanBiome.setType(BiomeType.OCEAN);

        // Mark as filler
        if (oceanBiome.getParameters() == null) {
            oceanBiome.setParameters(new HashMap<>());
        }
        oceanBiome.getParameters().put("filler", "true");
        oceanBiome.getParameters().put("fillerType", "ocean");
        oceanBiome.getParameters().put("flowGap", "true");

        // Apply defaults
        oceanBiome.applyDefaults();

        // Configure with coordinates
        oceanBiome.configureHexGrids(unfilledCoords);

        // Create PlacedBiome
        PlacedBiome placedOcean = new PlacedBiome();
        placedOcean.setBiome(oceanBiome);
        placedOcean.setCenter(unfilledCoords.get(0)); // First coord as center
        placedOcean.setCoordinates(new ArrayList<>(unfilledCoords));
        placedOcean.setActualSize(unfilledCoords.size());

        placementResult.getPlacedBiomes().add(placedOcean);

        log.info("OceanFiller.fillFlowGaps added 1 ocean biome with {} grids", unfilledCoords.size());

        return 1;
    }

    /**
     * Collects all coordinates from a flow's FeatureHexGrids.
     */
    private void collectFlowCoordinates(Flow flow, Set<String> flowCoords) {
        if (flow.getHexGrids() == null) {
            return;
        }

        for (FeatureHexGrid hexGrid : flow.getHexGrids()) {
            String coordKey = hexGrid.getPositionKey();
            if (coordKey != null) {
                flowCoords.add(coordKey);
            }
        }
    }
}

