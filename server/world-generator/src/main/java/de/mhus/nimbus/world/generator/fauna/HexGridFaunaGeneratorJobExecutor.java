package de.mhus.nimbus.world.generator.fauna;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job executor for generating fauna on a single hex grid.
 *
 * Executor name: 'hex-grid-fauna-generator'
 *
 * Required parameters:
 * - hexQ: Hex axial coordinate Q
 * - hexR: Hex axial coordinate R
 *
 * Output:
 * - success: hexQ, hexR, entityCount
 * - failure: Error message
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridFaunaGeneratorJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "hex-grid-fauna-generator";

    private final FaunaGeneratorService faunaGeneratorService;

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

            log.info("Generating fauna for hex {},{} in world {}", hexQ, hexR, job.getWorldId());

            int entityCount = faunaGeneratorService.generateFauna(job.getWorldId(), hexQ, hexR);

            log.info("Fauna generation complete for hex {},{}: {} entities created", hexQ, hexR, entityCount);

            Map<String, Object> result = new HashMap<>();
            result.put("hexQ", hexQ);
            result.put("hexR", hexR);
            result.put("entityCount", entityCount);
            return JobResult.success(result);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fauna generation failed for world {}", job.getWorldId(), e);
            throw new JobExecutionException("Fauna generation failed: " + e.getMessage(), e);
        }
    }
}
