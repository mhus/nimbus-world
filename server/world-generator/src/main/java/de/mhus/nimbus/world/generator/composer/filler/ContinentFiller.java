package de.mhus.nimbus.world.generator.composer.filler;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.feature.FeatureStatus;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.BiomeType;
import de.mhus.nimbus.world.generator.composer.biome.Continent;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Fills gaps between biomes that belong to the same continent.
 * Creates cohesive landmasses instead of isolated island biomes.
 *
 * Uses a convex hull algorithm to determine the continent boundary:
 * 1. Collect all land biome coordinates for the continent
 * 2. Convert to float coordinates respecting hex stagger
 * 3. Compute the convex hull (Andrew's monotone chain)
 * 4. Fill empty cells inside the hull with the continent's biome type
 *
 * This runs BEFORE CoastFiller and OceanFiller to ensure
 * continents are filled first.
 */
@Slf4j
public class ContinentFiller {

    /**
     * Fills gaps between biomes on the same continent.
     *
     * @param composition The composition with continent definitions
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result from BiomeComposer
     * @return Number of continent fill grids added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.debug("Starting ContinentFiller");

        if (composition.getContinents() == null || composition.getContinents().isEmpty()) {
            log.debug("No continents defined, skipping continent filling");
            return 0;
        }

        Map<String, Continent> continentMap = new HashMap<>();
        for (Continent continent : composition.getContinents()) {
            continentMap.put(continent.getContinentId(), continent);
            log.debug("Continent: {} (type={})",
                continent.getContinentId(), continent.getBiomeType());
        }

        Map<String, List<HexVector2>> continentFills = new HashMap<>();

        for (Continent continent : composition.getContinents()) {
            String continentId = continent.getContinentId();

            List<HexVector2> allContinentCoords = new ArrayList<>();
            for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
                if (continentId.equals(placed.getBiome().getContinentId())) {
                    BiomeType type = placed.getBiome().getType();
                    if (type != BiomeType.OCEAN && type != BiomeType.COAST && type != BiomeType.ISLAND) {
                        allContinentCoords.addAll(placed.getCoordinates());
                    }
                }
            }

            if (allContinentCoords.isEmpty()) {
                log.debug("No land biomes found for continent '{}'", continentId);
                continue;
            }

            log.debug("Continent '{}': computing convex hull for {} biome grids", continentId, allContinentCoords.size());

            // Compute convex hull of all biome coordinates
            List<double[]> hull = computeConvexHull(allContinentCoords);

            if (hull.size() < 3) {
                log.debug("Continent '{}': hull has only {} vertices, skipping fill", continentId, hull.size());
                continue;
            }

            log.debug("Continent '{}': convex hull has {} vertices", continentId, hull.size());

            // Bounding box for iteration
            int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
            int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            for (HexVector2 coord : allContinentCoords) {
                minQ = Math.min(minQ, coord.getQ());
                maxQ = Math.max(maxQ, coord.getQ());
                minR = Math.min(minR, coord.getR());
                maxR = Math.max(maxR, coord.getR());
            }

            // Fill empty cells inside the convex hull
            List<HexVector2> fills = new ArrayList<>();
            for (int q = minQ; q <= maxQ; q++) {
                for (int r = minR; r <= maxR; r++) {
                    HexVector2 coord = HexVector2.builder().q(q).r(r).build();
                    String key = TypeUtil.toStringHexCoord(coord);

                    if (existingCoords.contains(key)) {
                        continue;
                    }

                    double[] point = hexToFloat(coord);
                    if (isInsideConvexHull(point, hull)) {
                        fills.add(coord);
                        existingCoords.add(key);
                    }
                }
            }

            if (!fills.isEmpty()) {
                continentFills.put(continentId, fills);
                log.debug("Continent '{}': filled {} grids within convex hull", continentId, fills.size());
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

            Biome continentBiome = createContinentFillerBiome(continent);
            continentBiome.configureHexGrids(coords);

            HexVector2 center = calculateCenter(coords);

            PlacedBiome placedFiller = PlacedBiome.builder()
                .biome(continentBiome)
                .coordinates(coords)
                .center(center)
                .actualSize(coords.size())
                .build();

            placementResult.getPlacedBiomes().add(placedFiller);

            log.debug("Filled {} grids for continent '{}' (FeatureHexGrids in central registry)",
                    coords.size(), continentId);
            totalFilled += coords.size();
        }

        log.debug("ContinentFiller: added {} grids total", totalFilled);
        return totalFilled;
    }

    /**
     * Converts offset hex coordinates (odd-r stagger) to float coordinates
     * that respect the actual hex geometry for geometric calculations.
     *
     * x accounts for the half-cell stagger on odd rows.
     * z uses the 3/4 row height ratio of pointy-top hexagons.
     */
    private double[] hexToFloat(HexVector2 hex) {
        double x = hex.getQ() + (hex.getR() % 2 != 0 ? 0.5 : 0);
        double z = hex.getR() * 0.75;
        return new double[]{x, z};
    }

    /**
     * Computes the convex hull of hex coordinates using Andrew's monotone chain algorithm.
     * Returns hull vertices in counter-clockwise order.
     */
    private List<double[]> computeConvexHull(List<HexVector2> coords) {
        List<double[]> points = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (HexVector2 coord : coords) {
            String key = coord.getQ() + ":" + coord.getR();
            if (seen.add(key)) {
                points.add(hexToFloat(coord));
            }
        }

        points.sort((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Double.compare(a[1], b[1]);
        });

        int n = points.size();
        if (n < 3) return new ArrayList<>(points);

        // Lower hull
        List<double[]> lower = new ArrayList<>();
        for (double[] p : points) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
                lower.removeLast();
            }
            lower.add(p);
        }

        // Upper hull
        List<double[]> upper = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            double[] p = points.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
                upper.removeLast();
            }
            upper.add(p);
        }

        // Concatenate, removing duplicate endpoints
        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return lower;
    }

    /**
     * Cross product of vectors OA and OB.
     * Positive = counter-clockwise turn, negative = clockwise, zero = collinear.
     */
    private double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /**
     * Tests if a point is inside a convex polygon (hull vertices in CCW order).
     * A point is inside if it is on the left side (or on) every edge.
     */
    private boolean isInsideConvexHull(double[] point, List<double[]> hull) {
        int n = hull.size();
        if (n < 3) return false;

        for (int i = 0; i < n; i++) {
            double[] a = hull.get(i);
            double[] b = hull.get((i + 1) % n);
            double cp = (b[0] - a[0]) * (point[1] - a[1]) - (b[1] - a[1]) * (point[0] - a[0]);
            if (cp < -0.01) {
                return false;
            }
        }
        return true;
    }

    private Biome createContinentFillerBiome(Continent continent) {
        Biome biome = new Biome();
        biome.setName("continent-filler-" + continent.getContinentId());
        biome.setTitle("Continent Fill: " + continent.getName());
        biome.setType(continent.getBiomeType());
        biome.setContinentId(continent.getContinentId());

        Map<String, String> parameters = new HashMap<>();
        if (continent.getParameters() != null) {
            parameters.putAll(continent.getParameters());
        }

        if (continent.getBiomeType() != null) {
            BiomeType biomeType = continent.getBiomeType();

            if (!parameters.containsKey("g_builder")) {
                parameters.put("g_builder", biomeType.getDefaultBuilder());
            }

            Map<String, String> defaults = biomeType.getDefaultParameters();
            if (defaults != null) {
                defaults.forEach(parameters::putIfAbsent);
            }
        }

        parameters.put("continentFiller", "true");
        parameters.put("continentId", continent.getContinentId());

        biome.setParameters(parameters);
        biome.setStatus(FeatureStatus.COMPOSED);

        return biome;
    }

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
}
