package de.mhus.nimbus.world.generator.composer.biome;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.area.AreaShape;
import de.mhus.nimbus.world.generator.composer.build.CompositionContext;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.structure.PreparedPosition;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Composes biomes on a hex grid by placing them step by step
 * Uses HexComposition and creates actual WHexGrid instances
 */
@Slf4j
public class BiomeComposer {

    private static final int[] DIRECTION_ANGLES = {0, 60, 120, 180, 240, 300}; // NE, E, SE, SW, W, NW (pointy-top hex)

    /**
     * Composes biomes with default placement tolerance (start=1, increment=1, max=3).
     */
    public BiomePlacementResult compose(HexComposition prepared, String worldId, long seed) {
        return compose(prepared, worldId, seed, 1, 1, 3);
    }

    /**
     * Composes biomes from a prepared composition
     *
     * @param prepared The prepared composition with concrete ranges
     * @param worldId The world ID for generated HexGrids
     * @param seed Random seed for reproducible generation
     * @param placementToleranceStart Starting jitter tolerance in hex distance
     * @param placementToleranceIncrement Tolerance increase per retry
     * @param maxPlacementTolerance Maximum jitter tolerance
     * @return Result with placed biomes and generated HexGrids
     */
    public BiomePlacementResult compose(HexComposition prepared, String worldId, long seed,
                                         int placementToleranceStart, int placementToleranceIncrement,
                                         int maxPlacementTolerance) {
        log.debug("Starting biome composition with seed: {}, tolerance: start={}, increment={}, max={}",
            seed, placementToleranceStart, placementToleranceIncrement, maxPlacementTolerance);

        CompositionContext context = new CompositionContext(seed);

        int totalRetries = 0;
        boolean success = false;

        // Try multiple times to place all biomes
        while (totalRetries < context.getMaxTotalRetries() && !success) {
            context.reset();

            // Calculate placement tolerance for this attempt
            int tolerance = Math.min(
                placementToleranceStart + placementToleranceIncrement * totalRetries,
                maxPlacementTolerance);
            context.setPlacementTolerance(tolerance);
            log.debug("Composition attempt {}: placementTolerance={}", totalRetries + 1, tolerance);

            try {
                // Separate normal and enclosed biomes
                List<Biome> normalBiomes = new ArrayList<>();
                List<Biome> enclosedBiomes = new ArrayList<>();

                for (Biome biome : prepared.getBiomes()) {
                    if (biome.getEnclosedBy() != null && !biome.getEnclosedBy().isEmpty()) {
                        enclosedBiomes.add(biome);
                    } else {
                        normalBiomes.add(biome);
                    }
                }

                log.debug("Placing {} normal biomes and {} enclosed biomes",
                    normalBiomes.size(), enclosedBiomes.size());

                // Phase 1: Place normal biomes
                for (Biome biome : normalBiomes) {
                    boolean placed = placeBiome(biome, context);

                    if (!placed) {
                        log.warn("Failed to place biome: {} after {} attempts, retrying composition",
                            biome.getName(), context.getMaxRetriesPerBiome());
                        throw new BiomePlacementException("Could not place biome: " + biome.getName());
                    }

                    // Add biome name as anchor for other biomes to reference
                    if (biome.getName() != null && !context.getPlacedBiomes().isEmpty()) {
                        PlacedBiome lastPlaced = context.getPlacedBiomes().getLast();
                        context.addAnchor(biome.getName(), lastPlaced.getCenter());
                    }
                }

                // Phase 2: Place enclosed biomes (after their enclosing biomes are placed)
                for (Biome biome : enclosedBiomes) {
                    boolean placed = placeEnclosedBiome(biome, context);

                    if (!placed) {
                        log.warn("Failed to place enclosed biome: {} after {} attempts, retrying composition",
                            biome.getName(), context.getMaxRetriesPerBiome());
                        throw new BiomePlacementException("Could not place enclosed biome: " + biome.getName());
                    }

                    // Add biome name as anchor for other biomes to reference
                    if (biome.getName() != null && !context.getPlacedBiomes().isEmpty()) {
                        PlacedBiome lastPlaced = context.getPlacedBiomes().getLast();
                        context.addAnchor(biome.getName(), lastPlaced.getCenter());
                    }
                }

                success = true;
                log.debug("Successfully placed all {} biomes ({} normal, {} enclosed)",
                    prepared.getBiomes().size(), normalBiomes.size(), enclosedBiomes.size());

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

        // Configure FeatureHexGrids for all placed biomes - register in central composition registry
        // WHexGrids will be created later by HexGridGenerator from these FeatureHexGrids
        configureHexGridsForPlacedBiomes(context.getPlacedBiomes(), prepared);

        return BiomePlacementResult.builder()
            .composition(prepared)
            .placedBiomes(context.getPlacedBiomes())
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
    private boolean placeBiome(Biome biome, CompositionContext context) {
        log.debug("Attempting to place biome: {} (type: {}, shape: {})",
            biome.getName(), biome.getType(), biome.getShape());

        // Sort positions by priority (use preparedPositions from Area)
        List<PreparedPosition> sortedPositions = new ArrayList<>(biome.getPreparedPositions());
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
                targetCenter = applyPlacementJitter(targetCenter, context.getPlacementTolerance(), context.getRandom());

                // Generate coordinates for this biome (use calculated values)
                int size = randomInRange(biome.getCalculatedSizeFrom(), biome.getCalculatedSizeTo(), context.getRandom());
                List<HexVector2> coordinates = generateBiomeCoordinates(
                    targetCenter, size, biome.getShape(), context, biome);

                // Check if all coordinates are available
                if (areCoordinatesAvailable(coordinates, context)) {
                    // Place the biome
                    for (HexVector2 coord : coordinates) {
                        context.occupy(coord);
                    }

                    // Store placement results directly in the biome
                    biome.setPlacedCenter(targetCenter);
                    biome.setAssignedCoordinates(coordinates);

                    PlacedBiome placed = PlacedBiome.builder()
                        .biome(biome)
                        .coordinates(coordinates)
                        .center(targetCenter)
                        .actualSize(size)
                        .build();

                    context.getPlacedBiomes().add(placed);

                    log.debug("Placed biome '{}' at {} with {} hexes (attempt {})",
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
     * Attempts to place an enclosed biome (surrounded by other biomes).
     * Uses the centroid of enclosing biomes as the target position.
     *
     * @param biome The enclosed biome to place
     * @param context The composition context
     * @return true if successfully placed
     */
    private boolean placeEnclosedBiome(Biome biome, CompositionContext context) {
        log.debug("Attempting to place enclosed biome: {} (enclosed by: {})",
            biome.getName(), biome.getEnclosedBy());

        // Find enclosing biomes
        List<PlacedBiome> enclosingBiomes = new ArrayList<>();
        for (String enclosingName : biome.getEnclosedBy()) {
            PlacedBiome enclosing = findPlacedBiome(enclosingName, context);
            if (enclosing != null) {
                enclosingBiomes.add(enclosing);
            } else {
                log.warn("Enclosing biome not found: {} (required by {})",
                    enclosingName, biome.getName());
            }
        }

        if (enclosingBiomes.isEmpty()) {
            log.error("No enclosing biomes found for: {}", biome.getName());
            return false;
        }

        // Calculate centroid of enclosing biomes
        HexVector2 centroid = calculateCentroid(enclosingBiomes);
        log.debug("Calculated centroid for enclosed biome '{}': {}", biome.getName(), centroid);

        // Try to place at centroid with multiple attempts
        int attempts = 0;
        while (attempts < context.getMaxRetriesPerBiome()) {
            attempts++;

            // Add some randomization to avoid exact center
            int offsetQ = context.getRandom().nextInt(5) - 2; // -2 to +2
            int offsetR = context.getRandom().nextInt(5) - 2;
            HexVector2 targetCenter = applyPlacementJitter(
                HexVector2.builder()
                    .q(centroid.getQ() + offsetQ)
                    .r(centroid.getR() + offsetR)
                    .build(),
                context.getPlacementTolerance(), context.getRandom());

            // Generate coordinates for this biome
            int size = randomInRange(biome.getCalculatedSizeFrom(), biome.getCalculatedSizeTo(), context.getRandom());
            List<HexVector2> coordinates = generateBiomeCoordinates(
                targetCenter, size, biome.getShape(), context, biome);

            // Check if all coordinates are available
            if (areCoordinatesAvailable(coordinates, context)) {
                // Place the biome
                for (HexVector2 coord : coordinates) {
                    context.occupy(coord);
                }

                // Store placement results
                biome.setPlacedCenter(targetCenter);
                biome.setAssignedCoordinates(coordinates);

                PlacedBiome placed = PlacedBiome.builder()
                    .biome(biome)
                    .coordinates(coordinates)
                    .center(targetCenter)
                    .actualSize(size)
                    .build();

                context.getPlacedBiomes().add(placed);

                log.debug("Placed enclosed biome '{}' at {} with {} hexes (attempt {})",
                    biome.getName(), targetCenter, coordinates.size(), attempts);

                return true;
            }
        }

        log.warn("Failed to place enclosed biome '{}' after {} attempts", biome.getName(), attempts);
        return false;
    }

    /**
     * Calculates the centroid (center point) of a list of placed biomes.
     *
     * @param biomes List of placed biomes
     * @return The centroid coordinate
     */
    private HexVector2 calculateCentroid(List<PlacedBiome> biomes) {
        if (biomes.isEmpty()) {
            return HexVector2.builder().q(0).r(0).build();
        }

        int sumQ = 0;
        int sumR = 0;
        for (PlacedBiome biome : biomes) {
            sumQ += biome.getCenter().getQ();
            sumR += biome.getCenter().getR();
        }

        return HexVector2.builder()
            .q(sumQ / biomes.size())
            .r(sumR / biomes.size())
            .build();
    }

    /**
     * Finds a placed biome by its name.
     *
     * @param name The biome name to search for
     * @param context The composition context
     * @return The placed biome or null if not found
     */
    private PlacedBiome findPlacedBiome(String name, CompositionContext context) {
        for (PlacedBiome placed : context.getPlacedBiomes()) {
            if (name.equals(placed.getBiome().getName())) {
                return placed;
            }
        }
        return null;
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
        // Pointy-top hex directions in odd-r offset coordinates.
        // North = r+ = Z+ in 3D world. No exceptions.
        // 0° (NE): (1, 1), 60° (E): (1, 0), 120° (SE): (0, -1)
        // 180° (SW): (-1, -1), 240° (W): (-1, 0), 300° (NW): (0, 1)

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

        // Get direction unit vector for pointy-top hex (North = r+)
        int dq = 0, dr = 0;
        switch (closestAngle) {
            case 0:   dq = 1;  dr = 1;  break; // NE (north-east, r+ = north)
            case 60:  dq = 1;  dr = 0;  break; // E  (east)
            case 120: dq = 0;  dr = -1; break; // SE (south-east, r- = south)
            case 180: dq = -1; dr = -1; break; // SW (south-west, r- = south)
            case 240: dq = -1; dr = 0;  break; // W  (west)
            case 300: dq = 0;  dr = 1;  break; // NW (north-west, r+ = north)
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
                                                      AreaShape shape, CompositionContext context,
                                                      Biome biome) {
        List<HexVector2> coordinates = new ArrayList<>();

        if (shape == null) {
            coordinates.add(center); // Single hex
            return coordinates;
        }

        switch (shape) {
            case CIRCLE -> coordinates = generateCircularCoordinates(center, size);
            case LINE -> coordinates = generateLineCoordinates(center, size, context.getRandom(), biome);
            case RECTANGLE ->
                // For now, treat RECTANGLE like CIRCLE (can be improved later)
                    coordinates = generateCircularCoordinates(center, size);
            default -> coordinates.add(center); // Single hex
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
     * Generates line of hexes with optional direction deviation for organic shapes.
     *
     * @param startCenter Starting center coordinate
     * @param size Number of hexes to generate
     * @param random Random generator
     * @param area Area containing deviation parameters
     * @return List of coordinates forming a line (possibly with deviations)
     */
    private List<HexVector2> generateLineCoordinates(HexVector2 startCenter, int size, Random random, Area area) {
        List<HexVector2> coords = new ArrayList<>();
        coords.add(startCenter);

        if (size <= 1) return coords;

        // Get deviation parameters from area
        double deviationLeft = 0.0;
        double deviationRight = 0.0;

        if (area != null) {
            deviationLeft = area.getEffectiveDeviationLeft();
            deviationRight = area.getEffectiveDeviationRight();
        }

        // Random initial direction for line
        int direction = random.nextInt(6); // 0-5 for 6 hex directions

        // Current position
        HexVector2 current = startCenter;

        // Add hexes in line with possible deviations
        for (int i = 1; i < size; i++) {
            // Check if we should deviate
            double rand = random.nextDouble();

            if (rand < deviationLeft) {
                // Deviate left
                direction = (direction - 1 + 6) % 6;
            } else if (rand < deviationLeft + deviationRight) {
                // Deviate right
                direction = (direction + 1) % 6;
            }
            // else: continue straight

            // Move to next hex using odd-r offset neighbor
            current = HexMathUtil.getNeighborPosition(current, DIRECTION_EDGES[direction]);

            coords.add(current);
        }

        return coords;
    }

    /**
     * Maps direction index (0-5) to EDGE for odd-r offset neighbor lookup.
     * 0=SOUTH_EAST, 1=SOUTH_WEST, 2=WEST, 3=NORTH_WEST, 4=NORTH_EAST, 5=EAST
     */
    private static final WHexGrid.EDGE[] DIRECTION_EDGES = WHexGrid.EDGE.values();

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
        used.add(TypeUtil.toStringHexCoord(center));

        int attempts = 0;
        while (coords.size() < size && attempts < size * 10) {
            attempts++;

            int ring = random.nextInt(radius) + 1;
            List<HexVector2> candidates = getHexRing(center, ring);

            if (!candidates.isEmpty()) {
                HexVector2 candidate = candidates.get(random.nextInt(candidates.size()));
                String key = TypeUtil.toStringHexCoord(candidate);

                if (!used.contains(key)) {
                    coords.add(candidate);
                    used.add(key);
                }
            }
        }

        return coords;
    }

    /**
     * Gets all hexes in a ring around center using odd-r offset coordinates.
     */
    private List<HexVector2> getHexRing(HexVector2 center, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        if (radius == 0) {
            ring.add(center);
            return ring;
        }

        // Walk directions for a ring: after starting at WEST, walk these edges
        WHexGrid.EDGE[] walkDirections = {
            WHexGrid.EDGE.NORTH_EAST,
            WHexGrid.EDGE.EAST,
            WHexGrid.EDGE.SOUTH_EAST,
            WHexGrid.EDGE.SOUTH_WEST,
            WHexGrid.EDGE.WEST,
            WHexGrid.EDGE.NORTH_WEST
        };

        // Start at position 'radius' steps WEST from center
        HexVector2 current = center;
        for (int i = 0; i < radius; i++) {
            current = HexMathUtil.getNeighborPosition(current, WHexGrid.EDGE.WEST);
        }

        // Walk around the ring
        for (WHexGrid.EDGE direction : walkDirections) {
            for (int step = 0; step < radius; step++) {
                ring.add(current);
                current = HexMathUtil.getNeighborPosition(current, direction);
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
     * NOTE: WHexGrid creation removed - now handled by HexGridGenerator
     * which converts FeatureHexGrids from Central Registry to WHexGrids.
     * This ensures consistent architecture with Flow handling.
     */

    /**
     * Returns random int in range [from, to] inclusive
     */
    private int randomInRange(int from, int to, Random random) {
        if (from >= to) return from;
        return from + random.nextInt(to - from + 1);
    }

    /**
     * Stores HexGrid configurations in PreparedBiomes
     * <p>
     * Configures HexGrids for all placed biomes by calling each biome's configureHexGrids method.
     * Each biome configures its own grids polymorphically.
     */
    /**
     * Configures FeatureHexGrids for all placed biomes by registering them
     * in the central composition registry.
     * This replaces the old approach of storing grids in individual biome instances.
     *
     * @param placedBiomes List of placed biomes
     * @param composition Composition with central FeatureHexGrid registry
     */
    public void configureHexGridsForPlacedBiomes(List<PlacedBiome> placedBiomes, HexComposition composition) {
        for (PlacedBiome placed : placedBiomes) {
            Biome biome = placed.getBiome();
            List<de.mhus.nimbus.generated.types.HexVector2> coordinates = placed.getCoordinates();

            if (coordinates == null || coordinates.isEmpty()) {
                continue;
            }

            // Create FeatureHexGrid for each coordinate and register in central registry
            for (de.mhus.nimbus.generated.types.HexVector2 coord : coordinates) {
                // Get or create grid from central registry (prevents duplicates)
                de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid featureHexGrid =
                    composition.getOrCreateFeatureHexGrid(coord);

                // Set source biome reference
                if (featureHexGrid.getSourceBiomeName() == null) {
                    featureHexGrid.setSourceBiomeName(biome.getName());
                    featureHexGrid.setSourceBiomeId(biome.getFeatureId());
                }

                // Set name and description if not already set
                if (featureHexGrid.getName() == null) {
                    featureHexGrid.setName(biome.getName() + " [" + coord.getQ() + ";" + coord.getR() + "]");
                }
                if (featureHexGrid.getDescription() == null) {
                    featureHexGrid.setDescription("Part of " + (biome.getType() != null ? biome.getType().name() : "unknown") + " biome");
                }

                // Copy biome parameters to grid (only if not already present)
                if (biome.getParameters() != null) {
                    for (Map.Entry<String, String> entry : biome.getParameters().entrySet()) {
                        if (!featureHexGrid.getParameters().containsKey(entry.getKey())) {
                            featureHexGrid.addParameter(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // Add biome type parameter (use builderName for consistency)
                if (biome.getType() != null) {
                    if (!featureHexGrid.getParameters().containsKey("biome")) {
                        featureHexGrid.addParameter("biome", biome.getType().getBuilderName());
                    }
                    if (!featureHexGrid.getParameters().containsKey("biomeName")) {
                        featureHexGrid.addParameter("biomeName", biome.getName());
                    }
                    if (!featureHexGrid.getParameters().containsKey("biomeType")) {
                        featureHexGrid.addParameter("biomeType", biome.getType().name());
                    }
                }

                // Set filler information from parameters
                if (featureHexGrid.getParameters().containsKey("filler")) {
                    boolean isFiller = "true".equals(featureHexGrid.getParameters().get("filler"));
                    featureHexGrid.setFiller(isFiller);

                    if (isFiller && featureHexGrid.getParameters().containsKey("fillerType")) {
                        String fillerTypeStr = featureHexGrid.getParameters().get("fillerType");
                        try {
                            de.mhus.nimbus.world.generator.composer.filler.FillerType fillerType =
                                de.mhus.nimbus.world.generator.composer.filler.FillerType.valueOf(fillerTypeStr.toUpperCase());
                            featureHexGrid.setFillerType(fillerType);
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid fillerType '{}' for grid [{},{}], ignoring",
                                fillerTypeStr, coord.getQ(), coord.getR());
                        }
                    }
                }

                log.trace("Registered FeatureHexGrid [{},{}] for biome '{}' in central registry",
                    coord.getQ(), coord.getR(), biome.getName());
            }
        }

        log.debug("Registered {} biomes with their HexGrids in central composition registry",
            placedBiomes.size());
    }

    /**
     * Applies random jitter offset to a target position.
     * Shifts the target by up to ±tolerance hexes in Q and R.
     *
     * @param target Original target position
     * @param tolerance Maximum offset in hex distance (0 = no jitter)
     * @param random Random generator
     * @return Jittered position
     */
    private HexVector2 applyPlacementJitter(HexVector2 target, int tolerance, Random random) {
        if (tolerance <= 0) return target;
        int offsetQ = random.nextInt(2 * tolerance + 1) - tolerance;
        int offsetR = random.nextInt(2 * tolerance + 1) - tolerance;
        return HexVector2.builder()
            .q(target.getQ() + offsetQ)
            .r(target.getR() + offsetR)
            .build();
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
