package de.mhus.nimbus.world.generator.composer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.FilledHexGrid;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import de.mhus.nimbus.world.generator.composer.image.CrossOverlay;
import de.mhus.nimbus.world.generator.composer.image.TextOverlay;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.generator.composer.village.VillageDebugOverlayHelper;
import de.mhus.nimbus.world.shared.archive.WArchiveService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static de.mhus.nimbus.world.generator.translator.TranslateInstructionJobExecutor.COMPOSED_COLLECTION;

/**
 * Job executor for creating composite images from hex grids.
 * Loads all flats matching a pattern and creates level and material composite images
 * showing the complete world.
 * <p>
 * Required parameters:
 * - compositionId: ID of the composition (used in archive path)
 * <p>
 * Optional parameters:
 * - drawGridLines: Whether to draw hex grid lines (default: false)
 * - flatIdSuffix: Prefix pattern for flatIds (e.g., "genesis_" matches "genesis_0_0", "genesis_1_0", etc.)
 * <p>
 * Images are stored in the archive at:
 * - "composites/{worldId}_{compositionId}_level.png"
 * - "composites/{worldId}_{compositionId}_material.png"
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridCompositeImageJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "hex-grid-composite-image";

    private final WFlatService flatService;
    private final WArchiveService archiveService;
    private final WWorldService worldService;
    private final WDocumentService documentService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting hex grid composite image creation: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();
            WorldId.of(worldId).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldId)
            );
            WWorld world = worldService.getByWorldId(worldId).orElseThrow();

            // Extract required parameters
            String compositionId = getRequiredParameter(job, "compositionId");

            // Extract optional parameters
            boolean drawGridLines = getOptionalBooleanParameter(job, "drawGridLines", false);
            String flatIdSuffix = getOptionalParameter(job, "flatIdSuffix", "genesis_");

            int flatSize = world.getPublicData().getHexGridSize();

            log.info("Creating composite images: worldId={}, compositionId={}, flatIdSuffix={}, flatSize={}, drawGridLines={}",
                    worldId, compositionId, flatIdSuffix, flatSize, drawGridLines);

            // Step 1: Load HexComposition from document to get FilledHexGrids
            HexComposition composition = loadComposition(worldId, compositionId);
            if (composition == null || composition.getFilledHexGrids() == null || composition.getFilledHexGrids().isEmpty()) {
                throw new JobExecutionException("No FilledHexGrids found in composition: " + compositionId);
            }

            log.info("Loaded composition with {} FilledHexGrids", composition.getFilledHexGrids().size());

            // Step 2: Extract coordinates from FilledHexGrids
            Set<HexVector2> validCoordinates = new HashSet<>();
            Map<HexVector2, WHexGrid> hexGridsByCoord = new HashMap<>();
            for (FilledHexGrid filledHexGrid : composition.getFilledHexGrids()) {
                validCoordinates.add(filledHexGrid.getCoordinate());
                if (filledHexGrid.getHexGrid() != null) {
                    hexGridsByCoord.put(filledHexGrid.getCoordinate(), filledHexGrid.getHexGrid());
                }
            }

            log.info("Found {} valid coordinates from FilledHexGrids", validCoordinates.size());

            // Step 3: Create filtered flat provider that only loads flats from FilledHexGrids
            FilteredFlatProvider flatProvider = new FilteredFlatProvider(flatService, worldId, flatIdSuffix, validCoordinates);

            // Check that we have grids to render
            int gridCount = flatProvider.getCoordinates().size();
            if (gridCount == 0) {
                throw new JobExecutionException("No flats found for coordinates from composition");
            }

            log.info("Found {} flats to render", gridCount);

            // Step 4: Create composite images using the provider
            HexGridCompositeImageCreator creator = HexGridCompositeImageCreator.builder()
                    .flatProvider(flatProvider)
                    .flatSize(flatSize)
                    .drawGridLines(drawGridLines)
                    .build();

            // Step 5: Add overlays from composition
            addOverlaysFromComposition(creator, composition, flatProvider, hexGridsByCoord, flatSize);

            // Step 6: Create composite images
            HexGridCompositeImageCreator.CompositeImageResult result = creator.createCompositeImages();

            if (!result.isSuccess()) {
                throw new JobExecutionException("Failed to create composite images: " + result.getErrorMessage());
            }

            log.info("Composite images created: {}x{} pixels, rendered {}/{} grids",
                    result.getImageWidth(), result.getImageHeight(),
                    result.getRenderedGridCount(), result.getTotalGridCount());

            // Convert images to PNG byte arrays
            byte[] levelImageBytes = convertImageToBytes(result.getLevelImage());
            byte[] materialImageBytes = convertImageToBytes(result.getMaterialImage());

            // Store in archive
            String levelPath = String.format("composites/%s_%s_level.png", worldId, compositionId);
            String materialPath = String.format("composites/%s_%s_material.png", worldId, compositionId);

            log.info("Storing level composite in archive: {}", levelPath);
            archiveService.archive(levelPath, new ByteArrayInputStream(levelImageBytes));

            log.info("Storing material composite in archive: {}", materialPath);
            archiveService.archive(materialPath, new ByteArrayInputStream(materialImageBytes));

            // Build success result
            String resultData = String.format(
                    "Successfully created composite images: worldId=%s, compositionId=%s, grids=%d/%d, size=%dx%d, levelSize=%d bytes, materialSize=%d bytes",
                    worldId, compositionId,
                    result.getRenderedGridCount(), result.getTotalGridCount(),
                    result.getImageWidth(), result.getImageHeight(),
                    levelImageBytes.length, materialImageBytes.length
            );

            log.info("Hex grid composite image creation completed successfully: jobId={}", job.getId());

            return JobResult.success(resultData);

        } catch (JobExecutionException e) {
            log.error("Hex grid composite image creation failed: jobId={}", job.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in hex grid composite image creation: jobId={}", job.getId(), e);
            throw new JobExecutionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a BufferedImage to PNG byte array.
     */
    private byte[] convertImageToBytes(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Get required parameter from job.
     */
    private String getRequiredParameter(WJob job, String key) throws JobExecutionException {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + key);
        }
        return value;
    }

    /**
     * Get optional  parameter from job.
     */
    private String getOptionalParameter(WJob job, String key, String defaultValue) {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get optional boolean parameter from job.
     */
    private boolean getOptionalBooleanParameter(WJob job, String key, boolean defaultValue) {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Load HexComposition from document.
     */
    private HexComposition loadComposition(String worldId, String compositionId) throws JobExecutionException {
        try {
            WorldId wid = WorldId.of(worldId)
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + worldId));

            Optional<WDocument> documentOpt = documentService.findByDocumentId(wid, COMPOSED_COLLECTION, compositionId);
            if (documentOpt.isEmpty()) {
                throw new JobExecutionException("Composition document not found: " + compositionId);
            }

            WDocument document = documentOpt.get();

            // Parse composition from document content
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            HexComposition composition = mapper.readValue(document.getContent(), HexComposition.class);
            log.info("Successfully loaded HexComposition from document: {}", compositionId);
            return composition;

        } catch (Exception e) {
            log.error("Failed to load composition: {}", compositionId, e);
            throw new JobExecutionException("Failed to load composition: " + e.getMessage(), e);
        }
    }

    /**
     * Add overlays from composition (coordinates, biome names, points, villages).
     */
    private void addOverlaysFromComposition(HexGridCompositeImageCreator creator,
                                           HexComposition composition,
                                           FilteredFlatProvider flatProvider,
                                           Map<HexVector2, WHexGrid> hexGridsByCoord,
                                           int flatSize) {
        // Add coordinate and biome name text overlays
        addCoordinateTextOverlays(creator, composition, flatProvider, flatSize);

        // Add point overlays (cross + name)
        addPointOverlays(creator, composition, flatProvider, flatSize);

        // Add village slot overlays (cross + slot name)
        addVillageSlotOverlays(creator, hexGridsByCoord, flatSize);
    }

    /**
     * Adds text overlays showing coordinates and biome names for all grids.
     */
    private void addCoordinateTextOverlays(HexGridCompositeImageCreator creator,
                                          HexComposition composition,
                                          FilteredFlatProvider flatProvider,
                                          int flatSize) {
        // Build map of coordinate to biome name
        Map<String, String> coordToBiomeName = new HashMap<>();
        if (composition.getFilledHexGrids() != null) {
            for (FilledHexGrid filled : composition.getFilledHexGrids()) {
                String coordKey = filled.getCoordinate().getQ() + "," + filled.getCoordinate().getR();
                String biomeName = null;

                if (filled.getBiome() != null && filled.getBiome().getBiome() != null) {
                    biomeName = filled.getBiome().getBiome().getName();
                } else if (filled.isFiller() && filled.getFillerType() != null) {
                    biomeName = filled.getFillerType().name().toLowerCase();
                }

                if (biomeName != null) {
                    coordToBiomeName.put(coordKey, biomeName);
                }
            }
        }

        for (HexVector2 coord : flatProvider.getCoordinates()) {
            String coordText = coord.getQ() + "," + coord.getR();

            // Calculate center position of hex grid in world coordinates
            double[] hexCenter = HexMathUtil.hexToCartesian(coord, flatSize);
            int centerX = (int) Math.floor(hexCenter[0]);
            int centerY = (int) Math.floor(hexCenter[1]);

            // Create coordinate text overlay centered on grid (white color, scale 3)
            int coordTextWidth = coordText.length() * (5 + 1) * 3;
            TextOverlay coordOverlay = new TextOverlay(coordText, centerX - coordTextWidth/2, centerY - 15, Color.WHITE, 3);
            creator.addOverlay(coordOverlay);

            // Add biome name below coordinates if available
            String coordKey = coord.getQ() + "," + coord.getR();
            String biomeName = coordToBiomeName.get(coordKey);
            if (biomeName != null) {
                int biomeTextWidth = biomeName.length() * (5 + 1) * 2;
                TextOverlay biomeOverlay = new TextOverlay(biomeName, centerX - biomeTextWidth/2, centerY + 5, Color.CYAN, 2);
                creator.addOverlay(biomeOverlay);
            }
        }

        log.info("Added coordinate text overlays for {} grids", flatProvider.getCoordinates().size());
    }

    /**
     * Adds overlays for all composed points showing their positions and names.
     */
    private void addPointOverlays(HexGridCompositeImageCreator creator,
                                 HexComposition composition,
                                 FilteredFlatProvider flatProvider,
                                 int flatSize) {
        if (composition.getFeatures() == null) {
            return;
        }

        // Collect all points from composition
        List<Point> points = composition.getFeatures().stream()
                .filter(f -> f instanceof Point)
                .map(f -> (Point) f)
                .filter(p -> p.getPointComposed() != null && p.getPointComposed().getGridCoordinate() != null)
                .toList();

        if (points.isEmpty()) {
            return;
        }

        log.info("Adding overlays for {} composed points", points.size());

        for (Point point : points) {
            Point.PointComposed composed = point.getPointComposed();
            HexVector2 gridCoord = composed.getGridCoordinate();

            // Get WFlat for this grid
            WFlat flat = flatProvider.getFlat(gridCoord);
            if (flat == null) {
                log.warn("No WFlat found for point '{}' at grid [{},{}]",
                        point.getName(), gridCoord.getQ(), gridCoord.getR());
                continue;
            }

            // Convert HexLocal position to absolute world coordinates
            int[] worldCoords = getPointWorldCoordinates(composed, flat.getSizeX(), flat.getSizeZ(), gridCoord, flatSize);
            if (worldCoords == null) {
                log.warn("Could not calculate world coordinates for point '{}'", point.getName());
                continue;
            }

            int worldX = worldCoords[0];
            int worldZ = worldCoords[1];

            // Add CrossOverlay at point position (red color, size 20, thickness 3)
            creator.addOverlay(new CrossOverlay(worldX, worldZ, 20, Color.RED, 3.0f));

            // Add TextOverlay with point name (yellow color, scale 3)
            String pointName = point.getName() != null ? point.getName() : "point";
            int textWidth = pointName.length() * (5 + 1) * 3;
            TextOverlay textOverlay = new TextOverlay(pointName, worldX - textWidth/2, worldZ - 25, Color.YELLOW, 3);
            creator.addOverlay(textOverlay);

            log.debug("Added overlay for point '{}' at world coords ({}, {})", pointName, worldX, worldZ);
        }
    }

    /**
     * Calculates world coordinates for a point from its HexLocal position.
     */
    private int[] getPointWorldCoordinates(Point.PointComposed composed, int flatSizeX, int flatSizeZ,
                                          HexVector2 gridCoord, int flatSize) {
        int hexGridSize = flatSizeX;

        // Get position string
        String positionString = null;
        if (composed.getHexLocalPosition() != null) {
            positionString = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(composed.getHexLocalPosition());
        } else if (composed.getHexLocalEdgeVector() != null) {
            positionString = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(composed.getHexLocalEdgeVector());
        }

        if (positionString == null || positionString.isBlank()) {
            return null;
        }

        // Convert to relative coordinates
        de.mhus.nimbus.generated.types.Vector2Int relativePos =
                de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(positionString, hexGridSize);

        // Convert to absolute local coordinates within the flat
        int lx = flatSizeX / 2 + relativePos.getX();
        int lz = flatSizeZ / 2 + relativePos.getZ();

        // Convert to absolute world coordinates
        double[] hexCenter = HexMathUtil.hexToCartesian(gridCoord, flatSize);
        int mountX = (int) Math.floor(hexCenter[0] - flatSize / 2.0);
        int mountZ = (int) Math.floor(hexCenter[1] - flatSize / 2.0);

        int worldX = mountX + lx;
        int worldZ = mountZ + lz;

        return new int[]{worldX, worldZ};
    }

    /**
     * Adds village slot overlays (cross + slot name) to the composite image creator.
     */
    private void addVillageSlotOverlays(HexGridCompositeImageCreator creator,
                                       Map<HexVector2, WHexGrid> hexGridsByCoord,
                                       int flatSize) {
        if (hexGridsByCoord.isEmpty()) {
            return;
        }

        // Use VillageDebugOverlayHelper to add village slot overlays
        VillageDebugOverlayHelper.addVillageSlotOverlaysFromHexGrids(creator, hexGridsByCoord, flatSize);
        log.info("Added village slot overlays for {} grids", hexGridsByCoord.size());
    }

    /**
     * Filtered flat provider that only loads flats for specific coordinates.
     */
    private class FilteredFlatProvider implements de.mhus.nimbus.world.generator.composer.build.FlatProvider {
        private final WFlatService flatService;
        private final String worldId;
        private final String flatIdPrefix;
        private final Set<HexVector2> validCoordinates;

        public FilteredFlatProvider(WFlatService flatService, String worldId, String flatIdPrefix, Set<HexVector2> validCoordinates) {
            this.flatService = flatService;
            this.worldId = worldId;
            this.flatIdPrefix = flatIdPrefix;
            this.validCoordinates = validCoordinates;
        }

        @Override
        public WFlat getFlat(HexVector2 coordinate) {
            // Only load if coordinate is in valid set
            if (!validCoordinates.contains(coordinate)) {
                return null;
            }

            String flatId = flatIdPrefix + coordinate.getQ() + "_" + coordinate.getR();
            log.debug("Loading flat from database: worldId={}, flatId={}", worldId, flatId);

            try {
                return flatService.findByWorldId(worldId).stream()
                        .filter(f -> flatId.equals(f.getFlatId()))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Failed to load flat {}: {}", flatId, e.getMessage());
                return null;
            }
        }

        @Override
        public Collection<HexVector2> getCoordinates() {
            return validCoordinates;
        }
    }
}
