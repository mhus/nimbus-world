package de.mhus.nimbus.world.generator.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline.
 * <p>
 * This test uses the same architecture as Day3Generation workflow:
 * <ol>
 *   <li>HexCompositeBuilder.compose() - Enrichment with fillGaps and oceanBorderRings (like ApplyTranslatedInstructionJobExecutor)</li>
 *   <li>CREATE ALL - Initialize WFlats (like FlatHexGridEmptyCreateJobExecutor)</li>
 *   <li>GROUND - Build basic terrain using HexGridBuilderService.STEP.GROUND (like FlatManipulateJobExecutor with step=GROUND)</li>
 *   <li>BLENDER - Blend edges using HexGridBuilderService.STEP.BLENDER (like FlatManipulateJobExecutor with step=BLENDER)</li>
 *   <li>TERRAIN - Apply features using HexGridBuilderService.STEP.TERRAIN (like FlatManipulateJobExecutor with step=TERRAIN)</li>
 * </ol>
 * <p>
 * The test uses HexGridBuilderService directly (like HexGridManipulator does) without DB service dependencies,
 * making it a fast unit test that still validates the production code paths.
 */
@Slf4j
@Tag("full")
@Disabled
public class HexCompositeBuilderSimpleTest extends HexCompositeBuilderAbstract {

    @Test
    public void testSimpleRegions() throws Exception {
        composite("simple-test-regions");
    }

    @Test
    public void testSimpleRiver() throws Exception {
        composite("simple-test-river");
    }

    @Test
    public void testSimpleRoad() throws Exception {
        var res = composite("simple-test-road");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_road"), "HexGrids should contain road parameters");
    }

    @Test
    public void testSimpleRiverRoad() throws Exception {
        var res = composite("simple-test-river-road");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_road"), "HexGrids should contain road parameters");
        assertTrue(hexGridString.contains("g_river"), "HexGrids should contain river parameters");
    }

    @Test
    public void testSimpleWall() throws Exception {
        var res = composite("simple-test-wall");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_wall"), "HexGrids should contain wall parameters");
    }

    @Test
    public void testSimpleSmallTown() throws Exception {
        var res = composite("simple-test-small-town");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_village"), "HexGrids should contain village parameters");
    }

    @Test
    public void testSimpleVillagePoint() throws Exception {
        var res = composite("simple-test-village-point");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_village"), "HexGrids should contain village parameters");
    }

    @Test
    public void testSimpleMountainPoint() throws Exception {
        log.info("=== Testing MountainPoint ===");
        var res = composite("simple-test-mountain-point");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_mountain"), "HexGrids should contain mountain parameters");
        assertTrue(hexGridString.contains("mountainName"), "HexGrids should contain mountain configuration");
    }

    @Test
    public void testSimpleSpikesPoint() throws Exception {
        log.info("=== Testing SpikesPoint ===");
        var res = composite("simple-test-spikes-point");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_spikes"), "HexGrids should contain spikes parameters");
        assertTrue(hexGridString.contains("spikesName"), "HexGrids should contain spikes configuration");
    }

    @Test
    public void testSimpleMountainFacePoint() throws Exception {
        log.info("=== Testing MountainFacePoint ===");
        var res = composite("simple-test-mountain-face-point");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_mountain_face"), "HexGrids should contain mountain face parameters");
        assertTrue(hexGridString.contains("faceName"), "HexGrids should contain mountain face configuration");
    }

    @Test
    public void testSimpleLakesPoint() throws Exception {
        log.info("=== Testing LakesPoint ===");
        var res = composite("simple-test-lakes-point");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_lakes"), "HexGrids should contain lakes parameters");
        assertTrue(hexGridString.contains("lakesName"), "HexGrids should contain lakes configuration");
    }

    @Test
    public void testTightPlacement() throws Exception {
        log.info("=== Testing Tight Placement (5 small biomes close together, tests jitter) ===");
        var res = composite("simple-test-tight-placement");
        assertTrue(res.isSuccess(), "Tight placement should succeed thanks to jitter tolerance");
        assertTrue(res.getTotalBiomes() >= 5, "All 5 biomes should be placed, got: " + res.getTotalBiomes());
        log.info("Tight placement: {} biomes placed with {} retries",
            res.getTotalBiomes(),
            res.getBiomePlacementResult() != null ? res.getBiomePlacementResult().getRetries() : "N/A");
    }

    // ============= Biome Type Tests =============

    @Test
    public void testForestBiomeDense() throws Exception {
        log.info("=== Testing Forest Biome with DENSE density ===");
        var res = composite("simple-test-forest-dense");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"forest\""), "HexGrids should use forest builder");
        assertNotNull(res.getFillResult(), "Fill result should not be null");
    }

    @Test
    public void testForestBiomeSparse() throws Exception {
        log.info("=== Testing Forest Biome with SPARSE density ===");
        var res = composite("simple-test-forest-sparse");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"forest\""), "HexGrids should use forest builder");
    }

    @Test
    public void testPlainsBiomeRolling() throws Exception {
        log.info("=== Testing Plains Biome with ROLLING variation ===");
        var res = composite("simple-test-plains-rolling");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"plains\""), "HexGrids should use plains builder");
        assertTrue(hexGridString.contains("enableLakes"), "Plains should have lakes enabled");
    }

    @Test
    public void testPlainsBiomeMeadow() throws Exception {
        log.info("=== Testing Plains Biome with MEADOW variation ===");
        var res = composite("simple-test-plains-meadow");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"plains\""), "HexGrids should use plains builder");
    }

    @Test
    public void testDesertBiomeDunes() throws Exception {
        log.info("=== Testing Desert Biome with DUNES terrain ===");
        var res = composite("simple-test-desert-dunes");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"desert\""), "HexGrids should use desert builder");
    }

    @Test
    public void testDesertBiomeBadlands() throws Exception {
        log.info("=== Testing Desert Biome with BADLANDS terrain ===");
        var res = composite("simple-test-desert-badlands");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"desert\""), "HexGrids should use desert builder");
        assertTrue(hexGridString.contains("stoneRatio"), "Desert should have stone ratio parameter");
    }

    @Test
    public void testSwampBiomeDeep() throws Exception {
        log.info("=== Testing Swamp Biome with DEEP depth ===");
        var res = composite("simple-test-swamp-deep");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"swamp\""), "HexGrids should use swamp builder");
        assertTrue(hexGridString.contains("swampDepth"), "Swamp should have depth parameter");
    }

    @Test
    public void testSwampBiomeBog() throws Exception {
        log.info("=== Testing Swamp Biome with BOG depth ===");
        var res = composite("simple-test-swamp-bog");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"swamp\""), "HexGrids should use swamp builder");
    }

    @Test
    public void testMarshBiomeTidal() throws Exception {
        log.info("=== Testing Marsh Biome with TIDAL water level ===");
        var res = composite("simple-test-marsh-tidal");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"swamp\""), "HexGrids should use swamp builder (marsh uses swamp builder)");
    }

    // ============= GroundType Tests =============

    @Test
    public void testGroundTypeSnowy() throws Exception {
        log.info("=== Testing GroundType SNOWY ===");
        var res = composite("simple-test-groundtype-snowy");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("snowMaterial") || hexGridString.contains("groundType"),
            "HexGrids should contain snow material or groundType parameter");
    }

    @Test
    public void testGroundTypeSandy() throws Exception {
        log.info("=== Testing GroundType SANDY ===");
        var res = composite("simple-test-groundtype-sandy");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("desertSandMaterial") || hexGridString.contains("groundType"),
            "HexGrids should contain desert sand material or groundType parameter");
    }

    @Test
    public void testGroundTypeVolcanic() throws Exception {
        log.info("=== Testing GroundType VOLCANIC ===");
        var res = composite("simple-test-groundtype-volcanic");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("bedrockMaterial") || hexGridString.contains("groundType"),
            "HexGrids should contain bedrock material or groundType parameter");
    }

    @Test
    public void testGroundTypeIcy() throws Exception {
        log.info("=== Testing GroundType ICY ===");
        var res = composite("simple-test-groundtype-icy");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("iceMaterial") || hexGridString.contains("groundType"),
            "HexGrids should contain ice material or groundType parameter");
    }

    @Test
    public void testMountainBiomeSnowy() throws Exception {
        log.info("=== Testing Mountain Biome with SNOWY groundType ===");
        var res = composite("simple-test-mountain-snowy");
        var hexGridString = new ObjectMapper().writeValueAsString(res.getWHexGrids());
        assertTrue(hexGridString.contains("g_builder\":\"mountain\""), "HexGrids should use mountain builder");
        assertTrue(hexGridString.contains("groundType") || hexGridString.contains("snowMaterial"),
            "HexGrids should contain groundType or snow material");
    }

}
