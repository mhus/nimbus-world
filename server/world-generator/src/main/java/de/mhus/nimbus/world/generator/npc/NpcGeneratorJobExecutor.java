package de.mhus.nimbus.world.generator.npc;

import de.mhus.nimbus.world.generator.npc.NpcGeneratorService.NpcGenerationRequest;
import de.mhus.nimbus.world.generator.npc.NpcGeneratorService.ScheduleEntry;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Job executor for NPC generation.
 *
 * Executor name: 'npc-generator'
 *
 * Required parameters:
 * - entityId: Unique entity identifier
 * - npcDocumentName: Name of the WDocument (collection="lore") describing this NPC (e.g. "npc:farmer_hans")
 *
 * Optional parameters:
 * - modelId: Entity model reference (default: "human_male_1")
 * - posX, posY, posZ: Position coordinates
 * - portraitPath: Path to portrait image
 * - aiModel: AI model to use for generation
 * - epoches: JSON array of epoch numbers (e.g. "[0,1]")
 * - schedule: JSON array of schedule entries
 * - loreContext: JSON array of additional lore document names to load
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NpcGeneratorJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "npc-generator";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NpcGeneratorService npcGeneratorService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            Map<String, String> params = job.getParameters();

            String entityId = requireParam(params, "entityId");
            String npcDocumentName = params.get("npcDocumentName");

            NpcGenerationRequest request = new NpcGenerationRequest(
                    job.getWorldId(),
                    entityId,
                    params.get("modelId"),
                    parseDouble(params.get("posX")),
                    parseDouble(params.get("posY")),
                    parseDouble(params.get("posZ")),
                    params.get("environment"),
                    params.get("characterDescription"),
                    params.get("characterBackground"),
                    params.get("portraitPath"),
                    params.get("aiModel"),
                    parseIntList(params.get("epoches")),
                    parseSchedule(params.get("schedule")),
                    parseLoreContext(params.get("loreContext"), npcDocumentName)
            );

            log.info("Generating NPC '{}' in world {}", entityId, job.getWorldId());

            Map<String, Object> result = npcGeneratorService.generateNpc(request);

            log.info("NPC generation complete: {}", entityId);
            return JobResult.success(result);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("NPC generation failed for world {}", job.getWorldId(), e);
            throw new JobExecutionException("NPC generation failed: " + e.getMessage(), e);
        }
    }

    private String requireParam(Map<String, String> params, String key) throws JobExecutionException {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + key);
        }
        return value;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        return Double.parseDouble(value);
    }

    private List<Integer> parseIntList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private List<ScheduleEntry> parseSchedule(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse schedule JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseLoreContext(String json, String npcDocumentName) {
        List<String> result = new java.util.ArrayList<>();
        if (npcDocumentName != null && !npcDocumentName.isBlank()) {
            result.add(npcDocumentName);
        }
        if (json != null && !json.isBlank()) {
            try {
                List<String> additional = MAPPER.readValue(json, new TypeReference<>() {});
                result.addAll(additional);
            } catch (Exception e) {
                log.warn("Failed to parse loreContext JSON: {}", e.getMessage());
            }
        }
        return result.isEmpty() ? null : result;
    }
}
