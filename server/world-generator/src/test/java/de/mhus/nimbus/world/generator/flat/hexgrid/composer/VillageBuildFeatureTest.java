package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Village as BuildFeature - demonstrates cascaded composites
 */
@Slf4j
public class VillageBuildFeatureTest {

    @Test
    public void testVillageBuildFeature() {
        log.info("=== Testing Village as BuildFeature ===");

        // Create village with template
        Village village = Village.builder()
            .templateName("hamlet-medieval")
            .baseLevel(95)
            .build();

        village.setName("test-hamlet");
        village.setType(StructureType.HAMLET);

        // Use BuildFeature interface to build the village internally
        BuildContext context = BuildContext.of("test-world", 12345L);
        CompositionResult result = village.build(context);

        // Verify build success
        assertTrue(result.isSuccess(), "Village build should succeed");
        assertNull(result.getErrorMessage(), "Should have no error");

        // Verify village has been designed
        assertEquals(1, result.getTotalStructures(), "Should have 1 structure");
        assertEquals(1, result.getTotalGrids(), "Hamlet should have 1 grid");

        // Verify village properties have been populated
        assertNotNull(village.getBuildings(), "Buildings should be populated");
        assertNotNull(village.getStreets(), "Streets should be populated");
        assertNotNull(village.getParameters(), "Parameters should be populated");

        assertFalse(village.getBuildings().isEmpty(), "Should have buildings");
        assertTrue(village.getParameters().containsKey("village"),
            "Should have village parameter");

        log.info("Village built successfully: {} buildings, {} parameters",
            village.getBuildings().size(),
            village.getParameters().size());

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testVillageBuildWithoutTemplate() {
        log.info("=== Testing Village build without template (should fail) ===");

        Village village = Village.builder()
            .baseLevel(95)
            .build();

        village.setName("test-village");

        BuildContext context = BuildContext.of("test-world");
        CompositionResult result = village.build(context);

        // Should fail because templateName is missing
        assertFalse(result.isSuccess(), "Build should fail without template");
        assertNotNull(result.getErrorMessage(), "Should have error message");
        assertTrue(result.getErrorMessage().contains("templateName"),
            "Error should mention templateName");

        log.info("Expected failure: {}", result.getErrorMessage());
        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testVillageBuildWithInvalidTemplate() {
        log.info("=== Testing Village build with invalid template ===");

        Village village = Village.builder()
            .templateName("non-existent-template")
            .baseLevel(95)
            .build();

        village.setName("test-village");

        BuildContext context = BuildContext.of("test-world");
        CompositionResult result = village.build(context);

        // Should fail because template doesn't exist
        assertFalse(result.isSuccess(), "Build should fail with invalid template");
        assertNotNull(result.getErrorMessage(), "Should have error message");

        log.info("Expected failure: {}", result.getErrorMessage());
        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testVillage2x1Build() {
        log.info("=== Testing 2x1 Village build ===");

        Village village = Village.builder()
            .templateName("village-2x1-medieval")
            .baseLevel(95)
            .build();

        village.setName("test-village-2x1");
        village.setType(StructureType.VILLAGE);

        BuildContext context = BuildContext.of("test-world", 99999L);
        CompositionResult result = village.build(context);

        assertTrue(result.isSuccess(), "Village build should succeed");
        assertEquals(1, result.getTotalStructures(), "Should have 1 structure");
        assertEquals(2, result.getTotalGrids(), "2x1 Village should have 2 grids");

        assertNotNull(village.getBuildings(), "Buildings should be populated");
        assertTrue(village.getBuildings().size() > 0, "Should have buildings");

        log.info("2x1 Village built successfully: {} grids, {} buildings",
            result.getTotalGrids(),
            village.getBuildings().size());

        log.info("=== Test completed successfully ===");
    }
}
