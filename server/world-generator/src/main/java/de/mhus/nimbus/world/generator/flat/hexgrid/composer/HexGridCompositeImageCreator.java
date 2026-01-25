package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.generator.FlatLevelImageCreator;
import de.mhus.nimbus.world.shared.generator.FlatMaterialImageCreator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Creates composite images from multiple hex grids.
 * Combines individual hex grid terrain images into a single large composite image,
 * respecting hexagonal geometry and positioning.
 *
 * Usage:
 * <pre>
 * HexGridCompositeImageCreator creator = HexGridCompositeImageCreator.builder()
 *     .flats(flatsMap)
 *     .flatSize(512)
 *     .outputDirectory("/path/to/output")
 *     .imageName("world-composite")
 *     .drawGridLines(true)
 *     .build();
 *
 * CompositeImageResult result = creator.createCompositeImages();
 * </pre>
 */
@Slf4j
@Builder
public class HexGridCompositeImageCreator {

    /**
     * Map of hex coordinates to WFlat terrain data.
     * Each WFlat contains the terrain levels and materials for a single hex grid.
     */
    private final Map<HexVector2, WFlat> flats;

    /**
     * Size of each flat/hex grid in pixels (typically 512).
     */
    @Builder.Default
    private final int flatSize = 512;

    /**
     * Output directory where images will be saved.
     * If null, images are created but not saved to disk.
     */
    private final String outputDirectory;

    /**
     * Base name for output images (e.g., "world-composite").
     * Will create "{name}-level.png" and "{name}-material.png".
     */
    private final String imageName;

    /**
     * Whether to draw hexagon grid lines on the composite image.
     */
    @Builder.Default
    private final boolean drawGridLines = true;

    /**
     * Grid line color (default: semi-transparent white).
     */
    @Builder.Default
    private final Color gridLineColor = new Color(255, 255, 255, 60);

    /**
     * Grid line stroke width.
     */
    @Builder.Default
    private final float gridLineWidth = 2.0f;

    /**
     * Result of composite image creation.
     */
    @Data
    @Builder
    public static class CompositeImageResult {
        private final BufferedImage levelImage;
        private final BufferedImage materialImage;
        private final int imageWidth;
        private final int imageHeight;
        private final int renderedGridCount;
        private final int totalGridCount;
        private final File levelFile;
        private final File materialFile;
        private final boolean success;
        private final String errorMessage;
    }

    /**
     * Creates composite level and material images from the hex grids.
     *
     * @return Result containing the created images and metadata
     * @throws IOException if image creation or saving fails
     */
    public CompositeImageResult createCompositeImages() throws IOException {
        if (flats == null || flats.isEmpty()) {
            throw new IllegalStateException("No flats provided - cannot create composite image");
        }

        log.info("Creating composite images from {} hex grids", flats.size());

        try {
            // Calculate hex coordinate bounds
            HexBounds bounds = calculateHexBounds();

            log.info("Creating HEX composite: {}x{} grids, bounds q=[{},{}] r=[{},{}]",
                bounds.gridWidth, bounds.gridHeight, bounds.minQ, bounds.maxQ, bounds.minR, bounds.maxR);

            // Calculate cartesian bounds using HexMathUtil
            CartesianBounds cartBounds = calculateCartesianBounds();

            int imageWidth = (int) Math.ceil(cartBounds.maxX - cartBounds.minX);
            int imageHeight = (int) Math.ceil(cartBounds.maxZ - cartBounds.minZ);

            log.info("HEX composite cartesian bounds: x=[{},{}] z=[{},{}], image size={}x{}",
                (int)cartBounds.minX, (int)cartBounds.maxX, (int)cartBounds.minZ, (int)cartBounds.maxZ,
                imageWidth, imageHeight);

            // Create blank images
            BufferedImage levelImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            BufferedImage materialImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

            // Fill with black background
            fillBackground(levelImage, Color.BLACK);
            fillBackground(materialImage, Color.BLACK);

            // Render each hex grid onto the composite
            int renderedCount = renderHexGrids(levelImage, materialImage, cartBounds);

            log.info("Rendered {} of {} grids with HEX geometry", renderedCount, flats.size());

            // Draw grid lines if enabled
            if (drawGridLines) {
                drawHexagonGridLines(levelImage, cartBounds);
                drawHexagonGridLines(materialImage, cartBounds);
            }

            // Save to disk if output directory specified
            File levelFile = null;
            File materialFile = null;

            if (outputDirectory != null && imageName != null) {
                levelFile = saveImage(levelImage, outputDirectory, imageName + "-level.png");
                materialFile = saveImage(materialImage, outputDirectory, imageName + "-material.png");

                log.info("Saved composite level image: {} ({}x{} pixels)",
                    levelFile.getAbsolutePath(), imageWidth, imageHeight);
                log.info("Saved composite material image: {} ({}x{} pixels)",
                    materialFile.getAbsolutePath(), imageWidth, imageHeight);
            }

            return CompositeImageResult.builder()
                .levelImage(levelImage)
                .materialImage(materialImage)
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .renderedGridCount(renderedCount)
                .totalGridCount(flats.size())
                .levelFile(levelFile)
                .materialFile(materialFile)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to create composite images", e);
            return CompositeImageResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Calculates hex coordinate bounds (min/max Q and R).
     */
    private HexBounds calculateHexBounds() {
        int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;

        for (HexVector2 coord : flats.keySet()) {
            minQ = Math.min(minQ, coord.getQ());
            maxQ = Math.max(maxQ, coord.getQ());
            minR = Math.min(minR, coord.getR());
            maxR = Math.max(maxR, coord.getR());
        }

        return new HexBounds(minQ, maxQ, minR, maxR);
    }

    /**
     * Calculates cartesian bounds for all hex grids.
     */
    private CartesianBounds calculateCartesianBounds() {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;

        for (HexVector2 coord : flats.keySet()) {
            double[] cartesian = HexMathUtil.hexToCartesian(coord, flatSize);
            double halfSize = flatSize / 2.0;

            minX = Math.min(minX, cartesian[0] - halfSize);
            maxX = Math.max(maxX, cartesian[0] + halfSize);
            minZ = Math.min(minZ, cartesian[1] - halfSize);
            maxZ = Math.max(maxZ, cartesian[1] + halfSize);
        }

        return new CartesianBounds(minX, maxX, minZ, maxZ);
    }

    /**
     * Fills an image with a solid color.
     */
    private void fillBackground(BufferedImage image, Color color) {
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
    }

    /**
     * Renders all hex grids onto the composite images.
     *
     * @return Number of successfully rendered grids
     */
    private int renderHexGrids(BufferedImage levelImage, BufferedImage materialImage,
                               CartesianBounds bounds) throws IOException {
        int renderedCount = 0;

        for (Map.Entry<HexVector2, WFlat> entry : flats.entrySet()) {
            HexVector2 coord = entry.getKey();
            WFlat flat = entry.getValue();

            try {
                renderSingleHexGrid(levelImage, materialImage, coord, flat, bounds);
                renderedCount++;
            } catch (Exception e) {
                log.warn("Failed to render grid [{},{}]: {}", coord.getQ(), coord.getR(), e.getMessage());
            }
        }

        return renderedCount;
    }

    /**
     * Renders a single hex grid onto the composite images.
     * Only renders pixels that fall inside the hexagon boundary.
     */
    private void renderSingleHexGrid(BufferedImage levelImage, BufferedImage materialImage,
                                     HexVector2 coord, WFlat flat,
                                     CartesianBounds bounds) throws IOException {
        // Calculate cartesian center position
        double[] cartesian = HexMathUtil.hexToCartesian(coord, flatSize);
        double hexCenterX = cartesian[0] - bounds.minX;
        double hexCenterZ = cartesian[1] - bounds.minZ;

        // Create flat images
        FlatLevelImageCreator levelCreator = new FlatLevelImageCreator(flat);
        byte[] levelBytes = levelCreator.create(false);
        BufferedImage flatLevelImage = ImageIO.read(new ByteArrayInputStream(levelBytes));

        FlatMaterialImageCreator materialCreator = new FlatMaterialImageCreator(flat);
        byte[] materialBytes = materialCreator.create(false);
        BufferedImage flatMaterialImage = ImageIO.read(new ByteArrayInputStream(materialBytes));

        // Render only pixels inside the hexagon
        int halfSize = flatSize / 2;
        int startX = Math.max(0, (int)(hexCenterX - halfSize));
        int endX = Math.min(levelImage.getWidth(), (int)(hexCenterX + halfSize));
        int startZ = Math.max(0, (int)(hexCenterZ - halfSize));
        int endZ = Math.min(levelImage.getHeight(), (int)(hexCenterZ + halfSize));

        for (int z = startZ; z < endZ; z++) {
            for (int x = startX; x < endX; x++) {
                // Check if this pixel is inside the hexagon
                if (HexMathUtil.isPointInHex(x, z, hexCenterX, hexCenterZ, flatSize)) {
                    // Calculate source pixel coordinates in flat image
                    int flatX = (int)(x - hexCenterX + halfSize);
                    int flatZ = (int)(z - hexCenterZ + halfSize);

                    if (flatX >= 0 && flatX < flatSize && flatZ >= 0 && flatZ < flatSize) {
                        // Copy pixel from flat image to composite
                        int levelPixel = flatLevelImage.getRGB(flatX, flatZ);
                        int materialPixel = flatMaterialImage.getRGB(flatX, flatZ);

                        levelImage.setRGB(x, z, levelPixel);
                        materialImage.setRGB(x, z, materialPixel);
                    }
                }
            }
        }
    }

    /**
     * Draws hexagon grid lines on the composite image.
     */
    private void drawHexagonGridLines(BufferedImage image, CartesianBounds bounds) {
        Graphics2D g = image.createGraphics();
        g.setColor(gridLineColor);
        g.setStroke(new BasicStroke(gridLineWidth));

        for (HexVector2 coord : flats.keySet()) {
            double[] cartesian = HexMathUtil.hexToCartesian(coord, flatSize);
            double hexCenterX = cartesian[0] - bounds.minX;
            double hexCenterZ = cartesian[1] - bounds.minZ;

            // Draw hexagon outline
            Polygon hexagon = createHexagonPolygon(hexCenterX, hexCenterZ, flatSize);
            g.draw(hexagon);
        }

        g.dispose();
    }

    /**
     * Creates a hexagon polygon for drawing grid lines.
     *
     * @param centerX Center X coordinate in image space
     * @param centerZ Center Z coordinate in image space
     * @param gridSize Size of the hex grid
     * @return Polygon representing the hexagon outline
     */
    private Polygon createHexagonPolygon(double centerX, double centerZ, int gridSize) {
        double radius = gridSize / 2.0;
        int[] xPoints = new int[6];
        int[] zPoints = new int[6];

        // Create 6 vertices at 60-degree intervals, starting at -30 degrees
        // This creates a pointy-top hexagon (point facing up)
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            xPoints[i] = (int) Math.round(centerX + radius * Math.cos(angle));
            zPoints[i] = (int) Math.round(centerZ + radius * Math.sin(angle));
        }

        return new Polygon(xPoints, zPoints, 6);
    }

    /**
     * Saves an image to disk.
     */
    private File saveImage(BufferedImage image, String directory, String filename) throws IOException {
        File dir = new File(directory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File outputFile = new File(dir, filename);
        ImageIO.write(image, "PNG", outputFile);
        return outputFile;
    }

    // Helper classes for bounds

    private static class HexBounds {
        final int minQ, maxQ, minR, maxR;
        final int gridWidth, gridHeight;

        HexBounds(int minQ, int maxQ, int minR, int maxR) {
            this.minQ = minQ;
            this.maxQ = maxQ;
            this.minR = minR;
            this.maxR = maxR;
            this.gridWidth = maxQ - minQ + 1;
            this.gridHeight = maxR - minR + 1;
        }
    }

    private static class CartesianBounds {
        final double minX, maxX, minZ, maxZ;

        CartesianBounds(double minX, double maxX, double minZ, double maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
