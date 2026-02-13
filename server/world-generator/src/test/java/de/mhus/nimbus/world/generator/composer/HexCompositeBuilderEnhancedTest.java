package de.mhus.nimbus.world.generator.composer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class HexCompositeBuilderEnhancedTest extends HexCompositeBuilderAbstract {

    @Test
    public void testEnhancedRivers() throws Exception {
        composite("enhanced-test-rivers");
    }

}
