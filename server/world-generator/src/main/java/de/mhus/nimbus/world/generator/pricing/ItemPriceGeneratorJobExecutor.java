package de.mhus.nimbus.world.generator.pricing;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job executor for AI-assisted item price generation.
 *
 * Parameters:
 * - aiModel (optional): AI model name, e.g., "default:chat", "openai:gpt-4" (default: "default:chat")
 * - batchSize (optional): number of items per AI query (default: 15)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ItemPriceGeneratorJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "item-price-generator";

    private final ItemPriceGeneratorService service;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            Map<String, String> params = job.getParameters();

            WorldId worldId = WorldId.of(job.getWorldId())
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + job.getWorldId()));

            String aiModel = params != null ? params.getOrDefault("aiModel", null) : null;
            int batchSize = 15;
            if (params != null && params.containsKey("batchSize")) {
                try {
                    batchSize = Integer.parseInt(params.get("batchSize"));
                } catch (NumberFormatException e) {
                    log.warn("Invalid batchSize parameter, using default: 15");
                }
            }

            log.info("Starting item-price-generator for worldId={}, aiModel={}, batchSize={}",
                    worldId.getId(), aiModel, batchSize);

            Map<String, Object> result = service.generatePrices(worldId, aiModel, batchSize);

            return JobResult.success(result);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new JobExecutionException("Item price generation failed: " + e.getMessage(), e);
        }
    }
}
