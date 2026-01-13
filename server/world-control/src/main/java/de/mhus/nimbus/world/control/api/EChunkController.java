package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WChunkRepository;
import de.mhus.nimbus.world.shared.world.WChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Chunk viewing and management.
 * Base path: /control/worlds/{worldId}/chunks
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/chunks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chunks", description = "Chunk viewing and management for editors")
public class EChunkController extends BaseEditorController {

    private final WChunkRepository chunkRepository;
    private final WChunkService chunkService;
    private final ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.layer.WDirtyChunkService dirtyChunkService;

    /**
     * List all Chunks for a world with pagination.
     * GET /control/worlds/{worldId}/chunks?offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all Chunks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query for chunk key") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST chunks: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // IMPORTANT: Filter out instances - chunks are per world only
        String lookupWorldId = wid.withoutInstance().getId();

        // Get all Chunks for this world
        List<WChunk> all;
        if (query != null && !query.isBlank()) {
            // Search by chunk key
            all = chunkRepository.findByWorldIdAndChunkContaining(lookupWorldId, query);
        } else {
            all = chunkRepository.findByWorldId(lookupWorldId);
        }

        int totalCount = all.size();

        // Apply pagination and convert to simple DTOs
        List<Map<String, Object>> chunkDtos = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toSimpleDto)
                .collect(Collectors.toList());

        log.debug("Returning {} chunks (total: {})", chunkDtos.size(), totalCount);

        return ResponseEntity.ok(Map.of(
                "chunks", chunkDtos,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Get single Chunk metadata.
     * GET /control/worlds/{worldId}/chunks/{chunkKey}
     */
    @GetMapping("/{chunkKey}")
    @Operation(summary = "Get Chunk metadata by key")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chunk found"),
            @ApiResponse(responseCode = "404", description = "Chunk not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Chunk key (e.g., 0:0)") @PathVariable String chunkKey) {

        log.debug("GET chunk: worldId={}, chunkKey={}", worldId, chunkKey);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        String lookupWorldId = wid.withoutInstance().getId();

        Optional<WChunk> chunkOpt = chunkRepository.findByWorldIdAndChunk(lookupWorldId, chunkKey);
        if (chunkOpt.isEmpty()) {
            log.warn("Chunk not found: worldId={}, chunkKey={}", lookupWorldId, chunkKey);
            return notFound("chunk not found");
        }

        return ResponseEntity.ok(toSimpleDto(chunkOpt.get()));
    }

    /**
     * Get Chunk data (blocks, heightData) using streaming to save memory.
     * GET /control/worlds/{worldId}/chunks/{chunkKey}/data
     */
    @GetMapping("/{chunkKey}/data")
    @Operation(summary = "Get Chunk data (blocks, heightData)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chunk data retrieved"),
            @ApiResponse(responseCode = "404", description = "Chunk not found")
    })
    public ResponseEntity<?> getData(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Chunk key (e.g., 0:0)") @PathVariable String chunkKey) {

        log.debug("GET chunk data: worldId={}, chunkKey={}", worldId, chunkKey);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        String lookupWorldId = wid.withoutInstance().getId();

        // Load chunk data using WChunkService.getStream for memory efficiency
        try {
            InputStream stream = chunkService.getStream(wid, chunkKey);
            if (stream == null || stream.available() == 0) {
                log.warn("Chunk data not found: worldId={}, chunkKey={}", lookupWorldId, chunkKey);
                return notFound("chunk data not found");
            }

            // Deserialize chunk data from stream
            ChunkData chunkData = objectMapper.readValue(stream, ChunkData.class);

            // Return chunk data
            return ResponseEntity.ok(Map.of(
                    "cx", chunkData.getCx(),
                    "cz", chunkData.getCz(),
                    "size", chunkData.getSize(),
                    "blockCount", chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0,
                    "blocks", chunkData.getBlocks() != null ? chunkData.getBlocks() : List.of(),
                    "heightData", chunkData.getHeightData() != null ? chunkData.getHeightData() : new int[0][]
            ));
        } catch (Exception e) {
            log.error("Failed to load chunk data: worldId={}, chunkKey={}", lookupWorldId, chunkKey, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load chunk data: " + e.getMessage()));
        }
    }

    /**
     * Mark chunk as dirty for regeneration.
     * POST /control/worlds/{worldId}/chunks/{chunkKey}/dirty
     */
    @PostMapping("/{chunkKey}/dirty")
    @Operation(summary = "Mark chunk as dirty")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chunk marked as dirty"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> markDirty(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Chunk key (e.g., 0:0)") @PathVariable String chunkKey) {

        log.debug("MARK DIRTY chunk: worldId={}, chunkKey={}", worldId, chunkKey);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        String lookupWorldId = wid.withoutInstance().getId();

        // Mark chunk as dirty
        dirtyChunkService.markChunkDirty(lookupWorldId, chunkKey, "manual_editor_request");

        log.info("Marked chunk as dirty: worldId={}, chunkKey={}", lookupWorldId, chunkKey);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // Helper methods

    private Map<String, Object> toSimpleDto(WChunk chunk) {
        return Map.of(
                "id", chunk.getId(),
                "worldId", chunk.getWorldId(),
                "chunk", chunk.getChunk(),
                "storageId", chunk.getStorageId() != null ? chunk.getStorageId() : "",
                "compressed", chunk.isCompressed(),
                "createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : "",
                "updatedAt", chunk.getUpdatedAt() != null ? chunk.getUpdatedAt().toString() : ""
        );
    }
}
