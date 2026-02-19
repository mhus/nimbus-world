package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.flow.Flow;
import de.mhus.nimbus.world.generator.composer.flow.FlowType;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Creates a schematic overview image showing which hex grids are filled with which biomes.
 * Each hex grid is drawn as a colored hexagon using the p_color parameter from its configuration.
 * Labels show the biome name and hex coordinates.
 *
 * Orientation: North (Z-) at top, South (Z+) at bottom, West left, East right.
 *
 * Usage:
 * <pre>
 * HexGridSchemaImageCreator creator = HexGridSchemaImageCreator.builder()
 *     .composition(composition)
 *     .hexGridSize(400)
 *     .outputDirectory("/path/to/output")
 *     .imageName("schema")
 *     .build();
 *
 * SchemaImageResult result = creator.createSchemaImage();
 * </pre>
 */
@Slf4j
@Builder
public class HexGridSchemaImageCreator {

    private static final Color DEFAULT_HEX_COLOR = new Color(128, 128, 128);
    private static final Color GRID_LINE_COLOR = new Color(40, 40, 40);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TEXT_SHADOW_COLOR = new Color(0, 0, 0, 180);
    private static final int PADDING = 40;

    // Flow colors
    private static final Color RIVER_COLOR = new Color(30, 144, 255);
    private static final Color ROAD_COLOR = new Color(205, 133, 63);
    private static final Color WALL_COLOR = new Color(169, 169, 169);
    private static final Color DEFAULT_FLOW_COLOR = new Color(255, 165, 0);

    // Point colors
    private static final Color POINT_COLOR = Color.RED;
    private static final Color POINT_LABEL_COLOR = Color.YELLOW;

    private final HexComposition composition;

    @Builder.Default
    private final int hexGridSize = 400;

    private final String outputDirectory;
    private final String imageName;

    @Builder.Default
    private final boolean drawLabels = true;

    @Builder.Default
    private final boolean drawCoordinates = true;

    @Data
    @Builder
    public static class SchemaImageResult {
        private final BufferedImage image;
        private final int imageWidth;
        private final int imageHeight;
        private final int renderedGridCount;
        private final File outputFile;
        private final boolean success;
        private final String errorMessage;
    }

    public SchemaImageResult createSchemaImage() throws IOException {
        if (composition == null) {
            throw new IllegalStateException("No composition provided");
        }

        List<FeatureHexGrid> hexGrids = composition.getFeatureHexGrids();
        if (hexGrids == null || hexGrids.isEmpty()) {
            throw new IllegalStateException("No featureHexGrids available in composition");
        }

        log.debug("Creating schema image from {} hex grids", hexGrids.size());

        try {
            // Calculate cartesian bounds for all hex grids
            CartesianBounds bounds = calculateCartesianBounds(hexGrids);

            int imageWidth = (bounds.maxX - bounds.minX) + 2 * PADDING;
            int imageHeight = (bounds.maxZ - bounds.minZ) + 2 * PADDING;

            log.debug("Schema image size: {}x{}, bounds x=[{},{}] z=[{},{}]",
                imageWidth, imageHeight, bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ);

            BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

            // Fill background
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, imageWidth, imageHeight);

            // Draw filled hexagons
            int renderedCount = 0;
            for (FeatureHexGrid hexGrid : hexGrids) {
                if (hexGrid.getCoordinate() == null) continue;
                drawFilledHexagon(g, hexGrid, bounds);
                renderedCount++;
            }

            // Draw grid lines on top
            for (FeatureHexGrid hexGrid : hexGrids) {
                if (hexGrid.getCoordinate() == null) continue;
                drawHexagonOutline(g, hexGrid.getCoordinate(), bounds);
            }

            // Draw flows (roads, rivers, walls) as lines between hex centers
            drawFlows(g, bounds);

            // Draw points as cross markers
            drawPoints(g, bounds);

            // Draw labels on top of everything
            if (drawLabels || drawCoordinates) {
                for (FeatureHexGrid hexGrid : hexGrids) {
                    if (hexGrid.getCoordinate() == null) continue;
                    drawHexLabels(g, hexGrid, bounds);
                }
            }

            // Draw compass
            drawCompass(g, imageWidth, imageHeight);

            g.dispose();

            // Save to disk
            File outputFile = null;
            if (outputDirectory != null && imageName != null) {
                outputFile = saveImage(image, outputDirectory, imageName + "-schema.png");
                log.debug("Saved schema image: {} ({}x{} pixels)",
                    outputFile.getAbsolutePath(), imageWidth, imageHeight);
            }

            return SchemaImageResult.builder()
                .image(image)
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .renderedGridCount(renderedCount)
                .outputFile(outputFile)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to create schema image", e);
            return SchemaImageResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private CartesianBounds calculateCartesianBounds(List<FeatureHexGrid> hexGrids) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (FeatureHexGrid hexGrid : hexGrids) {
            HexVector2 coord = hexGrid.getCoordinate();
            if (coord == null) continue;

            int[] cartesian = HexMathUtil.hexToCartesian(coord, hexGridSize);
            int halfSize = hexGridSize / 2;

            minX = Math.min(minX, cartesian[0] - halfSize);
            maxX = Math.max(maxX, cartesian[0] + halfSize);
            minZ = Math.min(minZ, cartesian[1] - halfSize);
            maxZ = Math.max(maxZ, cartesian[1] + halfSize);
        }

        return new CartesianBounds(minX, maxX, minZ, maxZ);
    }

    private void drawFilledHexagon(Graphics2D g, FeatureHexGrid hexGrid, CartesianBounds bounds) {
        HexVector2 coord = hexGrid.getCoordinate();
        int[] cartesian = HexMathUtil.hexToCartesian(coord, hexGridSize);

        // Flip Z: North (Z-) at top of image
        int hexCenterX = cartesian[0] - bounds.minX + PADDING;
        int hexCenterZ = bounds.maxZ - cartesian[1] + PADDING;

        Color fillColor = parseColor(hexGrid);
        Polygon hexagon = createHexagonPolygon(hexCenterX, hexCenterZ, hexGridSize);

        g.setColor(fillColor);
        g.fillPolygon(hexagon);
    }

    private void drawHexagonOutline(Graphics2D g, HexVector2 coord, CartesianBounds bounds) {
        int[] cartesian = HexMathUtil.hexToCartesian(coord, hexGridSize);

        int hexCenterX = cartesian[0] - bounds.minX + PADDING;
        int hexCenterZ = bounds.maxZ - cartesian[1] + PADDING;

        Polygon hexagon = createHexagonPolygon(hexCenterX, hexCenterZ, hexGridSize);
        g.setColor(GRID_LINE_COLOR);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(hexagon);
    }

    private void drawHexLabels(Graphics2D g, FeatureHexGrid hexGrid, CartesianBounds bounds) {
        HexVector2 coord = hexGrid.getCoordinate();
        int[] cartesian = HexMathUtil.hexToCartesian(coord, hexGridSize);

        int hexCenterX = cartesian[0] - bounds.minX + PADDING;
        int hexCenterZ = bounds.maxZ - cartesian[1] + PADDING;

        // Scale font based on hex grid size
        int fontSize = Math.max(10, hexGridSize / 25);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int lineY = hexCenterZ;

        // Draw biome name
        if (drawLabels) {
            String biomeName = getBiomeLabel(hexGrid);
            if (biomeName != null) {
                int textWidth = fm.stringWidth(biomeName);
                int textX = hexCenterX - textWidth / 2;
                int textY = lineY - fm.getHeight() / 2;
                drawTextWithShadow(g, biomeName, textX, textY, fm);
                lineY += fm.getHeight();
            }
        }

        // Draw coordinates
        if (drawCoordinates) {
            Font smallFont = new Font("SansSerif", Font.PLAIN, Math.max(9, fontSize - 2));
            g.setFont(smallFont);
            FontMetrics smallFm = g.getFontMetrics();

            String coordText = coord.getQ() + ";" + coord.getR();
            int textWidth = smallFm.stringWidth(coordText);
            int textX = hexCenterX - textWidth / 2;
            drawTextWithShadow(g, coordText, textX, lineY, smallFm);

            g.setFont(font);
        }
    }

    private void drawTextWithShadow(Graphics2D g, String text, int x, int y, FontMetrics fm) {
        // Shadow
        g.setColor(TEXT_SHADOW_COLOR);
        g.drawString(text, x + 1, y + 1);
        // Text
        g.setColor(TEXT_COLOR);
        g.drawString(text, x, y);
    }

    private void drawCompass(Graphics2D g, int imageWidth, int imageHeight) {
        int fontSize = Math.max(12, hexGridSize / 20);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        // North at top center
        String north = "N";
        g.setColor(TEXT_SHADOW_COLOR);
        g.drawString(north, imageWidth / 2 - fm.stringWidth(north) / 2 + 1, fontSize + 5 + 1);
        g.setColor(Color.YELLOW);
        g.drawString(north, imageWidth / 2 - fm.stringWidth(north) / 2, fontSize + 5);

        // South at bottom center
        String south = "S";
        g.setColor(TEXT_SHADOW_COLOR);
        g.drawString(south, imageWidth / 2 - fm.stringWidth(south) / 2 + 1, imageHeight - 5 + 1);
        g.setColor(Color.YELLOW);
        g.drawString(south, imageWidth / 2 - fm.stringWidth(south) / 2, imageHeight - 5);

        // West at left center
        String west = "W";
        g.setColor(TEXT_SHADOW_COLOR);
        g.drawString(west, 5 + 1, imageHeight / 2 + fm.getAscent() / 2 + 1);
        g.setColor(Color.YELLOW);
        g.drawString(west, 5, imageHeight / 2 + fm.getAscent() / 2);

        // East at right center
        String east = "E";
        g.setColor(TEXT_SHADOW_COLOR);
        g.drawString(east, imageWidth - fm.stringWidth(east) - 5 + 1, imageHeight / 2 + fm.getAscent() / 2 + 1);
        g.setColor(Color.YELLOW);
        g.drawString(east, imageWidth - fm.stringWidth(east) - 5, imageHeight / 2 + fm.getAscent() / 2);
    }

    /**
     * Draws all flows (roads, rivers, walls) from the composition as lines along their routes.
     */
    private void drawFlows(Graphics2D g, CartesianBounds bounds) {
        if (composition.getFeatures() == null) return;

        List<Flow> flows = composition.getFeatures().stream()
                .filter(f -> f instanceof Flow)
                .map(f -> (Flow) f)
                .filter(f -> f.getFlowComposed() != null && f.getFlowComposed().getRoute() != null)
                .toList();

        if (flows.isEmpty()) return;

        log.debug("Drawing {} flows on schema image", flows.size());

        Stroke originalStroke = g.getStroke();

        for (Flow flow : flows) {
            List<HexVector2> route = flow.getFlowComposed().getRoute();
            if (route.size() < 2) continue;

            Color flowColor = getFlowColor(flow.getType());
            float lineWidth = getFlowLineWidth(flow.getType());

            g.setColor(flowColor);
            g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Draw route as connected line segments between hex centers
            for (int i = 0; i < route.size() - 1; i++) {
                int[] from = hexToImageCoords(route.get(i), bounds);
                int[] to = hexToImageCoords(route.get(i + 1), bounds);
                g.drawLine(from[0], from[1], to[0], to[1]);
            }

            // Draw flow name at midpoint of route
            HexVector2 midCoord = route.get(route.size() / 2);
            int[] mid = hexToImageCoords(midCoord, bounds);

            int fontSize = Math.max(9, hexGridSize / 30);
            Font flowFont = new Font("SansSerif", Font.ITALIC, fontSize);
            g.setFont(flowFont);
            FontMetrics fm = g.getFontMetrics();

            String flowLabel = flow.getName() != null ? flow.getName() : flow.getType().name();
            int textWidth = fm.stringWidth(flowLabel);
            // Offset label slightly above the line
            int labelY = mid[1] - fontSize;

            g.setColor(TEXT_SHADOW_COLOR);
            g.drawString(flowLabel, mid[0] - textWidth / 2 + 1, labelY + 1);
            g.setColor(flowColor);
            g.drawString(flowLabel, mid[0] - textWidth / 2, labelY);
        }

        g.setStroke(originalStroke);
        log.debug("Drew {} flows", flows.size());
    }

    /**
     * Draws all points from the composition as cross markers at their grid coordinates.
     */
    private void drawPoints(Graphics2D g, CartesianBounds bounds) {
        if (composition.getFeatures() == null) return;

        List<Point> points = composition.getFeatures().stream()
                .filter(f -> f instanceof Point)
                .map(f -> (Point) f)
                .filter(p -> p.getPointComposed() != null && p.getPointComposed().getGridCoordinate() != null)
                .toList();

        if (points.isEmpty()) return;

        log.debug("Drawing {} points on schema image", points.size());

        int crossSize = Math.max(8, hexGridSize / 20);
        float crossWidth = Math.max(2.0f, hexGridSize / 100.0f);
        Stroke originalStroke = g.getStroke();

        for (Point point : points) {
            HexVector2 gridCoord = point.getPointComposed().getGridCoordinate();
            int[] center = hexToImageCoords(gridCoord, bounds);

            // Draw cross
            g.setColor(POINT_COLOR);
            g.setStroke(new BasicStroke(crossWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(center[0] - crossSize, center[1], center[0] + crossSize, center[1]);
            g.drawLine(center[0], center[1] - crossSize, center[0], center[1] + crossSize);

            // Draw point name
            int fontSize = Math.max(9, hexGridSize / 30);
            Font pointFont = new Font("SansSerif", Font.BOLD, fontSize);
            g.setFont(pointFont);
            FontMetrics fm = g.getFontMetrics();

            String pointLabel = point.getName() != null ? point.getName() : "point";
            int textWidth = fm.stringWidth(pointLabel);
            int labelY = center[1] + crossSize + fm.getAscent() + 2;

            g.setColor(TEXT_SHADOW_COLOR);
            g.drawString(pointLabel, center[0] - textWidth / 2 + 1, labelY + 1);
            g.setColor(POINT_LABEL_COLOR);
            g.drawString(pointLabel, center[0] - textWidth / 2, labelY);
        }

        g.setStroke(originalStroke);
        log.debug("Drew {} points", points.size());
    }

    /**
     * Converts a hex coordinate to image pixel coordinates (with Z-flip and padding).
     */
    private int[] hexToImageCoords(HexVector2 coord, CartesianBounds bounds) {
        int[] cartesian = HexMathUtil.hexToCartesian(coord, hexGridSize);
        int imgX = cartesian[0] - bounds.minX + PADDING;
        int imgZ = bounds.maxZ - cartesian[1] + PADDING;
        return new int[]{imgX, imgZ};
    }

    private Color getFlowColor(FlowType type) {
        if (type == null) return DEFAULT_FLOW_COLOR;
        return switch (type) {
            case RIVER -> RIVER_COLOR;
            case ROAD -> ROAD_COLOR;
            case WALL, SIDEWALL -> WALL_COLOR;
        };
    }

    private float getFlowLineWidth(FlowType type) {
        float base = Math.max(3.0f, hexGridSize / 60.0f);
        if (type == null) return base;
        return switch (type) {
            case RIVER -> base * 1.5f;
            case ROAD -> base;
            case WALL, SIDEWALL -> base * 0.8f;
        };
    }

    private Color parseColor(FeatureHexGrid hexGrid) {
        if (hexGrid.getParameters() == null) {
            return DEFAULT_HEX_COLOR;
        }
        String colorStr = hexGrid.getParameters().get("p_color");
        if (colorStr == null || colorStr.isBlank()) {
            return DEFAULT_HEX_COLOR;
        }
        try {
            return Color.decode(colorStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid color '{}' for hex grid at {}, using default", colorStr, hexGrid.getPositionKey());
            return DEFAULT_HEX_COLOR;
        }
    }

    private String getBiomeLabel(FeatureHexGrid hexGrid) {
        // Prefer sourceBiomeName, fallback to biomeType parameter, then name
        if (hexGrid.getSourceBiomeName() != null && !hexGrid.getSourceBiomeName().isBlank()) {
            return hexGrid.getSourceBiomeName();
        }
        if (hexGrid.getParameters() != null) {
            String biomeType = hexGrid.getParameters().get("biomeType");
            if (biomeType != null && !biomeType.isBlank()) {
                return biomeType;
            }
        }
        return hexGrid.getName();
    }

    /**
     * Creates a pointy-top hexagon polygon, matching HexGridCompositeImageCreator geometry.
     */
    private Polygon createHexagonPolygon(double centerX, double centerZ, int gridSize) {
        double radius = gridSize / 2.0;
        int[] xPoints = new int[6];
        int[] zPoints = new int[6];

        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            xPoints[i] = (int) Math.floor(centerX + radius * Math.cos(angle));
            zPoints[i] = (int) Math.floor(centerZ + radius * Math.sin(angle));
        }

        return new Polygon(xPoints, zPoints, 6);
    }

    private File saveImage(BufferedImage image, String directory, String filename) throws IOException {
        File dir = new File(directory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File outputFile = new File(dir, filename);
        ImageIO.write(image, "PNG", outputFile);
        return outputFile;
    }

    private record CartesianBounds(int minX, int maxX, int minZ, int maxZ) {}
}
