package de.mhus.nimbus.world.generator.flora;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job executor for generating flora on a single hex grid.
 *
 * Executor name: 'hex-grid-flora-generator'
 *
 * Required parameters:
 * - hexQ: Hex axial coordinate Q
 * - hexR: Hex axial coordinate R
 *
 * Output:
 * - success: hexQ, hexR, blockCount
 * - failure: Error message
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridFloraGeneratorJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "hex-grid-flora-generator";

    private final FloraGeneratorService floraGeneratorService;

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

            log.info("Generating flora for hex {},{} in world {}", hexQ, hexR, job.getWorldId());

            int blockCount = floraGeneratorService.generateFlora(job.getWorldId(), hexQ, hexR);

            log.info("Flora generation complete for hex {},{}: {} blocks placed", hexQ, hexR, blockCount);

            Map<String, Object> result = new HashMap<>();
            result.put("hexQ", hexQ);
            result.put("hexR", hexR);
            result.put("blockCount", blockCount);
            return JobResult.success(result);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Flora generation failed for world {}", job.getWorldId(), e);
            throw new JobExecutionException("Flora generation failed: " + e.getMessage(), e);
        }
    }
}
