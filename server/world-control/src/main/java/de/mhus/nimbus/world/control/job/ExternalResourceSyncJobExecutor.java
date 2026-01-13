package de.mhus.nimbus.world.control.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.GitHelper;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncService;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Job executor for external resource sync operations (export, import, validate).
 *
 * Supports three job types via WJob.type:
 * - "export": Export world data to filesystem
 * - "import": Import world data from filesystem
 * - "validate": Validate Git configuration and connectivity
 *
 * Parameters:
 * - name (required): Name of the ExternalResource in WAnything collection 'externalResource'
 * - force (optional): "true" or "false" (default: "false") - force sync even if timestamps unchanged
 * - remove (optional): "true" or "false" (default: "false") - remove overtaken entities/files
 * - worldId: Provided by job.getWorldId()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalResourceSyncJobExecutor implements JobExecutor {

    private static final String COLLECTION_NAME = "externalResource";

    private final WAnythingService anythingService;
    private final ResourceSyncService syncService;
    private final GitHelper gitHelper;
    private final ObjectMapper objectMapper;

    @Override
    public String getExecutorName() {
        return "externalResourceSync";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        String jobType = job.getType();

        if ("export".equals(jobType)) {
            return executeExport(job);
        } else if ("import".equals(jobType)) {
            return executeImport(job);
        } else if ("validate".equals(jobType)) {
            return executeValidate(job);
        } else {
            throw new JobExecutionException("Unknown job type: " + jobType + " (expected 'export', 'import', or 'validate')");
        }
    }

    /**
     * Execute export operation.
     */
    private JobResult executeExport(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();
            String name = params.get("name");
            if (name == null || name.isBlank()) {
                throw new JobExecutionException("Missing required parameter: name");
            }

            boolean force = parseBooleanParameter(params, "force", false);
            boolean remove = parseBooleanParameter(params, "remove", false);

            String jobWorldIdStr = job.getWorldId();

            log.info("Starting export job: jobWorldId={} name={} force={} remove={}", jobWorldIdStr, name, force, remove);

            // Load ExternalResource (use job worldId to find it)
            ExternalResourceDTO dto = loadExternalResource(jobWorldIdStr, name);

            // Use worldId from DTO, not from job!
            WorldId worldId;
            try {
                WorldId.validate(dto.getWorldId());
                worldId = WorldId.of(dto.getWorldId()).orElseThrow(
                        () -> new JobExecutionException("Invalid worldId in ExternalResourceDTO: " + dto.getWorldId())
                );
            } catch (Exception e) {
                throw new JobExecutionException("Invalid worldId in ExternalResourceDTO: " + dto.getWorldId(), e);
            }

            // Execute export
            log.info("Executing export: worldId={} (from DTO) localPath={} types={} force={} remove={}",
                    worldId, dto.getLocalPath(), dto.getTypes(), force, remove);

            ResourceSyncService.ExportResult result = syncService.export(worldId, dto, force, remove);

            // Update ExternalResourceDTO with sync result (use job worldId for lookup)
            updateSyncStatus(jobWorldIdStr, name, dto, result.timestamp(),
                    result.success() ? "Success" : result.errorMessage());

            // Return result
            if (result.success()) {
                String resultMessage = String.format(
                        "Export completed successfully: worldId=%s (from DTO) name=%s exported=%d deleted=%d types=%s",
                        worldId, name, result.entityCount(), result.deletedCount(), result.exportedByType()
                );
                log.info(resultMessage);
                return JobResult.ofSuccess(resultMessage);
            } else {
                String errorMessage = String.format(
                        "Export failed: worldId=%s name=%s error=%s",
                        worldId, name, result.errorMessage()
                );
                log.error(errorMessage);
                return JobResult.ofFailure(errorMessage);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute export job", e);
            throw new JobExecutionException("Export job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute import operation.
     */
    private JobResult executeImport(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();
            String name = params.get("name");
            if (name == null || name.isBlank()) {
                throw new JobExecutionException("Missing required parameter: name");
            }

            boolean force = parseBooleanParameter(params, "force", false);
            boolean remove = parseBooleanParameter(params, "remove", false);

            String jobWorldIdStr = job.getWorldId();

            log.info("Starting import job: jobWorldId={} name={} force={} remove={}",
                    jobWorldIdStr, name, force, remove);

            // Load ExternalResource (use job worldId to find it)
            ExternalResourceDTO dto = loadExternalResource(jobWorldIdStr, name);

            // Use worldId from DTO, not from job!
            WorldId worldId;
            try {
                WorldId.validate(dto.getWorldId());
                worldId = WorldId.of(dto.getWorldId()).orElseThrow(
                        () -> new JobExecutionException("Invalid worldId in ExternalResourceDTO: " + dto.getWorldId())
                );
            } catch (Exception e) {
                throw new JobExecutionException("Invalid worldId in ExternalResourceDTO: " + dto.getWorldId(), e);
            }

            // Execute import
            log.info("Executing import: worldId={} (from DTO) localPath={} types={} force={} remove={}",
                    worldId, dto.getLocalPath(), dto.getTypes(), force, remove);

            ResourceSyncService.ImportResult result = syncService.importData(worldId, dto, force, remove);

            // Update ExternalResourceDTO with sync result (use job worldId for lookup)
            updateSyncStatus(jobWorldIdStr, name, dto, result.timestamp(),
                    result.success() ? "Success" : result.errorMessage());

            // Return result
            if (result.success()) {
                String resultMessage = String.format(
                        "Import completed successfully: worldId=%s name=%s imported=%d deleted=%d types=%s",
                        worldId, name, result.imported(), result.deleted(), result.importedByType()
                );
                log.info(resultMessage);
                return JobResult.ofSuccess(resultMessage);
            } else {
                String errorMessage = String.format(
                        "Import failed: worldId=%s name=%s error=%s",
                        worldId, name, result.errorMessage()
                );
                log.error(errorMessage);
                return JobResult.ofFailure(errorMessage);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute import job", e);
            throw new JobExecutionException("Import job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute validate operation.
     */
    private JobResult executeValidate(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();
            String name = params.get("name");
            if (name == null || name.isBlank()) {
                throw new JobExecutionException("Missing required parameter: name");
            }

            String worldIdStr = job.getWorldId();
            if (worldIdStr == null || worldIdStr.isBlank()) {
                throw new JobExecutionException("Missing worldId on job");
            }

            log.info("Starting validate job: worldId={} name={}", worldIdStr, name);

            // Load ExternalResource
            ExternalResourceDTO dto = loadExternalResource(worldIdStr, name);

            // Validate configuration
            StringBuilder result = new StringBuilder();
            result.append("=== ExternalResource Validation ===\n");
            result.append("Name: ").append(name).append("\n");
            result.append("World: ").append(worldIdStr).append("\n");
            result.append("Local Path: ").append(dto.getLocalPath()).append("\n");
            result.append("Types: ").append(dto.getTypes()).append("\n");
            result.append("Auto Git: ").append(dto.isAutoGit()).append("\n\n");

            // Validate Git if enabled
            if (dto.isAutoGit()) {
                result.append("=== Git Validation ===\n");
                String gitValidation = gitHelper.validate(dto);
                result.append(gitValidation);
            } else {
                result.append("ℹ️  Git sync disabled (autoGit=false)\n");
            }

            log.info("Validation completed:\n{}", result);
            return JobResult.ofSuccess(result.toString());

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute validate job", e);
            throw new JobExecutionException("Validate job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get and validate WorldId from job.
     */
    private WorldId getWorldId(WJob job) throws JobExecutionException {
        String worldIdStr = job.getWorldId();
        if (worldIdStr == null || worldIdStr.isBlank()) {
            throw new JobExecutionException("Missing worldId on job");
        }

        try {
            WorldId.validate(worldIdStr);
            return WorldId.of(worldIdStr).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldIdStr)
            );
        } catch (Exception e) {
            throw new JobExecutionException("Invalid worldId: " + worldIdStr, e);
        }
    }

    /**
     * Load ExternalResource from WAnything.
     */
    private ExternalResourceDTO loadExternalResource(String worldIdStr, String name) throws JobExecutionException {
        log.debug("Searching for ExternalResource: worldId={} collection={} name={}",
                worldIdStr, COLLECTION_NAME, name);

        Optional<WAnything> entityOpt = anythingService.findByWorldIdAndCollectionAndName(
                worldIdStr,
                COLLECTION_NAME,
                name
        );

        if (entityOpt.isEmpty()) {
            log.error("ExternalResource not found: worldId={} collection={} name={}",
                    worldIdStr, COLLECTION_NAME, name);
            throw new JobExecutionException("ExternalResource not found: " + name + " for worldId: " + worldIdStr);
        }

        log.info("Found ExternalResource: worldId={} name={}", worldIdStr, name);

        WAnything entity = entityOpt.get();

        try {
            return objectMapper.convertValue(entity.getData(), ExternalResourceDTO.class);
        } catch (Exception e) {
            throw new JobExecutionException("Failed to parse ExternalResourceDTO: " + e.getMessage(), e);
        }
    }

    /**
     * Update sync status in WAnything.
     */
    private void updateSyncStatus(String worldIdStr, String name, ExternalResourceDTO dto,
                                   java.time.Instant timestamp, String result) {
        try {
            Optional<WAnything> entityOpt = anythingService.findByWorldIdAndCollectionAndName(
                    worldIdStr,
                    COLLECTION_NAME,
                    name
            );

            if (entityOpt.isPresent()) {
                WAnything entity = entityOpt.get();
                dto.setLastSync(timestamp);
                dto.setLastSyncResult(result);
                entity.setData(objectMapper.convertValue(dto, Map.class));
                entity.touchUpdate();
                anythingService.save(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to update sync status", e);
        }
    }

    private boolean parseBooleanParameter(Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
