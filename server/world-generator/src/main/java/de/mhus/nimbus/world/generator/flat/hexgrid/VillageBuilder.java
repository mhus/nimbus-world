package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.hexgrid.composer.TextOverlay;
import de.mhus.nimbus.world.generator.flat.hexgrid.composer.VillageGridConfig;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * VillageBuilder builds villages from the new district/place-based configuration.
 *
 * NEW structure:
 * - Parameter: village={...} (NOT g_village, the 'g' is stripped)
 * - Uses VillageGridConfig with places and streets
 * - Drawing order is critical (to handle overlaps):
 *   1. Noise (done before this builder)
 *   2. Streets (all road places)
 *   3. Free places (parks, gardens, plazas, squares)
 *   4. Buildings (only plot at level+1)
 */
@Slf4j
public class VillageBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        // Get actual hexGridSize from world context
        int hexGridSize = context.getHexGridSize();

        log.info("Building village for flat: {} with hexGridSize: {}", flat.getFlatId(), hexGridSize);

        // Get village parameter from hex grid
        String villageParam = hexGrid.getParameters() != null ?
            hexGrid.getParameters().get("g_village") : null;

        if (villageParam == null || villageParam.isBlank()) {
            log.debug("No village parameter found, skipping");
            return;
        }

        try {
            // Parse village configuration
            VillageGridConfig config = objectMapper.readValue(villageParam, VillageGridConfig.class);

            log.info("Parsed village config for '{}' district '{}': {} places, {} streets",
                config.getVillageName(), config.getDistrictName(),
                config.getPlaces() != null ? config.getPlaces().size() : 0,
                config.getStreets() != null ? config.getStreets().size() : 0);

            // Convert hexagonal coordinates to cartesian for all places
            convertHexToCartesian(config, flat, hexGridSize);

            // Log all converted positions for debugging
            log.info("=== SLOT_POSITIONS: District '{}' with {} places (hexGridSize={}) ===",
                config.getDistrictName(), config.getPlaces().size(), hexGridSize);
            for (VillageGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
                log.info("SLOT_POSITION: district='{}' name='{}' type='{}' hex=<{};{}> local=({},{}) divider={}",
                    config.getDistrictName(), place.getName(), place.getType(),
                    place.getHexQ(), place.getHexR(),
                    place.getLocalX(), place.getLocalZ(),
                    place.getDivider());
            }

            // DRAWING ORDER (critical for overlaps):

            // Step 1: Noise is already drawn by NoiseBuilder before this

            // Step 2: Draw all street slots
            drawStreets(flat, config);

            // Step 2.5: Draw connections from buildings to streets
            drawBuildingToStreetConnections(flat, config);

            // Step 3: Draw all free place slots
            drawFreePlaces(flat, config, hexGridSize);

            // Step 4: Draw all building slots (only plot at level+1)
            drawBuildings(flat, config, hexGridSize);

            // Step 5: Draw debug markers if enabled
            String debugParam = hexGrid.getParameters() != null ?
                hexGrid.getParameters().get("g_village_debug") : null;
            boolean debugEnabled = "true".equalsIgnoreCase(debugParam);

            if (debugEnabled) {
                drawDebugMarkers(flat, config);
                drawDebugLabels(flat, config);
            }

            log.info("Village district '{}' completed", config.getDistrictName());

        } catch (Exception e) {
            log.error("Failed to build village for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Step 2: Draw all street slots
     */
    private void drawStreets(WFlat flat, VillageGridConfig config) {
        if (config.getStreets() == null || config.getStreets().isEmpty()) {
            log.debug("No streets to draw");
            return;
        }

        log.debug("Drawing {} streets", config.getStreets().size());

        for (VillageGridConfig.StreetSegmentConfig street : config.getStreets()) {
            drawStreetSegment(flat, street);
        }

        log.debug("Streets completed");
    }

    /**
     * Draw a single street segment
     */
    private void drawStreetSegment(WFlat flat, VillageGridConfig.StreetSegmentConfig street) {
        int dx = street.getToX() - street.getFromX();
        int dz = street.getToZ() - street.getFromZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Determine material based on type
        int material = getMaterialForStreetType(street.getType());

        // Draw line from start to end
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            int x = (int) Math.round(street.getFromX() + t * dx);
            int z = (int) Math.round(street.getFromZ() + t * dz);
            int level = street.getLevel();

            // Draw road segment with width
            int halfWidth = street.getWidth() / 2;
            for (int dwx = -halfWidth; dwx <= halfWidth; dwx++) {
                for (int dwz = -halfWidth; dwz <= halfWidth; dwz++) {
                    int wx = x + dwx;
                    int wz = z + dwz;

                    if (wx >= 0 && wx < flat.getSizeX() && wz >= 0 && wz < flat.getSizeZ()) {
                        flat.setLevel(wx, wz, level);
                        flat.setColumn(wx, wz, material);
                    }
                }
            }
        }
    }

    /**
     * Get material for street type
     */
    private int getMaterialForStreetType(String type) {
        if (type == null) {
            return FlatMaterialService.STREET;
        }

        switch (type.toLowerCase()) {
            case "street":
                return FlatMaterialService.STREET;
            case "path":
            case "trail":
                return FlatMaterialService.TRACK;
            case "alley":
                return FlatMaterialService.STREET;
            default:
                return FlatMaterialService.STREET;
        }
    }

    /**
     * Step 2.5: Draw connections from each building/plaza/square to nearest street
     */
    private void drawBuildingToStreetConnections(WFlat flat, VillageGridConfig config) {
        if (config.getPlaces() == null || config.getStreets() == null) {
            log.debug("No places or streets in config");
            return;
        }

        // Get all places that need street connections:
        // - All buildings
        // - Free places that are PLAZA or SQUARE (not PARK or GARDEN)
        List<VillageGridConfig.PlacedPlaceConfig> placesNeedingStreets = config.getPlaces().stream()
            .filter(p -> {
                if ("building".equals(p.getType())) {
                    return true;
                }
                if ("free".equals(p.getType())) {
                    String kind = p.getKind();
                    return "PLAZA".equalsIgnoreCase(kind) || "SQUARE".equalsIgnoreCase(kind);
                }
                return false;
            })
            .toList();

        log.debug("Found {} places (buildings/plazas/squares) and {} streets in district '{}'",
            placesNeedingStreets.size(), config.getStreets().size(), config.getDistrictName());

        if (placesNeedingStreets.isEmpty() || config.getStreets().isEmpty()) {
            log.debug("No places or streets to connect");
            return;
        }

        log.debug("Drawing {} place-to-street connections", placesNeedingStreets.size());

        for (VillageGridConfig.PlacedPlaceConfig place : placesNeedingStreets) {
            // Find nearest street segment
            VillageGridConfig.StreetSegmentConfig nearestStreet = findNearestStreetSegment(
                place.getLocalX(), place.getLocalZ(), config.getStreets());

            if (nearestStreet != null) {
                // Calculate closest point on the street segment
                int[] closestPoint = closestPointOnSegment(
                    place.getLocalX(), place.getLocalZ(),
                    nearestStreet.getFromX(), nearestStreet.getFromZ(),
                    nearestStreet.getToX(), nearestStreet.getToZ());

                // Draw connection from place to street
                drawStreetSegment(flat, VillageGridConfig.StreetSegmentConfig.builder()
                    .fromX(place.getLocalX())
                    .fromZ(place.getLocalZ())
                    .toX(closestPoint[0])
                    .toZ(closestPoint[1])
                    .width(2)  // Smaller width for connections
                    .type("path")
                    .level(config.getBaseLevel())
                    .build());

                log.debug("Connected place '{}' ({}) at [{},{}] to street at [{},{}]",
                    place.getName(), place.getType(), place.getLocalX(), place.getLocalZ(),
                    closestPoint[0], closestPoint[1]);
            }
        }

        log.debug("Building-to-street connections completed");
    }

    /**
     * Find the nearest street segment to a point
     */
    private VillageGridConfig.StreetSegmentConfig findNearestStreetSegment(
            int x, int z, List<VillageGridConfig.StreetSegmentConfig> streets) {
        VillageGridConfig.StreetSegmentConfig nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (VillageGridConfig.StreetSegmentConfig street : streets) {
            double distance = distanceToSegment(x, z,
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
     * Calculate distance from point to line segment
     */
    private double distanceToSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        int[] closest = closestPointOnSegment(px, pz, x1, z1, x2, z2);
        int dx = closest[0] - px;
        int dz = closest[1] - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Find closest point on a line segment to a given point
     */
    private int[] closestPointOnSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;

        if (dx == 0 && dz == 0) {
            // Segment is a point
            return new int[]{x1, z1};
        }

        // Calculate projection parameter t
        double t = ((px - x1) * dx + (pz - z1) * dz) / (double)(dx * dx + dz * dz);

        // Clamp t to [0, 1] to stay on segment
        t = Math.max(0, Math.min(1, t));

        // Calculate closest point
        int closestX = (int) Math.round(x1 + t * dx);
        int closestZ = (int) Math.round(z1 + t * dz);

        return new int[]{closestX, closestZ};
    }

    /**
     * Step 3: Draw all free place slots
     */
    private void drawFreePlaces(WFlat flat, VillageGridConfig config, int hexGridSize) {
        if (config.getPlaces() == null) {
            return;
        }

        List<VillageGridConfig.PlacedPlaceConfig> freePlaces = config.getPlaces().stream()
            .filter(p -> "free".equals(p.getType()))
            .toList();

        if (freePlaces.isEmpty()) {
            log.debug("No free places to draw");
            return;
        }

        log.debug("Drawing {} free places", freePlaces.size());

        for (VillageGridConfig.PlacedPlaceConfig place : freePlaces) {
            drawFreePlace(flat, place, config, hexGridSize);
        }

        log.debug("Free places completed");
    }

    /**
     * Draw a single free place (park, garden, plaza, square)
     */
    private void drawFreePlace(WFlat flat, VillageGridConfig.PlacedPlaceConfig place,
                                VillageGridConfig config, int hexGridSize) {
        // Calculate slot size based on divider from place
        int divider = place.getDivider() > 0 ? place.getDivider() : 5; // Default to 5 if not set
        int slotSize = calculateSlotSize(hexGridSize, divider);

        // Determine material based on kind
        int material = getMaterialForFreeKind(place.getKind());

        // Draw circular area
        drawCircularSlot(flat, place.getLocalX(), place.getLocalZ(),
            slotSize / 2, config.getBaseLevel(), material);

        log.debug("Drew free place '{}' ({}) at [{},{}] with divider {} (size {})",
            place.getName(), place.getKind(), place.getLocalX(), place.getLocalZ(),
            divider, slotSize);
    }

    /**
     * Get material for free place kind
     */
    private int getMaterialForFreeKind(String kind) {
        if (kind == null) {
            return FlatMaterialService.GRASS;
        }

        switch (kind.toUpperCase()) {
            case "PARK":
            case "GARDEN":
                return FlatMaterialService.GRASS;
            case "PLAZA":
            case "SQUARE":
                return FlatMaterialService.STREET;
            default:
                return FlatMaterialService.GRASS;
        }
    }

    /**
     * Step 4: Draw all building slots (only plot at level+1)
     */
    private void drawBuildings(WFlat flat, VillageGridConfig config, int hexGridSize) {
        if (config.getPlaces() == null) {
            return;
        }

        List<VillageGridConfig.PlacedPlaceConfig> buildings = config.getPlaces().stream()
            .filter(p -> "building".equals(p.getType()))
            .toList();

        if (buildings.isEmpty()) {
            log.debug("No buildings to draw");
            return;
        }

        log.debug("Drawing {} building plots", buildings.size());

        for (VillageGridConfig.PlacedPlaceConfig place : buildings) {
            drawBuildingPlot(flat, place, config, hexGridSize);
        }

        log.debug("Building plots completed");
    }

    /**
     * Draw a single building plot (elevated platform at level+1)
     */
    private void drawBuildingPlot(WFlat flat, VillageGridConfig.PlacedPlaceConfig place,
                                   VillageGridConfig config, int hexGridSize) {
        // Calculate slot size based on divider from place
        int divider = place.getDivider() > 0 ? place.getDivider() : 5; // Default to 5 if not set
        int slotSize = calculateSlotSize(hexGridSize, divider);

        // Building plot is at baseLevel + 1
        int plotLevel = config.getBaseLevel() + 1;

        // Get material based on village style
        int material = getMaterialForBuildingPlatform(config.getStyle());

        // For large buildings (divider 1 or 3), use rectangular slots
        // For smaller buildings (divider 5 or 7), use circular slots
        if (divider <= 3 || place.isOversized()) {
            // Rectangular plot for large/oversized buildings
            int sizeX = slotSize;
            int sizeZ = slotSize;
            drawRectangularSlot(flat, place.getLocalX(), place.getLocalZ(),
                sizeX, sizeZ, plotLevel, material);

            // Store building ID as group
            if (place.getBuildingId() != null) {
                storeGroupInRectangularSlot(flat, place.getLocalX(), place.getLocalZ(),
                    sizeX, sizeZ, place.getBuildingId());
            }
        } else {
            // Circular plot for normal buildings
            drawCircularSlot(flat, place.getLocalX(), place.getLocalZ(),
                slotSize / 2, plotLevel, material);

            // Store building ID as group
            if (place.getBuildingId() != null) {
                storeGroupInSlot(flat, place.getLocalX(), place.getLocalZ(),
                    slotSize / 2, place.getBuildingId());
            }
        }

        log.debug("Drew building plot '{}' (buildingId: {}) at [{},{}] with divider {} (size {}, oversized: {})",
            place.getName(), place.getBuildingId(), place.getLocalX(), place.getLocalZ(),
            divider, slotSize, place.isOversized());
    }

    /**
     * Get material for building platform based on village style
     */
    private int getMaterialForBuildingPlatform(String style) {
        if (style == null) {
            return FlatMaterialService.STREET;
        }

        switch (style.toLowerCase()) {
            case "medieval":
                return FlatMaterialService.STREET; // Stone
            case "modern":
                return FlatMaterialService.STREET; // Concrete
            case "fantasy":
                return FlatMaterialService.GRASS; // Natural platform
            default:
                return FlatMaterialService.STREET;
        }
    }

    /**
     * Draw a rectangular slot
     */
    private void drawRectangularSlot(WFlat flat, int centerX, int centerZ,
                                      int sizeX, int sizeZ, int level, int material) {
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        for (int dx = -halfX; dx < halfX; dx++) {
            for (int dz = -halfZ; dz < halfZ; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                    flat.setLevel(x, z, level);
                    flat.setColumn(x, z, material);
                }
            }
        }
    }

    /**
     * Store group ID in rectangular slot area
     */
    private void storeGroupInRectangularSlot(WFlat flat, int centerX, int centerZ,
                                              int sizeX, int sizeZ, String groupId) {
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        for (int dx = -halfX; dx < halfX; dx++) {
            for (int dz = -halfZ; dz < halfZ; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                    flat.setGroup(x, z, groupId);
                }
            }
        }
    }

    /**
     * Calculate slot size based on grid size and divider
     */
    private int calculateSlotSize(int gridSize, int divider) {
        if (divider <= 0) {
            return gridSize;
        }
        return gridSize / divider;
    }

    /**
     * Draw a circular slot
     */
    private void drawCircularSlot(WFlat flat, int centerX, int centerZ,
                                   int radius, int level, int material) {
        double radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;

                if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                    flat.setLevel(x, z, level);
                    flat.setColumn(x, z, material);
                }
            }
        }
    }

    /**
     * Store group ID in circular slot area
     */
    private void storeGroupInSlot(WFlat flat, int centerX, int centerZ,
                                   int radius, String groupId) {
        double radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;

                if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                    flat.setGroup(x, z, groupId);
                }
            }
        }
    }

    /**
     * Convert hexagonal coordinates to cartesian coordinates for all places in the config.
     * This must be called after parsing the VillageGridConfig and before drawing.
     */
    private void convertHexToCartesian(VillageGridConfig config, WFlat flat, int hexGridSize) {
        if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
            return;
        }

        log.info("Converting {} places from hex to cartesian coordinates (hexGridSize: {}, flatSize: {}x{})",
            config.getPlaces().size(), hexGridSize, flat.getSizeX(), flat.getSizeZ());

        for (VillageGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
            // Create HexVector2 from hex coordinates
            HexVector2 hexPos = TypeUtil.hexVector2(place.getHexQ(), place.getHexR());

            // Calculate slot size based on divider
            int slotSize = hexGridSize / place.getDivider();

            // Create HexLocalPosition
            HexLocalPosition hexLocalPos = new HexLocalPosition(hexPos, place.getDivider(), slotSize);

            // Convert hex position to cartesian coordinates relative to grid center
            Vector2Int relativePos = HexLocalUtil.toHexGridLocalCenter(hexLocalPos);

            // Convert to absolute coordinates using flat center (not hexGridSize center)
            // Flat is larger than hexGrid due to borders, so use flat dimensions
            int localX = flat.getSizeX() / 2 + relativePos.getX();
            int localZ = flat.getSizeZ() / 2 + relativePos.getZ();

            // Update place config with cartesian coordinates
            place.setLocalX(localX);
            place.setLocalZ(localZ);

            log.debug("Converted place '{}' from hex <{};{}> to cartesian ({}, {})",
                place.getName(), place.getHexQ(), place.getHexR(), localX, localZ);
        }
    }

    /**
     * Draw debug markers at the center of each place (only if g_village_debug=true)
     * Creates a high marker (level 250) at each local position to visualize placement
     */
    private void drawDebugMarkers(WFlat flat, VillageGridConfig config) {
        if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
            return;
        }

        log.info("Drawing {} debug markers for village district '{}'",
            config.getPlaces().size(), config.getDistrictName());

        for (VillageGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
            int x = place.getLocalX();
            int z = place.getLocalZ();

            // Draw a small 3x3 marker at level 250
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int markerX = x + dx;
                    int markerZ = z + dz;

                    if (markerX >= 0 && markerX < flat.getSizeX() &&
                        markerZ >= 0 && markerZ < flat.getSizeZ()) {
                        flat.setLevel(markerX, markerZ, 250);
                        flat.setColumn(markerX, markerZ, FlatMaterialService.STREET);
                    }
                }
            }

            log.debug("Debug marker for '{}' ({}) at [{},{}]",
                place.getName(), place.getType(), x, z);
        }

        log.info("Debug markers completed");
    }

    /**
     * Draw debug text labels at the center of each place (only if g_village_debug=true)
     * Uses the same bitmap font as TextOverlay to draw place names on level 250
     */
    private void drawDebugLabels(WFlat flat, VillageGridConfig config) {
        if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
            return;
        }

        log.info("Drawing {} debug labels for village district '{}'",
            config.getPlaces().size(), config.getDistrictName());

        for (VillageGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
            String label = place.getName();
            if (label == null || label.isEmpty()) {
                continue;
            }

            int centerX = place.getLocalX();
            int centerZ = place.getLocalZ();

            // Calculate text width to center it
            int textWidth = calculateTextWidth(label);
            int startX = centerX - textWidth / 2;
            int startZ = centerZ + 5; // Offset below the marker

            drawTextOnFlat(flat, label, startX, startZ);

            log.debug("Debug label '{}' at [{},{}]", label, startX, startZ);
        }

        log.info("Debug labels completed");
    }

    /**
     * Calculate the width of text in blocks using the bitmap font
     */
    private int calculateTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() * (TextOverlay.CHAR_WIDTH + TextOverlay.CHAR_SPACING) - TextOverlay.CHAR_SPACING;
    }

    /**
     * Draw text on the flat using the bitmap font from TextOverlay
     * Each pixel of the font is drawn as a block at level 250
     */
    private void drawTextOnFlat(WFlat flat, String text, int startX, int startZ) {
        if (text == null || text.isEmpty()) {
            return;
        }

        String upperText = text.toUpperCase();
        int currentX = startX;

        for (int i = 0; i < upperText.length(); i++) {
            char c = upperText.charAt(i);
            int[][] charBitmap = TextOverlay.FONT.get(c);

            if (charBitmap != null) {
                drawCharacterOnFlat(flat, charBitmap, currentX, startZ);
            }

            currentX += TextOverlay.CHAR_WIDTH + TextOverlay.CHAR_SPACING;
        }
    }

    /**
     * Draw a single character bitmap on the flat at level 250
     */
    private void drawCharacterOnFlat(WFlat flat, int[][] bitmap, int startX, int startZ) {
        for (int row = 0; row < TextOverlay.CHAR_HEIGHT; row++) {
            for (int col = 0; col < TextOverlay.CHAR_WIDTH; col++) {
                if (bitmap[row][col] == 1) {
                    int x = startX + col;
                    int z = startZ + row;

                    if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                        flat.setLevel(x, z, 250);
                        flat.setColumn(x, z, FlatMaterialService.STREET);
                    }
                }
            }
        }
    }

    @Override
    protected int getDefaultLandOffset() {
        return 0;
    }

    @Override
    protected int getDefaultLandLevel() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getLandCenterLevel();
    }
}
