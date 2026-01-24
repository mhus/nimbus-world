package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests HexGridFiller with visualization
 */
@Slf4j
public class HexGridFillerTest {

    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        outputDir = Paths.get("target/test-output/hex-grid-filler");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testSimpleFilling() {
        log.info("=== Testing Simple Hex Grid Filling ===");

        // Create and place biomes
        PreparedHexComposition composition = createSimpleComposition();
        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult placementResult = composer.compose(composition, "test-world", 12345L);

        assertTrue(placementResult.isSuccess());

        // Fill gaps
        HexGridFiller filler = new HexGridFiller();
        HexGridFillResult fillResult = filler.fill(placementResult, "test-world");

        // Verify
        assertTrue(fillResult.isSuccess());
        assertNotNull(fillResult.getAllGrids());
        assertTrue(fillResult.getTotalGridCount() > placementResult.getHexGrids().size());

        log.info("Total grids: {}, Ocean: {}, Land: {}, Coast: {}",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Visualize
        visualizeFillResult(fillResult, "simple-fill");
    }

    @Test
    public void testComplexFillingWithOceanRing() {
        log.info("=== Testing Complex Filling with Ocean Ring ===");

        // Create complex composition
        PreparedHexComposition composition = createComplexComposition();
        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult placementResult = composer.compose(composition, "test-world", 54321L);

        assertTrue(placementResult.isSuccess());

        // Fill with wider ocean ring
        HexGridFiller filler = new HexGridFiller();
        HexGridFillResult fillResult = filler.fill(placementResult, "test-world", 2);

        assertTrue(fillResult.isSuccess());

        log.info("Biome hexes: {}, Filled hexes: {}, Total: {}",
            placementResult.getHexGrids().size(),
            fillResult.getOceanFillCount() + fillResult.getLandFillCount() + fillResult.getCoastFillCount(),
            fillResult.getTotalGridCount());

        // Visualize
        visualizeFillResult(fillResult, "complex-fill-ring2");
    }

    @Test
    public void testFillingWithLandAndOceanBiomes() {
        log.info("=== Testing Filling with Land and Ocean Biomes ===");

        // Create composition with ocean biomes
        PreparedHexComposition composition = createMixedComposition();
        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult placementResult = composer.compose(composition, "test-world", 99999L);

        assertTrue(placementResult.isSuccess());

        // Fill
        HexGridFiller filler = new HexGridFiller();
        HexGridFillResult fillResult = filler.fill(placementResult, "test-world", 1);

        assertTrue(fillResult.isSuccess());

        log.info("Ocean fill: {}, Land fill: {}, Coast fill: {}",
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Coast should exist between ocean and land
        assertTrue(fillResult.getCoastFillCount() > 0, "Should have coast grids");

        // Visualize
        visualizeFillResult(fillResult, "mixed-fill");
    }

    /**
     * Creates a simple composition
     */
    private PreparedHexComposition createSimpleComposition() {
        PreparedHexComposition composition = new PreparedHexComposition();
        List<PreparedBiome> biomes = new ArrayList<>();

        // Forest at origin
        biomes.add(createBiome("Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            3, 5, Direction.N, 0, 0, 0, "origin", 10));

        // Mountains to north
        biomes.add(createBiome("Mountains", BiomeType.MOUNTAINS, AreaShape.LINE,
            4, 6, Direction.N, 0, 5, 7, "origin", 9));

        composition.setPreparedFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Creates a complex composition with 5 biomes
     */
    private PreparedHexComposition createComplexComposition() {
        PreparedHexComposition composition = new PreparedHexComposition();
        List<PreparedBiome> biomes = new ArrayList<>();

        // Center: Plains
        biomes.add(createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            3, 5, Direction.N, 0, 0, 0, "origin", 10));

        // North: Mountains
        biomes.add(createBiome("North Mountains", BiomeType.MOUNTAINS, AreaShape.LINE,
            5, 7, Direction.N, 0, 6, 8, "origin", 9));

        // East: Forest
        biomes.add(createBiome("East Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            4, 6, Direction.E, 120, 6, 8, "origin", 8));

        // South: Swamp
        biomes.add(createBiome("South Swamp", BiomeType.SWAMP, AreaShape.CIRCLE,
            3, 5, Direction.S, 180, 6, 8, "origin", 7));

        // West: Desert
        biomes.add(createBiome("West Desert", BiomeType.DESERT, AreaShape.CIRCLE,
            4, 5, Direction.W, 300, 6, 8, "origin", 6));

        composition.setPreparedFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Creates composition with ocean biomes
     */
    private PreparedHexComposition createMixedComposition() {
        PreparedHexComposition composition = new PreparedHexComposition();
        List<PreparedBiome> biomes = new ArrayList<>();

        // Center: Plains
        biomes.add(createBiome("Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            4, 6, Direction.N, 0, 0, 0, "origin", 10));

        // East: Forest
        biomes.add(createBiome("Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            3, 5, Direction.E, 120, 4, 6, "origin", 9));

        // West: Ocean
        biomes.add(createBiome("Ocean", BiomeType.OCEAN, AreaShape.CIRCLE,
            4, 6, Direction.W, 300, 4, 6, "origin", 8));

        composition.setPreparedFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Helper to create a prepared biome
     */
    private PreparedBiome createBiome(String name, BiomeType type, AreaShape shape,
                                      int sizeFrom, int sizeTo,
                                      Direction direction, int angle,
                                      int distFrom, int distTo,
                                      String anchor, int priority) {
        PreparedBiome biome = new PreparedBiome();
        biome.setName(name);
        biome.setType(type);
        biome.setShape(shape);
        biome.setSizeFrom(sizeFrom);
        biome.setSizeTo(sizeTo);

        PreparedPosition pos = new PreparedPosition();
        pos.setDirection(direction);
        pos.setDirectionAngle(angle);
        pos.setDistanceFrom(distFrom);
        pos.setDistanceTo(distTo);
        pos.setAnchor(anchor);
        pos.setPriority(priority);

        biome.setPositions(Arrays.asList(pos));
        biome.setParameters(new HashMap<>());

        return biome;
    }

    /**
     * Visualizes the fill result
     */
    private void visualizeFillResult(HexGridFillResult result, String filename) {
        if (!result.isSuccess() || result.getAllGrids().isEmpty()) {
            log.warn("No grids to visualize");
            return;
        }

        // Calculate bounds
        int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;

        for (FilledHexGrid filled : result.getAllGrids()) {
            HexVector2 coord = filled.getCoordinate();
            minQ = Math.min(minQ, coord.getQ());
            maxQ = Math.max(maxQ, coord.getQ());
            minR = Math.min(minR, coord.getR());
            maxR = Math.max(maxR, coord.getR());
        }

        int gridWidth = maxQ - minQ + 1;
        int gridHeight = maxR - minR + 1;

        // Hex dimensions
        int hexSize = 20;
        int hexWidth = (int) (Math.sqrt(3) * hexSize);
        int hexHeight = 2 * hexSize;

        int imageWidth = gridWidth * hexWidth + hexWidth / 2;
        int imageHeight = gridHeight * hexHeight * 3 / 4 + hexHeight / 4;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imageWidth, imageHeight);

        // Define colors
        Map<BiomeType, Color> biomeColors = new HashMap<>();
        biomeColors.put(BiomeType.PLAINS, new Color(144, 238, 144));    // Light green
        biomeColors.put(BiomeType.FOREST, new Color(34, 139, 34));      // Forest green
        biomeColors.put(BiomeType.MOUNTAINS, new Color(139, 137, 137)); // Gray
        biomeColors.put(BiomeType.DESERT, new Color(244, 164, 96));     // Sandy brown
        biomeColors.put(BiomeType.SWAMP, new Color(85, 107, 47));       // Dark olive green
        biomeColors.put(BiomeType.OCEAN, new Color(30, 144, 255));      // Dodger blue
        biomeColors.put(BiomeType.COAST, new Color(135, 206, 250));     // Light sky blue
        biomeColors.put(BiomeType.VILLAGE, new Color(160, 82, 45));     // Sienna
        biomeColors.put(BiomeType.TOWN, new Color(178, 34, 34));        // Firebrick

        // Filler colors (lighter/transparent)
        Map<FillerType, Color> fillerColors = new HashMap<>();
        fillerColors.put(FillerType.OCEAN, new Color(173, 216, 230));   // Light blue
        fillerColors.put(FillerType.LAND, new Color(240, 255, 240));    // Honeydew (very light green)
        fillerColors.put(FillerType.COAST, new Color(245, 245, 220));   // Beige

        // Draw hexes
        for (FilledHexGrid filled : result.getAllGrids()) {
            HexVector2 coord = filled.getCoordinate();
            int x = (coord.getQ() - minQ) * hexWidth + (coord.getR() - minR) * hexWidth / 2;
            int y = (coord.getR() - minR) * hexHeight * 3 / 4;

            Color color;
            String label;

            if (filled.isFiller()) {
                // Filler grid
                color = fillerColors.get(filled.getFillerType());
                label = filled.getFillerType().name().substring(0, 1);
            } else {
                // Biome grid
                BiomeType biomeType = filled.getBiome().getBiome().getType();
                color = biomeColors.getOrDefault(biomeType, Color.LIGHT_GRAY);
                label = biomeType.name().substring(0, 1);
            }

            drawHex(g, x, y, hexSize, color, label, filled.isFiller());
        }

        // Draw origin marker
        int originX = (0 - minQ) * hexWidth + (0 - minR) * hexWidth / 2;
        int originY = (0 - minR) * hexHeight * 3 / 4;
        g.setColor(Color.RED);
        g.fillOval(originX + hexWidth / 2 - 3, originY + hexHeight / 2 - 3, 6, 6);

        // Draw legend
        drawLegend(g, imageWidth, imageHeight, biomeColors, fillerColors);

        g.dispose();

        // Save image
        try {
            File outputFile = outputDir.resolve(filename + ".png").toFile();
            ImageIO.write(image, "PNG", outputFile);
            log.info("Saved visualization: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save visualization", e);
        }
    }

    /**
     * Draws a single hexagon
     */
    private void drawHex(Graphics2D g, int x, int y, int size, Color fillColor, String label, boolean isFiller) {
        int[] xPoints = new int[6];
        int[] yPoints = new int[6];

        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i - Math.PI / 6;
            xPoints[i] = x + (int) (Math.sqrt(3) * size / 2) + (int) (size * Math.cos(angle));
            yPoints[i] = y + size + (int) (size * Math.sin(angle));
        }

        // Fill
        g.setColor(fillColor);
        g.fillPolygon(xPoints, yPoints, 6);

        // Border (thicker for biomes, thinner for fillers)
        g.setColor(isFiller ? Color.LIGHT_GRAY : Color.BLACK);
        g.setStroke(new BasicStroke(isFiller ? 0.5f : 1.5f));
        g.drawPolygon(xPoints, yPoints, 6);

        // Label
        if (label != null && !label.isEmpty()) {
            g.setColor(isFiller ? Color.GRAY : Color.BLACK);
            g.setFont(new Font("Arial", isFiller ? Font.PLAIN : Font.BOLD, 8));
            FontMetrics fm = g.getFontMetrics();
            int textX = x + (int) (Math.sqrt(3) * size / 2) - fm.stringWidth(label) / 2;
            int textY = y + size + fm.getAscent() / 2;
            g.drawString(label, textX, textY);
        }
    }

    /**
     * Draws legend
     */
    private void drawLegend(Graphics2D g, int imageWidth, int imageHeight,
                            Map<BiomeType, Color> biomeColors,
                            Map<FillerType, Color> fillerColors) {
        int legendX = 10;
        int legendY = imageHeight - 80;

        g.setColor(new Color(255, 255, 255, 200));
        g.fillRoundRect(legendX - 5, legendY - 5, 150, 75, 10, 10);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("Biomes (bold border)", legendX, legendY + 10);
        g.drawString("Fillers (thin border)", legendX, legendY + 30);

        g.setFont(new Font("Arial", Font.PLAIN, 9));
        g.drawString("O=Ocean L=Land C=Coast", legendX, legendY + 50);
        g.drawString("Red dot = Origin", legendX, legendY + 65);
    }
}
