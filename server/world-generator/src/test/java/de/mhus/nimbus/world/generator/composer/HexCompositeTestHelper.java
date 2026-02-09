package de.mhus.nimbus.world.generator.composer;

import de.mhus.nimbus.world.generator.composer.build.CompositionResult;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridGenerator;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Test helper for HexComposition testing.
 * Provides utilities for generating and persisting WHexGrids in tests.
 */
@Slf4j
public class HexCompositeTestHelper {

    /**
     * Generate WHexGrids from composition and save to repository.
     * This is used in tests to simulate the complete workflow including WHexGrid persistence.
     *
     * @param composition The hex composition
     * @param repository The repository to save WHexGrids
     * @return Generation result with statistics
     */
    public static HexGridGenerator.GenerationResult generateAndSaveWHexGrids(
            HexComposition composition,
            WHexGridRepository repository) {

        if (composition == null) {
            throw new IllegalArgumentException("composition is required");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository is required");
        }

        log.debug("Generating WHexGrids from composition: {}", composition.getName());

        HexGridGenerator generator = new HexGridGenerator(repository);
        HexGridGenerator.GenerationResult result = generator.generateHexGrids(composition);

        if (result.isSuccess()) {
            log.debug("Generated {} WHexGrids successfully", result.getCreatedGrids());
        } else {
            log.error("WHexGrid generation failed: {}", result.getErrors());
        }

        return result;
    }

    /**
     * Generate WHexGrids after composition and add result to CompositionResult.
     * Convenience method for tests.
     *
     * @param compositionResult The composition result from HexCompositeBuilder
     * @param composition The hex composition
     * @param repository The repository to save WHexGrids
     * @return Updated composition result with generation result
     */
    public static CompositionResult generateWHexGridsForResult(
            CompositionResult compositionResult,
            HexComposition composition,
            WHexGridRepository repository) {

        HexGridGenerator.GenerationResult genResult = generateAndSaveWHexGrids(composition, repository);

        // Update composition result with generation result
        return CompositionResult.builder()
                .success(compositionResult.isSuccess() && genResult.isSuccess())
                .errorMessage(compositionResult.getErrorMessage())
                .warnings(compositionResult.getWarnings())
                .biomePlacementResult(compositionResult.getBiomePlacementResult())
                .structurePlacementResult(compositionResult.getStructurePlacementResult())
                .fillResult(compositionResult.getFillResult())
                .pointCompositionResult(compositionResult.getPointCompositionResult())
                .flowCompositionResult(compositionResult.getFlowCompositionResult())
                .generationResult(genResult)
                .totalBiomes(compositionResult.getTotalBiomes())
                .totalStructures(compositionResult.getTotalStructures())
                .totalPoints(compositionResult.getTotalPoints())
                .totalFlows(compositionResult.getTotalFlows())
                .totalGrids(compositionResult.getTotalGrids())
                .filledGrids(compositionResult.getFilledGrids())
                .generatedWHexGrids(genResult.getCreatedGrids())
                .build();
    }
}
