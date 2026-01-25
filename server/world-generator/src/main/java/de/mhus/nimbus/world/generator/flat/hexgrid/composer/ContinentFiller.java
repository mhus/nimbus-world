package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fills gaps between biomes that belong to the same continent.
 * Creates cohesive landmasses instead of isolated island biomes.
 *
 * Process:
 * 1. For each empty coordinate within the bounding box
 * 2. Check if it has neighbors from the same continent
 * 3. If enough neighbors (minNeighbors), fill with continent's biome type
 * 4. Otherwise leave empty for coast/ocean filler
 *
 * This runs BEFORE CoastFiller and OceanFiller to ensure
 * continents are filled first.
 */
@Slf4j
public class ContinentFiller {

    /**
     * Fills gaps between biomes on the same continent
     *
     * @param composition The composition with continent definitions
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result from BiomeComposer
     * @return Number of continent fill grids added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.info("Starting ContinentFiller");

        // If no continents defined, skip filling
        if (composition.getContinents() == null || composition.getContinents().isEmpty()) {
            log.info("No continents defined, skipping continent filling");
            return 0;
        }

        // Build continent lookup map
        Map<String, Continent> continentMap = new HashMap<>();
        for (Continent continent : composition.getContinents()) {
            continentMap.put(continent.getContinentId(), continent);
            log.info("Continent: {} (type={})",
                continent.getContinentId(), continent.getBiomeType());
        }

        // Track fills per continent
        Map<String, List<HexVector2>> continentFills = new HashMap<>();

        // Process each continent separately
        for (Continent continent : composition.getContinents()) {
            String continentId = continent.getContinentId();

            // Find all coordinates of biomes belonging to this continent
            List<HexVector2> allContinentCoords = new ArrayList<>();
            for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                if (continentId.equals(placed.getBiome().getContinentId())) {
                    BiomeType type = placed.getBiome().getType();
                    // Skip ocean/coast/island biomes
                    if (type != BiomeType.OCEAN && type != BiomeType.COAST && type != BiomeType.ISLAND) {
                        allContinentCoords.addAll(placed.getCoordinates());
                    }
                }
            }

            if (allContinentCoords.isEmpty()) {
                log.info("No land biomes found for continent '{}'", continentId);
                continue;
            }

            log.info("Continent '{}': computing hull for {} biome grids", continentId, allContinentCoords.size());

            // Calculate center of all coordinates
            int centerQ = 0, centerR = 0;
            for (HexVector2 coord : allContinentCoords) {
                centerQ += coord.getQ();
                centerR += coord.getR();
            }
            centerQ /= allContinentCoords.size();
            centerR /= allContinentCoords.size();

            log.info("Continent center: q={}, r={}", centerQ, centerR);

            // Find outermost points in 6 directions (every 60 degrees)
            // Hex directions: NE (30°), E (90°), SE (150°), SW (210°), W (270°), NW (330°)
            Map<Integer, HexVector2> boundaryPoints = new HashMap<>();

            for (int angle = 0; angle < 360; angle += 60) {
                HexVector2 farthest = null;
                double maxDist = 0;

                for (HexVector2 coord : allContinentCoords) {
                    // Calculate distance from center in this direction
                    int dq = coord.getQ() - centerQ;
                    int dr = coord.getR() - centerR;

                    // Convert to angle and distance
                    double coordAngle = Math.toDegrees(Math.atan2(dr, dq));
                    if (coordAngle < 0) coordAngle += 360;

                    // Check if this coordinate is roughly in our angle direction (±30°)
                    double angleDiff = Math.abs(coordAngle - angle);
                    if (angleDiff > 180) angleDiff = 360 - angleDiff;

                    if (angleDiff <= 45) {  // Wider tolerance for hex
                        double dist = Math.sqrt(dq * dq + dr * dr);
                        if (dist > maxDist) {
                            maxDist = dist;
                            farthest = coord;
                        }
                    }
                }

                if (farthest != null) {
                    boundaryPoints.put(angle, farthest);
                }
            }

            log.info("Found {} boundary points for continent '{}'", boundaryPoints.size(), continentId);

            // Calculate bounding box to limit search area
            int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
            int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            for (HexVector2 coord : allContinentCoords) {
                minQ = Math.min(minQ, coord.getQ());
                maxQ = Math.max(maxQ, coord.getQ());
                minR = Math.min(minR, coord.getR());
                maxR = Math.max(maxR, coord.getR());
            }

            // Fill all coordinates within the hull
            List<HexVector2> fills = new ArrayList<>();
            for (int q = minQ; q <= maxQ; q++) {
                for (int r = minR; r <= maxR; r++) {
                    HexVector2 coord = HexVector2.builder().q(q).r(r).build();
                    String key = coordKey(coord);

                    // Skip if already filled
                    if (existingCoords.contains(key)) {
                        continue;
                    }

                    // Check if point is inside the hull using raycasting
                    if (isInsideHull(coord, centerQ, centerR, boundaryPoints)) {
                        fills.add(coord);
                        existingCoords.add(key);
                    }
                }
            }

            if (!fills.isEmpty()) {
                continentFills.put(continentId, fills);
                log.info("Continent '{}': filled {} grids within hull", continentId, fills.size());
            }
        }

        // Create PlacedBiomes for each continent's fills
        int totalFilled = 0;

        for (Map.Entry<String, List<HexVector2>> entry : continentFills.entrySet()) {
            String continentId = entry.getKey();
            List<HexVector2> coords = entry.getValue();
            Continent continent = continentMap.get(continentId);

            if (coords.isEmpty()) {
                continue;
            }

            // Create a filler biome for this continent
            Biome continentBiome = createContinentFillerBiome(continent);

            // Calculate center of filled area
            HexVector2 center = calculateCenter(coords);

            PlacedBiome placedFiller = PlacedBiome.builder()
                .biome(continentBiome)
                .coordinates(coords)
                .center(center)
                .actualSize(coords.size())
                .build();

            placementResult.getPlacedBiomes().add(placedFiller);

            log.info("Filled {} grids for continent '{}'", coords.size(), continentId);
            totalFilled += coords.size();
        }

        log.info("ContinentFiller: added {} grids total", totalFilled);
        return totalFilled;
    }


    /**
     * Creates a filler biome for a continent
     */
    private Biome createContinentFillerBiome(Continent continent) {
        Biome biome = new Biome();
        biome.setName("continent-filler-" + continent.getContinentId());
        biome.setTitle("Continent Fill: " + continent.getName());
        biome.setType(continent.getBiomeType());
        biome.setContinentId(continent.getContinentId());

        // Copy continent parameters
        Map<String, String> parameters = new HashMap<>();
        if (continent.getParameters() != null) {
            parameters.putAll(continent.getParameters());
        }

        // Apply defaults from biome type
        if (continent.getBiomeType() != null) {
            BiomeType biomeType = continent.getBiomeType();

            // Set default builder if not specified
            if (!parameters.containsKey("g_builder")) {
                parameters.put("g_builder", biomeType.getDefaultBuilder());
            }

            // Apply default parameters from biome type
            Map<String, String> defaults = biomeType.getDefaultParameters();
            if (defaults != null) {
                defaults.forEach(parameters::putIfAbsent);
            }
        }

        // Mark as continent filler
        parameters.put("continentFiller", "true");
        parameters.put("continentId", continent.getContinentId());

        biome.setParameters(parameters);
        biome.setStatus(FeatureStatus.COMPOSED);

        return biome;
    }

    /**
     * Calculates center of coordinates
     */
    private HexVector2 calculateCenter(List<HexVector2> coords) {
        if (coords.isEmpty()) {
            return HexVector2.builder().q(0).r(0).build();
        }

        int sumQ = 0;
        int sumR = 0;
        for (HexVector2 coord : coords) {
            sumQ += coord.getQ();
            sumR += coord.getR();
        }

        return HexVector2.builder()
            .q(sumQ / coords.size())
            .r(sumR / coords.size())
            .build();
    }

    /**
     * Checks if a point is inside the hull defined by boundary points.
     * Uses distance-based check: point should be closer to center than all boundary points.
     */
    private boolean isInsideHull(HexVector2 point, int centerQ, int centerR, Map<Integer, HexVector2> boundaryPoints) {
        int dq = point.getQ() - centerQ;
        int dr = point.getR() - centerR;
        double pointDist = Math.sqrt(dq * dq + dr * dr);

        // Calculate angle of point from center
        double pointAngle = Math.toDegrees(Math.atan2(dr, dq));
        if (pointAngle < 0) pointAngle += 360;

        // Find the two nearest boundary points (before and after this angle)
        HexVector2 nearestBoundary = null;
        double minAngleDiff = 360;

        for (Map.Entry<Integer, HexVector2> entry : boundaryPoints.entrySet()) {
            double angleDiff = Math.abs(pointAngle - entry.getKey());
            if (angleDiff > 180) angleDiff = 360 - angleDiff;

            if (angleDiff < minAngleDiff) {
                minAngleDiff = angleDiff;
                nearestBoundary = entry.getValue();
            }
        }

        if (nearestBoundary == null) {
            return false;
        }

        // Point is inside if it's closer to center than the nearest boundary point
        int bdq = nearestBoundary.getQ() - centerQ;
        int bdr = nearestBoundary.getR() - centerR;
        double boundaryDist = Math.sqrt(bdq * bdq + bdr * bdr);

        return pointDist <= boundaryDist + 1; // +1 for tolerance
    }

    /**
     * Gets all 6 neighbors of a hex coordinate
     */
    private List<HexVector2> getNeighbors(HexVector2 coord) {
        int q = coord.getQ();
        int r = coord.getR();

        return List.of(
            HexVector2.builder().q(q + 1).r(r).build(),
            HexVector2.builder().q(q - 1).r(r).build(),
            HexVector2.builder().q(q).r(r + 1).build(),
            HexVector2.builder().q(q).r(r - 1).build(),
            HexVector2.builder().q(q + 1).r(r - 1).build(),
            HexVector2.builder().q(q - 1).r(r + 1).build()
        );
    }

    /**
     * Creates coordinate key for map lookup
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + ":" + coord.getR();
    }
}
