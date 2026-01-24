package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
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
 * Tests BiomeComposer with visualization
 */
@Slf4j
public class BiomeComposerTest {

    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        outputDir = Paths.get("target/test-output/biome-composer");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testSimpleComposition() {
        log.info("=== Testing Simple Biome Composition ===");

        // Create a simple prepared composition
        HexComposition composition = createSimpleComposition();

        // Compose biomes
        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult result = composer.compose(composition, "test-world", 12345L);

        // Verify
        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getPlacedBiomes());
        assertNotNull(result.getHexGrids());

        log.info("Placed {} biomes resulting in {} hexGrids",
            result.getPlacedBiomes().size(),
            result.getHexGrids().size());

        // Log details
        for (PlacedBiome placed : result.getPlacedBiomes()) {
            log.info("Biome '{}': {} hexes at center {}",
                placed.getBiome().getName(),
                placed.getCoordinates().size(),
                placed.getCenter());
        }

        // Visualize
        visualizeComposition(result, "simple-composition");
    }

    @Test
    public void testMultipleBiomesWithDifferentShapes() {
        log.info("=== Testing Multiple Biomes with Different Shapes ===");

        HexComposition composition = createComplexComposition();

        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult result = composer.compose(composition, "test-world", 54321L);

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertEquals(5, result.getPlacedBiomes().size(), "Should place all 5 biomes");

        log.info("Total hexGrids created: {}", result.getHexGrids().size());

        // Visualize
        visualizeComposition(result, "complex-composition");
    }

    @Test
    public void testCollisionDetection() {
        log.info("=== Testing Collision Detection ===");

        // Create composition where biomes might collide
        HexComposition composition = createCollisionTestComposition();

        BiomeComposer composer = new BiomeComposer();
        BiomePlacementResult result = composer.compose(composition, "test-world", 99999L);

        assertTrue(result.isSuccess(), "Should still succeed with retries");
        log.info("Required {} retries to place all biomes", result.getRetries());

        visualizeComposition(result, "collision-test");
    }

    /**
     * Creates a simple composition with 2 biomes
     */
    private HexComposition createSimpleComposition() {
        HexComposition composition = new HexComposition();
        List<Biome> biomes = new ArrayList<>();

        // Biome 1: Forest at origin
        Biome forest = Biome.builder()
            .type(BiomeType.FOREST)
            .parameters(new HashMap<>())
            .build();
        forest.setName("Central Forest");
        forest.setShape(AreaShape.CIRCLE);
        forest.setSizeFrom(3);
        forest.setSizeTo(5);

        // Manually set calculated values for test
        forest.setCalculatedSizeFrom(3);
        forest.setCalculatedSizeTo(5);

        PreparedPosition forestPos = new PreparedPosition();
        forestPos.setDirection(Direction.N);
        forestPos.setDirectionAngle(0);
        forestPos.setDistanceFrom(0);
        forestPos.setDistanceTo(0);
        forestPos.setAnchor("origin");
        forestPos.setPriority(10);

        forest.setPreparedPositions(Arrays.asList(forestPos));
        biomes.add(forest);

        // Biome 2: Mountains to the north
        Biome mountains = Biome.builder()
            .type(BiomeType.MOUNTAINS)
            .parameters(new HashMap<>())
            .build();
        mountains.setName("Northern Mountains");
        mountains.setShape(AreaShape.LINE);
        mountains.setSizeFrom(4);
        mountains.setSizeTo(6);

        // Manually set calculated values for test
        mountains.setCalculatedSizeFrom(4);
        mountains.setCalculatedSizeTo(6);

        PreparedPosition mountainPos = new PreparedPosition();
        mountainPos.setDirection(Direction.N);
        mountainPos.setDirectionAngle(0);
        mountainPos.setDistanceFrom(5);
        mountainPos.setDistanceTo(7);
        mountainPos.setAnchor("origin");
        mountainPos.setPriority(8);

        mountains.setPreparedPositions(Arrays.asList(mountainPos));
        biomes.add(mountains);

        composition.setFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Creates a complex composition with 5 biomes in different directions
     */
    private HexComposition createComplexComposition() {
        HexComposition composition = new HexComposition();
        List<Biome> biomes = new ArrayList<>();

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

        composition.setFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Creates composition with potential collisions
     */
    private HexComposition createCollisionTestComposition() {
        HexComposition composition = new HexComposition();
        List<Biome> biomes = new ArrayList<>();

        // Three biomes with overlapping ranges
        biomes.add(createBiome("Center", BiomeType.PLAINS, AreaShape.CIRCLE,
            5, 7, Direction.N, 0, 0, 0, "origin", 10));

        biomes.add(createBiome("Near 1", BiomeType.FOREST, AreaShape.CIRCLE,
            4, 6, Direction.NE, 60, 3, 5, "origin", 9));

        biomes.add(createBiome("Near 2", BiomeType.MOUNTAINS, AreaShape.CIRCLE,
            4, 6, Direction.E, 120, 3, 5, "origin", 8));

        composition.setFeatures(new ArrayList<>(biomes));
        return composition;
    }

    /**
     * Helper to create a biome and prepare it for composition
     */
    private Biome createBiome(String name, BiomeType type, AreaShape shape,
                              int sizeFrom, int sizeTo,
                              Direction direction, int angle,
                              int distFrom, int distTo,
                              String anchor, int priority) {
        Biome biome = Biome.builder()
            .type(type)
            .parameters(new HashMap<>())
            .build();
        biome.setName(name);
        biome.setShape(shape);
        biome.setSizeFrom(sizeFrom);
        biome.setSizeTo(sizeTo);

        // Create RelativePosition (not PreparedPosition - that's calculated)
        RelativePosition pos = RelativePosition.builder()
            .direction(direction)
            .distanceFrom(distFrom)
            .distanceTo(distTo)
            .anchor(anchor)
            .priority(priority)
            .build();

        biome.setPositions(Arrays.asList(pos));

        // Prepare biome for tests that bypass HexCompositionPreparer
        biome.prepareForComposition();

        return biome;
    }

    /**
     * Visualizes the composition result as a PNG image
     */
    private void visualizeComposition(BiomePlacementResult result, String filename) {
        if (!result.isSuccess() || result.getPlacedBiomes().isEmpty()) {
            log.warn("No biomes to visualize");
            return;
        }

        // Calculate bounds
        int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;

        for (PlacedBiome placed : result.getPlacedBiomes()) {
            for (HexVector2 coord : placed.getCoordinates()) {
                minQ = Math.min(minQ, coord.getQ());
                maxQ = Math.max(maxQ, coord.getQ());
                minR = Math.min(minR, coord.getR());
                maxR = Math.max(maxR, coord.getR());
            }
        }

        // Add padding
        minQ -= 2;
        maxQ += 2;
        minR -= 2;
        maxR += 2;

        int gridWidth = maxQ - minQ + 1;
        int gridHeight = maxR - minR + 1;

        // Hex dimensions
        int hexSize = 30;
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

        // Define colors for biome types
        Map<BiomeType, Color> biomeColors = new HashMap<>();
        biomeColors.put(BiomeType.PLAINS, new Color(144, 238, 144));  // Light green
        biomeColors.put(BiomeType.FOREST, new Color(34, 139, 34));    // Forest green
        biomeColors.put(BiomeType.MOUNTAINS, new Color(139, 137, 137)); // Gray
        biomeColors.put(BiomeType.DESERT, new Color(244, 164, 96));   // Sandy brown
        biomeColors.put(BiomeType.SWAMP, new Color(85, 107, 47));     // Dark olive green
        biomeColors.put(BiomeType.OCEAN, new Color(30, 144, 255));    // Dodger blue
        biomeColors.put(BiomeType.VILLAGE, new Color(160, 82, 45));   // Sienna
        biomeColors.put(BiomeType.TOWN, new Color(178, 34, 34));      // Firebrick

        // Draw hexes
        for (PlacedBiome placed : result.getPlacedBiomes()) {
            Color color = biomeColors.getOrDefault(placed.getBiome().getType(), Color.LIGHT_GRAY);

            for (HexVector2 coord : placed.getCoordinates()) {
                int x = (coord.getQ() - minQ) * hexWidth + (coord.getR() - minR) * hexWidth / 2;
                int y = (coord.getR() - minR) * hexHeight * 3 / 4;

                drawHex(g, x, y, hexSize, color, placed.getBiome().getType().name());
            }
        }

        // Draw origin marker
        int originX = (0 - minQ) * hexWidth + (0 - minR) * hexWidth / 2;
        int originY = (0 - minR) * hexHeight * 3 / 4;
        g.setColor(Color.RED);
        g.fillOval(originX + hexWidth / 2 - 5, originY + hexHeight / 2 - 5, 10, 10);

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
    private void drawHex(Graphics2D g, int x, int y, int size, Color fillColor, String label) {
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

        // Border
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1));
        g.drawPolygon(xPoints, yPoints, 6);

        // Label (first letter of biome type)
        if (label != null && !label.isEmpty()) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            String text = label.substring(0, 1);
            int textX = x + (int) (Math.sqrt(3) * size / 2) - fm.stringWidth(text) / 2;
            int textY = y + size + fm.getAscent() / 2;
            g.drawString(text, textX, textY);
        }
    }
}
