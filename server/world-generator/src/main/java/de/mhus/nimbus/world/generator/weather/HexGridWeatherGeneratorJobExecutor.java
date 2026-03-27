package de.mhus.nimbus.world.generator.weather;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job executor for generating weather configuration on a single hex grid.
 *
 * Executor name: 'hex-grid-weather-generator'
 *
 * Required parameters:
 * - hexQ: Hex axial coordinate Q
 * - hexR: Hex axial coordinate R
 * - epoch: Epoch number to generate for
 *
 * Output:
 * - success: hexQ, hexR, epoch, generated (true/false)
 * - failure: Error message
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridWeatherGeneratorJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "hex-grid-weather-generator";

    private final WeatherGeneratorService weatherGeneratorService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            String hexQStr = job.getParameters().get("hexQ");
            String hexRStr = job.getParameters().get("hexR");
            String epochStr = job.getParameters().get("epoch");

            if (hexQStr == null || hexRStr == null) {
                throw new JobExecutionException("Missing required parameters: hexQ, hexR");
            }

            int hexQ = Integer.parseInt(hexQStr);
            int hexR = Integer.parseInt(hexRStr);
            int epoch = epochStr != null ? Integer.parseInt(epochStr) : 0;

            log.info("Generating weather for hex {},{} epoch {} in world {}", hexQ, hexR, epoch, job.getWorldId());

            boolean generated = weatherGeneratorService.generateWeather(job.getWorldId(), hexQ, hexR, epoch);

            log.info("Weather generation for hex {},{} epoch {}: {}", hexQ, hexR, epoch,
                    generated ? "created" : "skipped (already exists or no biome)");

            Map<String, Object> result = new HashMap<>();
            result.put("hexQ", hexQ);
            result.put("hexR", hexR);
            result.put("epoch", epoch);
            result.put("generated", generated);
            return JobResult.success(result);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Weather generation failed for world {}", job.getWorldId(), e);
            throw new JobExecutionException("Weather generation failed: " + e.getMessage(), e);
        }
    }
}
