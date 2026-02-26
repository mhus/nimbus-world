package de.mhus.nimbus.world.generator.structures;

import de.mhus.nimbus.world.generator.composer.town.StructuresIndex;
import de.mhus.nimbus.world.generator.composer.town.StructuresService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job executor for placing structures on a single hex grid.
 *
 * Executor name: 'hexgrid-place-structures'
 *
 * Required parameters:
 * - hexQ: Hex axial coordinate Q
 * - hexR: Hex axial coordinate R
 *
 * Output:
 * - success: hexQ, hexR, placedCount, skipped, errors
 * - failure: Error message
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridStructurePlacerJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "hexgrid-place-structures";

    private final WHexGridRepository hexGridRepository;
    private final WFlatService wFlatService;
    private final WWorldService worldService;
    private final WLayerService layerService;
    private final StructuresService structuresService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            String hexQStr = job.getParameters().get("hexQ");
            String hexRStr = job.getParameters().get("hexR");

            if (hexQStr == null || hexRStr == null) {
                throw new JobExecutionException("Missing required parameters: hexQ, hexR");
            }

            int hexQ = Integer.parseInt(hexQStr);
            int hexR = Integer.parseInt(hexRStr);
            String worldId = job.getWorldId();

            log.info("Placing structures for hex {},{} in world {}", hexQ, hexR, worldId);

            // Load world
            WWorld world = worldService.getByWorldId(worldId)
                    .orElseThrow(() -> new JobExecutionException("World not found: " + worldId));

            // Load hex grid
            String position = hexQ + ";" + hexR;
            WHexGrid hexGrid = hexGridRepository.findByWorldIdAndPosition(worldId, position)
                    .orElseThrow(() -> new JobExecutionException("HexGrid not found: " + position));

            // Load flat for coordinate mapping
            String flatId = "genesis_" + hexQ + "_" + hexR;
            WFlat flat = wFlatService.findByWorldAndFlatId(worldId, flatId);
            if (flat == null) {
                throw new JobExecutionException("WFlat not found: " + flatId);
            }

            // Load structures index from region collection
            StructuresIndex structuresIndex = structuresService.findStructuresForWorldId(worldId);
            log.info("Loaded StructuresIndex: {} buildings", structuresIndex.getTotalBuildingCount());

            // Place structures
            StructurePlacerResult result = StructurePlacer.builder()
                    .structuresIndex(structuresIndex)
                    .world(world)
                    .layerService(layerService)
                    .hexGrid(hexGrid)
                    .flat(flat)
                    .build()
                    .placeStructures();

            log.info("Structure placement complete for hex {},{}: placed={}, skipped={}, errors={}",
                    hexQ, hexR, result.getPlacedCount(), result.getSkipped(),
                    result.getErrors() != null ? result.getErrors().size() : 0);

            if (!result.isSuccess()) {
                log.warn("Structure placement had errors: {}", result.getErrors());
            }

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("hexQ", hexQ);
            resultData.put("hexR", hexR);
            resultData.put("placedCount", result.getPlacedCount());
            resultData.put("skipped", result.getSkipped());
            if (result.getPlaced() != null && !result.getPlaced().isEmpty()) {
                resultData.put("placed", result.getPlaced());
            }
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                resultData.put("errors", result.getErrors());
            }
            return JobResult.success(resultData);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Structure placement failed for world {}", job.getWorldId(), e);
            throw new JobExecutionException("Structure placement failed: " + e.getMessage(), e);
        }
    }
}
