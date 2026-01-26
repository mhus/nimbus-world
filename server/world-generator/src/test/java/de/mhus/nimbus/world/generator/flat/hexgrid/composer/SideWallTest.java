package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SideWall (wall around a biome)
 */
@Slf4j
public class SideWallTest {

    @Test
    public void testSideWallAroundBiome() {
        log.info("=== Testing SideWall Around Biome ===");

        // Create composition with biome and sidewall
        HexComposition composition = createCompositionWithSideWall();

        // Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        boolean prepared = preparer.prepare(composition);
        assertTrue(prepared, "Composition should prepare successfully");

        // Compose biomes
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult biomePlacementResult = biomeComposer.compose(composition, "test-world", 12345L);
        assertTrue(biomePlacementResult.isSuccess(), "Biome composition should succeed");
        log.info("Placed {} biomes", biomePlacementResult.getPlacedBiomes().size());

        // Compose flows (including sidewall)
        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);
        assertTrue(flowResult.isSuccess(), "Flow composition should succeed");
        log.info("Composed {} flows", flowResult.getComposedFlows());

        // Verify sidewall was configured
        SideWall sideWall = findSideWall(composition, "city-wall");
        assertNotNull(sideWall, "Should find sidewall");
        assertEquals(FeatureStatus.COMPOSED, sideWall.getStatus(), "SideWall should be COMPOSED");

        // Verify biome has edge grids with sidewall parameter
        Biome city = findBiome(composition, "city");
        assertNotNull(city, "Should find city biome");
        assertNotNull(city.getHexGrids(), "City should have hex grids");
        assertFalse(city.getHexGrids().isEmpty(), "City should have hex grids");

        int gridsWithSidewall = 0;
        for (FeatureHexGrid grid : city.getHexGrids()) {
            String sidewallParam = grid.getParameters().get("g_sidewall");
            if (sidewallParam != null && !sidewallParam.isBlank()) {
                gridsWithSidewall++;
                log.debug("Grid {} has sidewall: {}", grid.getPositionKey(), sidewallParam);

                // Verify JSON structure
                assertTrue(sidewallParam.contains("\"height\""), "Sidewall should have height");
                assertTrue(sidewallParam.contains("\"level\""), "Sidewall should have level");
                assertTrue(sidewallParam.contains("\"width\""), "Sidewall should have width");
                assertTrue(sidewallParam.contains("\"distance\""), "Sidewall should have distance");
                assertTrue(sidewallParam.contains("\"sides\""), "Sidewall should have sides");
            }
        }

        assertTrue(gridsWithSidewall > 0, "At least some grids should have sidewall parameter");
        log.info("Found {} grids with sidewall parameter", gridsWithSidewall);

        log.info("=== SideWall Around Biome Test Completed ===");
    }

    @Test
    public void testSideWallWithSpecificSides() {
        log.info("=== Testing SideWall With Specific Sides ===");

        HexComposition composition = new HexComposition();
        composition.setName("sidewall-specific-sides-test");

        List<Feature> features = new ArrayList<>();

        // Biome: Medium plains
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setFeatureId("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.MEDIUM);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();
        features.add(plains);

        // SideWall: Only on NE, E, SE sides
        SideWall sideWall = SideWall.builder()
            .targetBiomeId("plains")
            .sides(List.of(WHexGrid.SIDE.NORTH_EAST, WHexGrid.SIDE.EAST, WHexGrid.SIDE.SOUTH_EAST))
            .height(15)
            .level(100)
            .distance(8)
            .minimum(5)
            .material("stone")
            .materialType(3)
            .build();
        sideWall.setName("east-wall");
        sideWall.setFeatureId("east-wall");
        sideWall.setStatus(FeatureStatus.NEW);
        sideWall.setType(FlowType.SIDEWALL);
        sideWall.initialize();
        features.add(sideWall);

        composition.setFeatures(features);

        // Prepare and compose
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        preparer.prepare(composition);

        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult biomePlacementResult = biomeComposer.compose(composition, "test-world", 54321L);

        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);

        assertTrue(flowResult.isSuccess(), "Flow composition should succeed");

        // Verify sidewall configuration uses specified sides
        Biome plainsB = findBiome(composition, "plains");
        int gridsWithSidewall = 0;
        for (FeatureHexGrid grid : plainsB.getHexGrids()) {
            String sidewallParam = grid.getParameters().get("g_sidewall");
            if (sidewallParam != null && !sidewallParam.isBlank()) {
                gridsWithSidewall++;

                // Verify JSON contains the requested sides
                assertTrue(sidewallParam.contains("\"NE\"") ||
                          sidewallParam.contains("\"NORTH_EAST\"") ||
                          sidewallParam.contains("\"E\"") ||
                          sidewallParam.contains("\"EAST\"") ||
                          sidewallParam.contains("\"SE\"") ||
                          sidewallParam.contains("\"SOUTH_EAST\""),
                    "Sidewall should reference the specified sides");

                // Verify custom parameters
                assertTrue(sidewallParam.contains("\"height\":15"), "Should use custom height");
                assertTrue(sidewallParam.contains("\"level\":100"), "Should use custom level");
                assertTrue(sidewallParam.contains("\"distance\":8"), "Should use custom distance");
                assertTrue(sidewallParam.contains("\"minimum\":5"), "Should use custom minimum");
            }
        }

        assertTrue(gridsWithSidewall > 0, "Should configure some grids with sidewall");
        log.info("Configured {} grids with specific-side sidewall", gridsWithSidewall);

        log.info("=== Specific Sides Test Completed ===");
    }

    @Test
    public void testSideWallWithDefaults() {
        log.info("=== Testing SideWall With Defaults ===");

        HexComposition composition = new HexComposition();
        composition.setName("sidewall-defaults-test");

        List<Feature> features = new ArrayList<>();

        // Biome: Small plains
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setFeatureId("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.SMALL);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();
        features.add(plains);

        // SideWall: With minimal configuration (uses defaults)
        SideWall sideWall = SideWall.builder()
            .targetBiomeId("plains")
            .build();
        sideWall.setName("default-wall");
        sideWall.setFeatureId("default-wall");
        sideWall.setStatus(FeatureStatus.NEW);
        sideWall.setType(FlowType.SIDEWALL);
        sideWall.initialize();
        features.add(sideWall);

        composition.setFeatures(features);

        // Prepare and compose
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        preparer.prepare(composition);

        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult biomePlacementResult = biomeComposer.compose(composition, "test-world", 99999L);

        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);

        assertTrue(flowResult.isSuccess(), "Flow composition should succeed");

        // Verify defaults were applied
        SideWall wall = findSideWall(composition, "default-wall");
        assertNotNull(wall);
        assertEquals(10, wall.getEffectiveHeight(), "Should use default height 10");
        assertEquals(95, wall.getEffectiveLevel(), "Should use default level 95");
        assertEquals(3, wall.getEffectiveWidthBlocks(), "Should use default width 3");
        assertEquals(5, wall.getEffectiveDistance(), "Should use default distance 5");
        assertEquals(0, wall.getEffectiveMinimum(), "Should use default minimum 0");

        log.info("=== Defaults Test Completed ===");
    }

    /**
     * Creates a composition with a city biome and a sidewall around it
     */
    private HexComposition createCompositionWithSideWall() {
        HexComposition composition = new HexComposition();
        composition.setName("sidewall-test");

        List<Feature> features = new ArrayList<>();

        // Biome: City (medium plains)
        PlainsBiome city = new PlainsBiome();
        city.setName("city");
        city.setFeatureId("city");
        city.setType(BiomeType.PLAINS);
        city.setSize(AreaSize.MEDIUM);
        city.setShape(AreaShape.CIRCLE);
        city.initialize();
        features.add(city);

        // SideWall: Wall around the city
        SideWall sideWall = SideWall.builder()
            .targetBiomeId("city")
            .height(10)
            .level(95)
            .distance(5)
            .minimum(0)
            .material("stone")
            .materialType(3)
            .build();
        sideWall.setName("city-wall");
        sideWall.setFeatureId("city-wall");
        sideWall.setStatus(FeatureStatus.NEW);
        sideWall.setType(FlowType.SIDEWALL);
        sideWall.setWidthBlocks(3);
        sideWall.initialize();
        features.add(sideWall);

        composition.setFeatures(features);
        return composition;
    }

    /**
     * Finds a SideWall by name in the composition
     */
    private SideWall findSideWall(HexComposition composition, String name) {
        if (composition.getFeatures() == null) return null;

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof SideWall && name.equals(feature.getName())) {
                return (SideWall) feature;
            }
        }
        return null;
    }

    /**
     * Finds a Biome by name in the composition
     */
    private Biome findBiome(HexComposition composition, String name) {
        if (composition.getFeatures() == null) return null;

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Biome && name.equals(feature.getName())) {
                return (Biome) feature;
            }
        }
        return null;
    }
}
