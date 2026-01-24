package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Composes biomes on a hex grid by placing them step by step
 * Uses PreparedHexComposition and creates actual WHexGrid instances
 */
@Slf4j
public class BiomeComposer {

    private static final int[] DIRECTION_ANGLES = {0, 60, 120, 180, 240, 300}; // N, NE, E, SE, S, SW

    /**
     * Composes biomes from a prepared composition
     *
     * @param prepared The prepared composition with concrete ranges
     * @param worldId The world ID for generated HexGrids
     * @param seed Random seed for reproducible generation
     * @return Result with placed biomes and generated HexGrids
     */
    public BiomePlacementResult compose(PreparedHexComposition prepared, String worldId, long seed) {
        log.info("Starting biome composition with seed: {}", seed);

        CompositionContext context = new CompositionContext(seed);

        int totalRetries = 0;
        boolean success = false;

        // Try multiple times to place all biomes
        while (totalRetries < context.getMaxTotalRetries() && !success) {
            context.reset();

            try {
                // Place each biome
                for (PreparedBiome biome : prepared.getBiomes()) {
                    boolean placed = placeBiome(biome, context);

                    if (!placed) {
                        log.warn("Failed to place biome: {} after {} attempts, retrying composition",
                            biome.getName(), context.getMaxRetriesPerBiome());
                        throw new BiomePlacementException("Could not place biome: " + biome.getName());
                    }

                    // Add biome name as anchor for other biomes to reference
                    if (biome.getName() != null && !context.getPlacedBiomes().isEmpty()) {
                        PlacedBiome lastPlaced = context.getPlacedBiomes().get(context.getPlacedBiomes().size() - 1);
                        context.addAnchor(biome.getName(), lastPlaced.getCenter());
                    }
                }

                success = true;
                log.info("Successfully placed all {} biomes", prepared.getBiomes().size());

            } catch (BiomePlacementException e) {
                totalRetries++;
                log.debug("Composition attempt {} failed: {}", totalRetries, e.getMessage());
            }
        }

        if (!success) {
            log.error("Failed to compose biomes after {} total retries", totalRetries);
            return BiomePlacementResult.builder()
                .composition(prepared)
                .success(false)
                .retries(totalRetries)
                .errorMessage("Failed to place all biomes after " + totalRetries + " retries")
                .build();
        }

        // Generate WHexGrids for all placed biomes
        List<WHexGrid> hexGrids = generateHexGrids(context.getPlacedBiomes(), worldId);

        // NEW: Store FeatureHexGrid configurations in PreparedBiomes
        storeHexGridConfigurations(context.getPlacedBiomes(), prepared.getBiomes());

        // NEW: Copy HexGrid configurations to original Features
        copyHexGridConfigsToOriginalFeatures(context.getPlacedBiomes());

        return BiomePlacementResult.builder()
            .composition(prepared)
            .placedBiomes(context.getPlacedBiomes())
            .hexGrids(hexGrids)
            .retries(totalRetries)
            .success(true)
            .build();
    }

    /**
     * Attempts to place a single biome
     *
     * @param biome The biome to place
     * @param context The composition context
     * @return true if successfully placed
     */
    private boolean placeBiome(PreparedBiome biome, CompositionContext context) {
        log.debug("Attempting to place biome: {} (type: {}, shape: {})",
            biome.getName(), biome.getType(), biome.getShape());

        // Sort positions by priority
        List<PreparedPosition> sortedPositions = new ArrayList<>(biome.getPositions());
        sortedPositions.sort(Comparator.comparingInt(PreparedPosition::getPriority).reversed());

        for (PreparedPosition position : sortedPositions) {
            int attempts = 0;

            while (attempts < context.getMaxRetriesPerBiome()) {
                attempts++;

                // Get anchor point
                HexVector2 anchor = context.getAnchor(position.getAnchor());
                if (anchor == null) {
                    log.warn("Anchor not found: {}, using origin", position.getAnchor());
                    anchor = context.getAnchor("origin");
                }

                // Calculate target position with randomization
                HexVector2 targetCenter = calculateTargetPosition(position, anchor, context.getRandom());

                // Generate coordinates for this biome
                int size = randomInRange(biome.getSizeFrom(), biome.getSizeTo(), context.getRandom());
                List<HexVector2> coordinates = generateBiomeCoordinates(
                    targetCenter, size, biome.getShape(), context);

                // Check if all coordinates are available
                if (areCoordinatesAvailable(coordinates, context)) {
                    // Place the biome
                    for (HexVector2 coord : coordinates) {
                        context.occupy(coord);
                    }

                    PlacedBiome placed = PlacedBiome.builder()
                        .biome(biome)
                        .coordinates(coordinates)
                        .center(targetCenter)
                        .actualSize(size)
                        .build();

                    context.getPlacedBiomes().add(placed);

                    log.info("Placed biome '{}' at {} with {} hexes (attempt {})",
                        biome.getName(), targetCenter, coordinates.size(), attempts);

                    return true;
                }

                // If coordinates not available, try again with different random values
            }

            log.debug("Failed to place biome '{}' with position priority {}, trying next position",
                biome.getName(), position.getPriority());
        }

        return false;
    }

    /**
     * Calculates target position with direction and distance randomization
     */
    private HexVector2 calculateTargetPosition(PreparedPosition position, HexVector2 anchor, Random random) {
        // Randomize direction slightly (±30 degrees)
        int baseAngle = position.getDirectionAngle();
        int angleVariation = random.nextInt(61) - 30; // -30 to +30
        int actualAngle = (baseAngle + angleVariation + 360) % 360;

        // Randomize distance within range
        int distance = randomInRange(position.getDistanceFrom(), position.getDistanceTo(), random);

        // Convert angle and distance to hex coordinates
        HexVector2 offset = calculateHexOffset(actualAngle, distance);

        return HexVector2.builder()
            .q(anchor.getQ() + offset.getQ())
            .r(anchor.getR() + offset.getR())
            .build();
    }

    /**
     * Calculates hex coordinate offset for given angle and distance
     */
    private HexVector2 calculateHexOffset(int angle, int distance) {
        // Hex directions (axial coordinates)
        // 0° (N): (0, -1), 60° (NE): (1, -1), 120° (E): (1, 0)
        // 180° (S): (0, 1), 240° (SW): (-1, 1), 300° (W): (-1, 0)

        // Find closest hex direction
        int closestAngle = 0;
        int minDiff = 360;
        for (int hexAngle : DIRECTION_ANGLES) {
            int diff = Math.abs(angle - hexAngle);
            if (diff > 180) diff = 360 - diff;
            if (diff < minDiff) {
                minDiff = diff;
                closestAngle = hexAngle;
            }
        }

        // Get direction unit vector
        int dq = 0, dr = 0;
        switch (closestAngle) {
            case 0:   dq = 0;  dr = -1; break; // N
            case 60:  dq = 1;  dr = -1; break; // NE
            case 120: dq = 1;  dr = 0;  break; // E
            case 180: dq = 0;  dr = 1;  break; // S
            case 240: dq = -1; dr = 1;  break; // SW
            case 300: dq = -1; dr = 0;  break; // W
        }

        return HexVector2.builder()
            .q(dq * distance)
            .r(dr * distance)
            .build();
    }

    /**
     * Generates coordinates for a biome based on shape and size
     */
    private List<HexVector2> generateBiomeCoordinates(HexVector2 center, int size,
                                                      AreaShape shape, CompositionContext context) {
        List<HexVector2> coordinates = new ArrayList<>();

        if (shape == null) {
            coordinates.add(center); // Single hex
            return coordinates;
        }

        switch (shape) {
            case CIRCLE:
                coordinates = generateCircularCoordinates(center, size);
                break;
            case LINE:
                coordinates = generateLineCoordinates(center, size, context.getRandom());
                break;
            case RECTANGLE:
                // For now, treat RECTANGLE like CIRCLE (can be improved later)
                coordinates = generateCircularCoordinates(center, size);
                break;
            default:
                coordinates.add(center); // Single hex
        }

        return coordinates;
    }

    /**
     * Generates circular cluster of hexes
     */
    private List<HexVector2> generateCircularCoordinates(HexVector2 center, int size) {
        List<HexVector2> coords = new ArrayList<>();
        coords.add(center);

        if (size <= 1) return coords;

        // Add rings around center
        int rings = (int) Math.ceil(Math.sqrt(size));
        for (int ring = 1; ring <= rings && coords.size() < size; ring++) {
            List<HexVector2> ringCoords = getHexRing(center, ring);
            for (HexVector2 coord : ringCoords) {
                if (coords.size() >= size) break;
                coords.add(coord);
            }
        }

        return coords;
    }

    /**
     * Generates line of hexes
     */
    private List<HexVector2> generateLineCoordinates(HexVector2 center, int size, Random random) {
        List<HexVector2> coords = new ArrayList<>();
        coords.add(center);

        if (size <= 1) return coords;

        // Random direction for line
        int direction = random.nextInt(6); // 0-5 for 6 hex directions
        int dq = 0, dr = 0;

        switch (direction) {
            case 0: dq = 0;  dr = -1; break; // N
            case 1: dq = 1;  dr = -1; break; // NE
            case 2: dq = 1;  dr = 0;  break; // E
            case 3: dq = 0;  dr = 1;  break; // S
            case 4: dq = -1; dr = 1;  break; // SW
            case 5: dq = -1; dr = 0;  break; // W
        }

        // Add hexes in line
        for (int i = 1; i < size; i++) {
            coords.add(HexVector2.builder()
                .q(center.getQ() + dq * i)
                .r(center.getR() + dr * i)
                .build());
        }

        return coords;
    }

    /**
     * Generates scattered hexes around center
     */
    private List<HexVector2> generateScatteredCoordinates(HexVector2 center, int size, Random random) {
        List<HexVector2> coords = new ArrayList<>();
        coords.add(center);

        if (size <= 1) return coords;

        // Add scattered hexes within radius
        int radius = Math.max(2, size / 2);
        Set<String> used = new HashSet<>();
        used.add(center.getQ() + "," + center.getR());

        int attempts = 0;
        while (coords.size() < size && attempts < size * 10) {
            attempts++;

            int ring = random.nextInt(radius) + 1;
            List<HexVector2> candidates = getHexRing(center, ring);

            if (!candidates.isEmpty()) {
                HexVector2 candidate = candidates.get(random.nextInt(candidates.size()));
                String key = candidate.getQ() + "," + candidate.getR();

                if (!used.contains(key)) {
                    coords.add(candidate);
                    used.add(key);
                }
            }
        }

        return coords;
    }

    /**
     * Gets all hexes in a ring around center
     */
    private List<HexVector2> getHexRing(HexVector2 center, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        if (radius == 0) {
            ring.add(center);
            return ring;
        }

        // Hex ring algorithm
        int q = center.getQ() - radius;
        int r = center.getR() + radius;

        int[][] directions = {{1, -1}, {1, 0}, {0, 1}, {-1, 1}, {-1, 0}, {0, -1}};

        for (int[] dir : directions) {
            for (int step = 0; step < radius; step++) {
                ring.add(HexVector2.builder().q(q).r(r).build());
                q += dir[0];
                r += dir[1];
            }
        }

        return ring;
    }

    /**
     * Checks if all coordinates are available
     */
    private boolean areCoordinatesAvailable(List<HexVector2> coordinates, CompositionContext context) {
        for (HexVector2 coord : coordinates) {
            if (context.isOccupied(coord)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates WHexGrid instances for placed biomes
     */
    private List<WHexGrid> generateHexGrids(List<PlacedBiome> placedBiomes, String worldId) {
        List<WHexGrid> hexGrids = new ArrayList<>();

        for (PlacedBiome placed : placedBiomes) {
            for (HexVector2 coord : placed.getCoordinates()) {
                WHexGrid hexGrid = createHexGrid(coord, placed, worldId);
                hexGrids.add(hexGrid);
            }
        }

        log.info("Generated {} HexGrids from {} biomes", hexGrids.size(), placedBiomes.size());
        return hexGrids;
    }

    /**
     * Creates a single WHexGrid for a coordinate
     */
    private WHexGrid createHexGrid(HexVector2 coord, PlacedBiome placed, String worldId) {
        PreparedBiome biome = placed.getBiome();

        // Create public HexGrid data
        HexGrid publicData = new HexGrid();
        publicData.setPosition(coord);
        publicData.setName(biome.getName() + " [" + coord.getQ() + "," + coord.getR() + "]");
        publicData.setDescription("Part of " + biome.getType() + " biome");

        // Copy parameters from biome
        Map<String, String> parameters = new HashMap<>();
        if (biome.getParameters() != null) {
            parameters.putAll(biome.getParameters());
        }

        // Add biome type as parameter (use builderName for consistency with HexGridBuilderService)
        parameters.put("biome", biome.getType().getBuilderName());
        parameters.put("biomeName", biome.getName());

        return WHexGrid.builder()
            .worldId(worldId)
            .position(coord.getQ() + ":" + coord.getR())
            .publicData(publicData)
            .parameters(parameters)
            .enabled(true)
            .build();
    }

    /**
     * Returns random int in range [from, to] inclusive
     */
    private int randomInRange(int from, int to, Random random) {
        if (from >= to) return from;
        return from + random.nextInt(to - from + 1);
    }

    /**
     * Stores HexGrid configurations in PreparedBiomes
     */
    /**
     * Stores HexGrid configurations by calling configureHexGrids() on each biome.
     * This allows each biome to configure its own grids polymorphically.
     */
    private void storeHexGridConfigurations(List<PlacedBiome> placedBiomes,
                                            List<PreparedBiome> preparedBiomes) {
        for (PlacedBiome placed : placedBiomes) {
            PreparedBiome preparedBiome = placed.getBiome();

            // Let the biome configure its own HexGrids
            preparedBiome.configureHexGrids(placed.getCoordinates());
        }
    }

    /**
     * Copies HexGrid configurations back to original Features
     */
    private void copyHexGridConfigsToOriginalFeatures(List<PlacedBiome> placedBiomes) {
        for (PlacedBiome placed : placedBiomes) {
            placed.getBiome().copyHexGridsToOriginal();
        }
    }

    /**
     * Exception for biome placement failures
     */
    private static class BiomePlacementException extends RuntimeException {
        public BiomePlacementException(String message) {
            super(message);
        }
    }
}
