package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncService;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing external resource definitions for import/export.
 */
@RestController
@RequestMapping("/control/external-resources")
@RequiredArgsConstructor
@Tag(name = "External Resources", description = "Manage import/export resource definitions")
public class ExternalResourceController extends BaseEditorController {

    private static final String COLLECTION_NAME = "externalResource";

    private final WAnythingService anythingService;
    private final ResourceSyncService syncService;
    private final ObjectMapper objectMapper;

    // ==================== DTOs ====================

    public record CreateResourceRequest(
            String worldId,
            String name,
            String localPath,
            List<String> types,
            boolean autoGit
    ) {}

    public record UpdateResourceRequest(
            String localPath,
            List<String> types,
            Boolean autoGit
    ) {}

    public record ExternalResourceResponse(
            String worldId,
            String name,
            String localPath,
            Instant lastSync,
            String lastSyncResult,
            List<String> types,
            boolean autoGit
    ) {}

    public record SyncResultResponse(
            boolean success,
            int entityCount,
            int deletedCount,
            Map<String, Integer> exportedByType,
            Map<String, Integer> deletedByType,
            String errorMessage,
            Instant timestamp
    ) {}

    public record ImportResultResponse(
            boolean success,
            int imported,
            int deleted,
            Map<String, Integer> importedByType,
            Map<String, Integer> deletedByType,
            String errorMessage,
            Instant timestamp
    ) {}

    // ==================== CRUD Operations ====================

    @PostMapping
    @Operation(summary = "Create external resource definition")
    public ResponseEntity<?> create(@RequestBody CreateResourceRequest request) {
        if (blank(request.name())) {
            return bad("Name is required");
        }
        if (blank(request.localPath())) {
            return bad("Local path is required");
        }
        if (blank(request.worldId())) {
            return bad("World ID is required");
        }

        // Validate world ID
        try {
            WorldId.validate(request.worldId());
        } catch (Exception e) {
            return bad("Invalid world ID: " + e.getMessage());
        }

        // Check if already exists
        var existing = anythingService.findByWorldIdAndCollectionAndName(
                request.worldId(),
                COLLECTION_NAME,
                request.name()
        );
        if (existing.isPresent()) {
            return conflict("Resource with name '" + request.name() + "' already exists");
        }

        // Create DTO
        ExternalResourceDTO dto = ExternalResourceDTO.builder()
                .worldId(request.worldId())
                .localPath(request.localPath())
                .types(request.types() != null ? request.types() : List.of())
                .autoGit(request.autoGit())
                .build();

        // Save in WAnything
        WAnything entity = new WAnything();
        entity.setWorldId(request.worldId());
        entity.setCollection(COLLECTION_NAME);
        entity.setName(request.name());
        entity.setTitle(request.name());
        entity.setData(objectMapper.convertValue(dto, Map.class));
        entity.setEnabled(true);
        entity.touchCreate();

        entity = anythingService.save(entity);

        return ResponseEntity.ok(toResponse(entity));
    }

    @GetMapping
    @Operation(summary = "List all external resource definitions")
    public ResponseEntity<?> list(@RequestParam(required = false) String worldId) {
        List<WAnything> entities;

        if (worldId != null) {
            entities = anythingService.findByWorldIdAndCollection(worldId, COLLECTION_NAME);
        } else {
            entities = anythingService.findByCollection(COLLECTION_NAME);
        }

        List<ExternalResourceResponse> responses = entities.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get external resource definition")
    public ResponseEntity<?> get(@RequestParam String worldId, @PathVariable String name) {
        if (blank(worldId)) {
            return bad("World ID is required");
        }

        var entity = anythingService.findByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        if (entity.isEmpty()) {
            return notFound("Resource not found: " + name);
        }

        return ResponseEntity.ok(toResponse(entity.get()));
    }

    @PutMapping("/{name}")
    @Operation(summary = "Update external resource definition")
    public ResponseEntity<?> update(@RequestParam String worldId,
                                    @PathVariable String name,
                                    @RequestBody UpdateResourceRequest request) {
        if (blank(worldId)) {
            return bad("World ID is required");
        }

        var entity = anythingService.findByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        if (entity.isEmpty()) {
            return notFound("Resource not found: " + name);
        }

        WAnything existing = entity.get();

        // Parse existing DTO
        ExternalResourceDTO dto = objectMapper.convertValue(existing.getData(), ExternalResourceDTO.class);

        // Update fields
        if (request.localPath() != null) {
            dto.setLocalPath(request.localPath());
        }
        if (request.types() != null) {
            dto.setTypes(request.types());
        }
        if (request.autoGit() != null) {
            dto.setAutoGit(request.autoGit());
        }

        // Save
        existing.setData(objectMapper.convertValue(dto, Map.class));
        existing.touchUpdate();
        existing = anythingService.save(existing);

        return ResponseEntity.ok(toResponse(existing));
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete external resource definition")
    public ResponseEntity<?> delete(@RequestParam String worldId, @PathVariable String name) {
        if (blank(worldId)) {
            return bad("World ID is required");
        }

        var entity = anythingService.findByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        if (entity.isEmpty()) {
            return notFound("Resource not found: " + name);
        }

        anythingService.deleteByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        return ResponseEntity.noContent().build();
    }

    // ==================== Sync Operations ====================

    @PostMapping("/{name}/export")
    @Operation(summary = "Export world data to filesystem")
    public ResponseEntity<?> export(@RequestParam String worldId,
                                    @PathVariable String name,
                                    @RequestParam(defaultValue = "false") boolean force,
                                    @RequestParam(defaultValue = "false") boolean remove) {
        if (blank(worldId)) {
            return bad("World ID is required");
        }

        // Load resource definition
        var entity = anythingService.findByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        if (entity.isEmpty()) {
            return notFound("Resource not found: " + name);
        }

        ExternalResourceDTO dto = objectMapper.convertValue(entity.get().getData(), ExternalResourceDTO.class);

        // Validate and parse world ID
        WorldId parsedWorldId;
        try {
            WorldId.validate(worldId);
            parsedWorldId = WorldId.of(worldId).orElseThrow(() -> new IllegalArgumentException("Invalid world ID"));
        } catch (Exception e) {
            return bad("Invalid world ID: " + e.getMessage());
        }

        // Execute export
        ResourceSyncService.ExportResult result = syncService.export(parsedWorldId, dto, force, remove);

        // Update last sync info
        dto.setLastSync(result.timestamp());
        dto.setLastSyncResult(result.success() ? "Success" : result.errorMessage());
        entity.get().setData(objectMapper.convertValue(dto, Map.class));
        entity.get().touchUpdate();
        anythingService.save(entity.get());

        // Return result
        SyncResultResponse response = new SyncResultResponse(
                result.success(),
                result.entityCount(),
                result.deletedCount(),
                result.exportedByType(),
                result.deletedByType(),
                result.errorMessage(),
                result.timestamp()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{name}/import")
    @Operation(summary = "Import world data from filesystem")
    public ResponseEntity<?> importData(@RequestParam String worldId,
                                       @PathVariable String name,
                                       @RequestParam(defaultValue = "false") boolean force,
                                       @RequestParam(defaultValue = "false") boolean remove) {
        if (blank(worldId)) {
            return bad("World ID is required");
        }

        // Load resource definition
        var entity = anythingService.findByWorldIdAndCollectionAndName(worldId, COLLECTION_NAME, name);
        if (entity.isEmpty()) {
            return notFound("Resource not found: " + name);
        }

        ExternalResourceDTO dto = objectMapper.convertValue(entity.get().getData(), ExternalResourceDTO.class);

        // Validate and parse world ID
        WorldId parsedWorldId;
        try {
            WorldId.validate(worldId);
            parsedWorldId = WorldId.of(worldId).orElseThrow(() -> new IllegalArgumentException("Invalid world ID"));
        } catch (Exception e) {
            return bad("Invalid world ID: " + e.getMessage());
        }

        // Execute import
        ResourceSyncService.ImportResult result = syncService.importData(parsedWorldId, dto, force, remove);

        // Update last sync info
        dto.setLastSync(result.timestamp());
        dto.setLastSyncResult(result.success() ? "Success" : result.errorMessage());
        entity.get().setData(objectMapper.convertValue(dto, Map.class));
        entity.get().touchUpdate();
        anythingService.save(entity.get());

        // Return result
        ImportResultResponse response = new ImportResultResponse(
                result.success(),
                result.imported(),
                result.deleted(),
                result.importedByType(),
                result.deletedByType(),
                result.errorMessage(),
                result.timestamp()
        );

        return ResponseEntity.ok(response);
    }

    // ==================== Helper Methods ====================

    private ExternalResourceResponse toResponse(WAnything entity) {
        ExternalResourceDTO dto = objectMapper.convertValue(entity.getData(), ExternalResourceDTO.class);

        return new ExternalResourceResponse(
                dto.getWorldId(),
                entity.getName(),
                dto.getLocalPath(),
                dto.getLastSync(),
                dto.getLastSyncResult(),
                dto.getTypes(),
                dto.isAutoGit()
        );
    }
}
