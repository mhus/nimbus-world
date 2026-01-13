package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for WFlat operations.
 * Base path: /control/flats
 * <p>
 * Provides access to flat terrain data for editing.
 */
@RestController
@RequestMapping("/control/flats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flats", description = "Flat terrain data management")
public class FlatController extends BaseEditorController {

    private final WFlatService flatService;

    // DTOs
    public record FlatListDto(
            String id,
            String worldId,
            String layerDataId,
            String flatId,
            String title,
            String description,
            int sizeX,
            int sizeZ,
            int mountX,
            int mountZ,
            int oceanLevel,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record FlatDetailDto(
            String id,
            String worldId,
            String layerDataId,
            String flatId,
            String title,
            String description,
            int sizeX,
            int sizeZ,
            int mountX,
            int mountZ,
            int oceanLevel,
            String oceanBlockId,
            boolean unknownProtected,
            byte[] levels,
            byte[] columns,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateFlatMetadataRequest(
            String title,
            String description
    ) {}

    public record MaterialDefinitionDto(
            int materialId,
            String blockDef,
            String nextBlockDef,
            boolean hasOcean,
            boolean isBlockMapDelta,
            Map<Integer, String> blockAtLevels
    ) {}

    public record UpdateMaterialRequest(
            String blockDef,
            String nextBlockDef,
            boolean hasOcean,
            boolean isBlockMapDelta,
            Map<Integer, String> blockAtLevels
    ) {}

    public record ApplyPaletteRequest(
            String paletteName
    ) {}


    /**
     * List all flats for a world.
     * GET /control/flats?worldId={worldId}
     */
    @GetMapping
    @Operation(summary = "List flats for a world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Missing worldId parameter")
    })
    public ResponseEntity<List<FlatListDto>> listFlats(
            @Parameter(description = "World ID", required = true)
            @RequestParam String worldId) {

        log.debug("Listing flats for worldId: {}", worldId);

        List<WFlat> flats = flatService.findByWorldId(worldId);

        List<FlatListDto> dtos = flats.stream()
                .map(flat -> new FlatListDto(
                        flat.getId(),
                        flat.getWorldId(),
                        flat.getLayerDataId(),
                        flat.getFlatId(),
                        flat.getTitle(),
                        flat.getDescription(),
                        flat.getSizeX(),
                        flat.getSizeZ(),
                        flat.getMountX(),
                        flat.getMountZ(),
                        flat.getOceanLevel(),
                        flat.getCreatedAt(),
                        flat.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        log.info("Found {} flats for worldId: {}", dtos.size(), worldId);
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get flat details by ID.
     * GET /control/flats/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get flat details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<FlatDetailDto> getFlat(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.debug("Getting flat details: id={}", id);

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();
        FlatDetailDto dto = new FlatDetailDto(
                flat.getId(),
                flat.getWorldId(),
                flat.getLayerDataId(),
                flat.getFlatId(),
                flat.getTitle(),
                flat.getDescription(),
                flat.getSizeX(),
                flat.getSizeZ(),
                flat.getMountX(),
                flat.getMountZ(),
                flat.getOceanLevel(),
                flat.getOceanBlockId(),
                flat.isUnknownProtected(),
                flat.getLevels(),
                flat.getColumns(),
                flat.getCreatedAt(),
                flat.getUpdatedAt()
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Update flat metadata (title and description).
     * PATCH /control/flats/{id}/metadata
     */
    @PatchMapping("/{id}/metadata")
    @Operation(summary = "Update flat metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<FlatDetailDto> updateFlatMetadata(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @RequestBody UpdateFlatMetadataRequest request) {

        log.info("Updating flat metadata: id={}", id);

        // Load flat
        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for metadata update: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        // Update metadata
        flat.setTitle(request.title());
        flat.setDescription(request.description());
        flat.touchUpdate();

        // Save to database
        WFlat updated = flatService.update(flat);

        log.info("Flat metadata updated successfully: id={}", id);

        // Return updated flat details
        FlatDetailDto dto = new FlatDetailDto(
                updated.getId(),
                updated.getWorldId(),
                updated.getLayerDataId(),
                updated.getFlatId(),
                updated.getTitle(),
                updated.getDescription(),
                updated.getSizeX(),
                updated.getSizeZ(),
                updated.getMountX(),
                updated.getMountZ(),
                updated.getOceanLevel(),
                updated.getOceanBlockId(),
                updated.isUnknownProtected(),
                updated.getLevels(),
                updated.getColumns(),
                updated.getCreatedAt(),
                updated.getUpdatedAt()
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Delete flat by ID.
     * DELETE /control/flats/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flat")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<Void> deleteFlat(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.info("Deleting flat: id={}", id);

        // Check if exists
        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for deletion: id={}", id);
            return ResponseEntity.notFound().build();
        }

        flatService.deleteById(id);
        log.info("Flat deleted successfully: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get height map image for flat.
     * GET /control/flats/{id}/height-map
     */
    @GetMapping("/{id}/height-map")
    @Operation(summary = "Get height map image")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<byte[]> getHeightMap(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.debug("Generating height map for flat: id={}", id);

        // Load flat
        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for height map: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        try {
            // Generate height map image
            byte[] imageBytes = generateHeightMapImage(flat);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("Failed to generate height map", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get block map image for flat.
     * GET /control/flats/{id}/block-map
     */
    @GetMapping("/{id}/block-map")
    @Operation(summary = "Get block map image")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<byte[]> getBlockMap(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.debug("Generating block map for flat: id={}", id);

        // Load flat
        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for block map: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        try {
            // Generate block map image
            byte[] imageBytes = generateBlockMapImage(flat);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("Failed to generate block map", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate height map image from flat data.
     * Blue (low) -> Green (mid) -> Red (high)
     */
    private byte[] generateHeightMapImage(WFlat flat) throws java.io.IOException {
        int width = flat.getSizeX();
        int height = flat.getSizeZ();
        byte[] levels = flat.getLevels();

        // Find min/max for color mapping
        int minLevel = Integer.MAX_VALUE;
        int maxLevel = Integer.MIN_VALUE;
        for (int i = 0; i < levels.length; i++) {
            int level = levels[i] & 0xFF; // Convert to unsigned
            if (level < minLevel) minLevel = level;
            if (level > maxLevel) maxLevel = level;
        }

        int range = maxLevel - minLevel;
        if (range == 0) range = 1;

        // Create image
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

        // Draw height map
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int level = levels[index] & 0xFF;
                float normalized = (float)(level - minLevel) / range;

                // Color gradient: blue (low) -> green (mid) -> red (high)
                int r, g, b;
                if (normalized < 0.5f) {
                    // Blue to green
                    float t = normalized * 2;
                    r = 0;
                    g = (int)(t * 255);
                    b = (int)((1 - t) * 255);
                } else {
                    // Green to red
                    float t = (normalized - 0.5f) * 2;
                    r = (int)(t * 255);
                    g = (int)((1 - t) * 255);
                    b = 0;
                }

                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, z, rgb);
            }
        }

        // Convert to PNG bytes
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Generate block map image from flat data.
     * Each block type ID gets a unique color.
     */
    private byte[] generateBlockMapImage(WFlat flat) throws java.io.IOException {
        int width = flat.getSizeX();
        int height = flat.getSizeZ();
        byte[] columns = flat.getColumns();

        // Create image
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

        // Draw block map
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int blockTypeId = columns[index] & 0xFF; // Convert to unsigned

                int rgb = getBlockColor(blockTypeId);
                image.setRGB(x, z, rgb);
            }
        }

        // Convert to PNG bytes
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Get RGB color for block type ID.
     */
    private int getBlockColor(int id) {
        if (id == 0) return 0x000000; // Black for air

        // Predefined colors for common block types
        int[] colors = {
                0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFA500, 0x800080,
                0xA52A2A, 0xFFC0CB, 0xFFD700, 0xC0C0C0, 0x808080, 0x800000, 0x808000, 0x008000,
                0x008080, 0x000080, 0xFF6347, 0x4682B4, 0xD2691E, 0xCD5C5C, 0xF08080, 0xFA8072,
                0xE9967A, 0xFFA07A, 0xDC143C, 0xFF1493, 0xFF69B4, 0xFFB6C1, 0xFFC0CB, 0xDB7093
        };

        if (id <= colors.length) {
            return colors[id - 1];
        }

        // Generate color based on ID using HSL-like algorithm
        float hue = ((id * 137.5f) % 360) / 360f;
        float saturation = 0.7f + ((id % 30) / 100f);
        float lightness = 0.45f + ((id % 20) / 100f);

        return hslToRgb(hue, saturation, lightness);
    }

    /**
     * Convert HSL to RGB color.
     */
    private int hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = l - c / 2;

        float r, g, b;
        int hi = (int)(h * 6);
        switch (hi) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }

        int ri = (int)((r + m) * 255);
        int gi = (int)((g + m) * 255);
        int bi = (int)((b + m) * 255);

        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Export flat data as JSON file for download.
     * GET /control/flats/{id}/export
     * Downloads levels, columns, and materials as JSON file.
     */
    @GetMapping("/{id}/export")
    @Operation(summary = "Export flat data as JSON file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<byte[]> exportFlat(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.info("Exporting flat data: id={}", id);

        try {
            // Load flat
            Optional<WFlat> flatOpt = flatService.findById(id);
            if (flatOpt.isEmpty()) {
                log.warn("Flat not found for export: id={}", id);
                return ResponseEntity.notFound().build();
            }

            WFlat flat = flatOpt.get();

            // Build JSON with levels, columns, and materials
            StringBuilder json = new StringBuilder();
            json.append("{");

            // Levels array
            json.append("\"levels\":[");
            byte[] levels = flat.getLevels();
            for (int i = 0; i < levels.length; i++) {
                if (i > 0) json.append(",");
                json.append(levels[i] & 0xFF); // unsigned
            }
            json.append("],");

            // Columns array
            json.append("\"columns\":[");
            byte[] columns = flat.getColumns();
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) json.append(",");
                json.append(columns[i] & 0xFF); // unsigned
            }
            json.append("],");

            // Materials map
            json.append("\"materials\":{");
            HashMap<Byte, WFlat.MaterialDefinition> materials = flat.getMaterials();
            if (materials != null && !materials.isEmpty()) {
                boolean first = true;
                for (Map.Entry<Byte, WFlat.MaterialDefinition> entry : materials.entrySet()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("\"").append(entry.getKey() & 0xFF).append("\":{");
                    WFlat.MaterialDefinition mat = entry.getValue();
                    json.append("\"blockDef\":").append(escapeJson(mat.getBlockDef())).append(",");
                    json.append("\"nextBlockDef\":").append(mat.getNextBlockDef() != null ? escapeJson(mat.getNextBlockDef()) : "null").append(",");
                    json.append("\"hasOcean\":").append(mat.isHasOcean());
                    json.append("}");
                }
            }
            json.append("}");

            json.append("}");

            byte[] jsonBytes = json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // Build filename: flat_{worldId}_{flatId}_{title}_{dateTime}.wflat.json
            String normalizedTitle = normalizeForFilename(flat.getTitle());
            String dateTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("flat_%s_%s_%s_%s.wflat.json",
                    flat.getWorldId(),
                    flat.getFlatId(),
                    normalizedTitle,
                    dateTime
            );

            log.info("Flat exported successfully: id={}, size={} bytes, filename={}", id, jsonBytes.length, filename);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(jsonBytes);

        } catch (Exception e) {
            log.error("Failed to export flat", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Import flat data from uploaded JSON file.
     * POST /control/flats/{id}/import
     * Updates levels, columns, and materials from uploaded file.
     */
    @PostMapping("/{id}/import")
    @Operation(summary = "Import flat data from JSON file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file or data")
    })
    public ResponseEntity<FlatDetailDto> importFlat(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @Parameter(description = "JSON file to import", required = true)
            @RequestParam("file") MultipartFile file) {

        log.info("Importing flat data: id={}, filename={}", id, file.getOriginalFilename());

        try {
            // Validate file
            if (file.isEmpty()) {
                log.warn("Empty file uploaded");
                return ResponseEntity.badRequest().build();
            }

            // Load flat
            Optional<WFlat> flatOpt = flatService.findById(id);
            if (flatOpt.isEmpty()) {
                log.warn("Flat not found for import: id={}", id);
                return ResponseEntity.notFound().build();
            }

            WFlat flat = flatOpt.get();

            // Read and parse JSON
            String jsonContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Parse JSON manually (simple parsing for our structure)
            Map<String, Object> data = parseSimpleJson(jsonContent);

            // Extract levels
            @SuppressWarnings("unchecked")
            List<Integer> levelsList = (List<Integer>) data.get("levels");
            if (levelsList == null) {
                log.warn("Missing levels in import data");
                return ResponseEntity.badRequest().build();
            }

            // Extract columns
            @SuppressWarnings("unchecked")
            List<Integer> columnsList = (List<Integer>) data.get("columns");
            if (columnsList == null) {
                log.warn("Missing columns in import data");
                return ResponseEntity.badRequest().build();
            }

            // Validate size
            int expectedSize = flat.getSizeX() * flat.getSizeZ();
            if (levelsList.size() != expectedSize || columnsList.size() != expectedSize) {
                log.warn("Invalid import data: size mismatch. Expected: {}, got levels: {}, columns: {}",
                        expectedSize, levelsList.size(), columnsList.size());
                return ResponseEntity.badRequest().build();
            }

            // Convert to byte arrays
            byte[] newLevels = new byte[levelsList.size()];
            for (int i = 0; i < levelsList.size(); i++) {
                newLevels[i] = levelsList.get(i).byteValue();
            }

            byte[] newColumns = new byte[columnsList.size()];
            for (int i = 0; i < columnsList.size(); i++) {
                newColumns[i] = columnsList.get(i).byteValue();
            }

            // Extract materials
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> materialsMap = (Map<String, Map<String, Object>>) data.get("materials");
            HashMap<Byte, WFlat.MaterialDefinition> newMaterials = new HashMap<>();
            if (materialsMap != null) {
                for (Map.Entry<String, Map<String, Object>> entry : materialsMap.entrySet()) {
                    byte key = Byte.parseByte(entry.getKey());
                    Map<String, Object> matData = entry.getValue();

                    WFlat.MaterialDefinition matDef = WFlat.MaterialDefinition.builder()
                            .blockDef((String) matData.get("blockDef"))
                            .nextBlockDef((String) matData.get("nextBlockDef"))
                            .hasOcean((Boolean) matData.get("hasOcean"))
                            .build();

                    newMaterials.put(key, matDef);
                }
            }

            // Update flat data
            flat.setLevels(newLevels);
            flat.setColumns(newColumns);
            flat.setMaterials(newMaterials);
            flat.touchUpdate();

            // Save to database
            WFlat updated = flatService.update(flat);

            log.info("Flat imported successfully: id={}", id);

            // Return updated flat details
            FlatDetailDto dto = new FlatDetailDto(
                    updated.getId(),
                    updated.getWorldId(),
                    updated.getLayerDataId(),
                    updated.getFlatId(),
                    updated.getTitle(),
                    updated.getDescription(),
                    updated.getSizeX(),
                    updated.getSizeZ(),
                    updated.getMountX(),
                    updated.getMountZ(),
                    updated.getOceanLevel(),
                    updated.getOceanBlockId(),
                    updated.isUnknownProtected(),
                    updated.getLevels(),
                    updated.getColumns(),
                    updated.getCreatedAt(),
                    updated.getUpdatedAt()
            );

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Failed to import flat", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Escape JSON string
     */
    private String escapeJson(String str) {
        if (str == null) return "null";
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Normalize string for use in filename
     * Removes/replaces special characters, spaces, etc.
     * Limits length to 50 characters.
     */
    private String normalizeForFilename(String str) {
        if (str == null || str.isBlank()) {
            return "untitled";
        }

        // Replace spaces with underscores, remove special characters
        String normalized = str.trim()
                .replaceAll("[\\s]+", "_")  // Replace whitespace with underscore
                .replaceAll("[^a-zA-Z0-9_-]", "")  // Remove special characters except underscore and dash
                .replaceAll("_{2,}", "_")  // Replace multiple underscores with single
                .toLowerCase();

        // Limit length to 50 characters
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
            // Remove trailing underscore if any
            if (normalized.endsWith("_")) {
                normalized = normalized.substring(0, 49);
            }
        }

        return normalized;
    }

    /**
     * Simple JSON parser for our specific structure
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSimpleJson(String json) {
        // Use Jackson ObjectMapper for proper JSON parsing
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    /**
     * List all materials for a flat.
     * GET /control/flats/{id}/materials
     */
    @GetMapping("/{id}/materials")
    @Operation(summary = "List all materials for a flat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found")
    })
    public ResponseEntity<List<MaterialDefinitionDto>> listMaterials(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id) {

        log.debug("Listing materials for flat: id={}", id);

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for materials list: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();
        HashMap<Byte, WFlat.MaterialDefinition> materials = flat.getMaterials();

        // Convert to DTOs
        List<MaterialDefinitionDto> dtos = new java.util.ArrayList<>();
        if (materials != null) {
            for (Map.Entry<Byte, WFlat.MaterialDefinition> entry : materials.entrySet()) {
                int materialId = entry.getKey() & 0xFF;
                WFlat.MaterialDefinition mat = entry.getValue();

                // Convert blockAtLevels to Map<Integer, String> for DTO
                Map<Integer, String> blockAtLevels = mat.getBlockAtLevels() != null
                    ? new HashMap<>(mat.getBlockAtLevels())
                    : new HashMap<>();

                MaterialDefinitionDto dto = new MaterialDefinitionDto(
                    materialId,
                    mat.getBlockDef(),
                    mat.getNextBlockDef(),
                    mat.isHasOcean(),
                    mat.isBlockMapDelta(),
                    blockAtLevels
                );
                dtos.add(dto);
            }
        }

        // Sort by materialId
        dtos.sort(java.util.Comparator.comparingInt(MaterialDefinitionDto::materialId));

        log.debug("Returned {} materials for flat: id={}", dtos.size(), id);
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get single material by ID.
     * GET /control/flats/{id}/materials/{materialId}
     */
    @GetMapping("/{id}/materials/{materialId}")
    @Operation(summary = "Get single material by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat or material not found")
    })
    public ResponseEntity<MaterialDefinitionDto> getMaterial(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Material ID (1-255)", required = true)
            @PathVariable int materialId) {

        log.debug("Getting material: flatId={}, materialId={}", id, materialId);

        // Validate materialId range
        if (materialId < 0 || materialId > 255) {
            log.warn("Invalid material ID: {}", materialId);
            return ResponseEntity.badRequest().build();
        }

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for get material: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();
        HashMap<Byte, WFlat.MaterialDefinition> materials = flat.getMaterials();

        if (materials == null || !materials.containsKey((byte) materialId)) {
            log.warn("Material not found: flatId={}, materialId={}", id, materialId);
            return ResponseEntity.notFound().build();
        }

        WFlat.MaterialDefinition mat = materials.get((byte) materialId);
        Map<Integer, String> blockAtLevels = mat.getBlockAtLevels() != null
            ? new HashMap<>(mat.getBlockAtLevels())
            : new HashMap<>();

        MaterialDefinitionDto dto = new MaterialDefinitionDto(
            materialId,
            mat.getBlockDef(),
            mat.getNextBlockDef(),
            mat.isHasOcean(),
            mat.isBlockMapDelta(),
            blockAtLevels
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Create or update material.
     * PUT /control/flats/{id}/materials/{materialId}
     */
    @PutMapping("/{id}/materials/{materialId}")
    @Operation(summary = "Create or update material")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found"),
            @ApiResponse(responseCode = "400", description = "Invalid material data")
    })
    public ResponseEntity<MaterialDefinitionDto> updateMaterial(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Material ID (1-254)", required = true)
            @PathVariable int materialId,
            @RequestBody UpdateMaterialRequest request) {

        log.info("Updating material: flatId={}, materialId={}", id, materialId);

        // Validate materialId: 1-254 editable (0=protected, 255=special)
        if (materialId < 1 || materialId > 254) {
            log.warn("Invalid material ID for update: {} (must be 1-254)", materialId);
            return ResponseEntity.badRequest().build();
        }

        // Validate blockDef format
        if (request.blockDef() == null || !isValidBlockDef(request.blockDef())) {
            log.warn("Invalid blockDef format: {}", request.blockDef());
            return ResponseEntity.badRequest().build();
        }

        // Validate nextBlockDef if present
        if (request.nextBlockDef() != null && !request.nextBlockDef().isBlank()
            && !isValidBlockDef(request.nextBlockDef())) {
            log.warn("Invalid nextBlockDef format: {}", request.nextBlockDef());
            return ResponseEntity.badRequest().build();
        }

        // Validate blockAtLevels
        if (request.blockAtLevels() != null) {
            for (Map.Entry<Integer, String> entry : request.blockAtLevels().entrySet()) {
                if (entry.getKey() < 0 || entry.getKey() > 255) {
                    log.warn("Invalid blockAtLevels key: {}", entry.getKey());
                    return ResponseEntity.badRequest().build();
                }
                if (!isValidBlockDef(entry.getValue())) {
                    log.warn("Invalid blockAtLevels value: {}", entry.getValue());
                    return ResponseEntity.badRequest().build();
                }
            }
        }

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for update material: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        // Build MaterialDefinition
        WFlat.MaterialDefinition matDef = WFlat.MaterialDefinition.builder()
                .blockDef(request.blockDef())
                .nextBlockDef(request.nextBlockDef())
                .hasOcean(request.hasOcean())
                .isBlockMapDelta(request.isBlockMapDelta())
                .blockAtLevels(request.blockAtLevels() != null ? new HashMap<>(request.blockAtLevels()) : new HashMap<>())
                .build();

        // Set material
        flat.setMaterial((byte) materialId, matDef);
        flat.touchUpdate();

        // Save
        WFlat updated = flatService.update(flat);

        log.info("Material updated successfully: flatId={}, materialId={}", id, materialId);

        // Return DTO
        WFlat.MaterialDefinition savedMat = updated.getMaterial((byte) materialId);
        Map<Integer, String> blockAtLevels = savedMat.getBlockAtLevels() != null
            ? new HashMap<>(savedMat.getBlockAtLevels())
            : new HashMap<>();

        MaterialDefinitionDto dto = new MaterialDefinitionDto(
            materialId,
            savedMat.getBlockDef(),
            savedMat.getNextBlockDef(),
            savedMat.isHasOcean(),
            savedMat.isBlockMapDelta(),
            blockAtLevels
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Delete material.
     * DELETE /control/flats/{id}/materials/{materialId}
     */
    @DeleteMapping("/{id}/materials/{materialId}")
    @Operation(summary = "Delete material")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found"),
            @ApiResponse(responseCode = "400", description = "Cannot delete protected material")
    })
    public ResponseEntity<Void> deleteMaterial(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Material ID (1-254)", required = true)
            @PathVariable int materialId) {

        log.info("Deleting material: flatId={}, materialId={}", id, materialId);

        // Cannot delete material 0 (protected)
        if (materialId == 0) {
            log.warn("Cannot delete protected material 0");
            return ResponseEntity.badRequest().build();
        }

        // Validate materialId range
        if (materialId < 0 || materialId > 255) {
            log.warn("Invalid material ID: {}", materialId);
            return ResponseEntity.badRequest().build();
        }

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for delete material: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        // Remove material
        if (flat.getMaterials() != null) {
            flat.getMaterials().remove((byte) materialId);
            flat.touchUpdate();
            flatService.update(flat);
            log.info("Material deleted successfully: flatId={}, materialId={}", id, materialId);
        } else {
            log.warn("No materials to delete: flatId={}, materialId={}", id, materialId);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Apply preset palette.
     * POST /control/flats/{id}/materials/palette
     */
    @PostMapping("/{id}/materials/palette")
    @Operation(summary = "Apply preset palette (nimbus or legacy)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Flat not found"),
            @ApiResponse(responseCode = "400", description = "Invalid palette name")
    })
    public ResponseEntity<List<MaterialDefinitionDto>> applyPalette(
            @Parameter(description = "Flat ID", required = true)
            @PathVariable String id,
            @RequestBody ApplyPaletteRequest request) {

        log.info("Applying palette: flatId={}, palette={}", id, request.paletteName());

        String paletteName = request.paletteName().toLowerCase();

        if (!paletteName.equals("nimbus") && !paletteName.equals("legacy")) {
            log.warn("Invalid palette name: {}", paletteName);
            return ResponseEntity.badRequest().build();
        }

        Optional<WFlat> flatOpt = flatService.findById(id);
        if (flatOpt.isEmpty()) {
            log.warn("Flat not found for apply palette: id={}", id);
            return ResponseEntity.notFound().build();
        }

        WFlat flat = flatOpt.get();

        // Apply palette based on name
        applyPaletteToFlat(flat, paletteName);
        flat.touchUpdate();

        // Save
        WFlat updated = flatService.update(flat);

        log.info("Palette applied successfully: flatId={}, palette={}", id, paletteName);

        // Return all materials
        List<MaterialDefinitionDto> dtos = new java.util.ArrayList<>();
        HashMap<Byte, WFlat.MaterialDefinition> materials = updated.getMaterials();
        if (materials != null) {
            for (Map.Entry<Byte, WFlat.MaterialDefinition> entry : materials.entrySet()) {
                int materialId = entry.getKey() & 0xFF;
                WFlat.MaterialDefinition mat = entry.getValue();

                Map<Integer, String> blockAtLevels = mat.getBlockAtLevels() != null
                    ? new HashMap<>(mat.getBlockAtLevels())
                    : new HashMap<>();

                MaterialDefinitionDto dto = new MaterialDefinitionDto(
                    materialId,
                    mat.getBlockDef(),
                    mat.getNextBlockDef(),
                    mat.isHasOcean(),
                    mat.isBlockMapDelta(),
                    blockAtLevels
                );
                dtos.add(dto);
            }
        }

        dtos.sort(java.util.Comparator.comparingInt(MaterialDefinitionDto::materialId));

        return ResponseEntity.ok(dtos);
    }

    /**
     * Apply palette to flat (mimics FlatMaterialService palettes)
     */
    private void applyPaletteToFlat(WFlat flat, String paletteName) {
        // Palette definitions from FlatMaterialService
        if (paletteName.equals("nimbus")) {
            // Nimbus palette
            flat.setMaterial((byte) 1, createMaterialDef("n:g", "n:d", true));    // GRASS
            flat.setMaterial((byte) 2, createMaterialDef("n:d", "n:s", false));   // DIRT
            flat.setMaterial((byte) 3, createMaterialDef("n:s", "n:s", false));   // STONE
            flat.setMaterial((byte) 4, createMaterialDef("n:sa", "n:sa", false)); // SAND
            flat.setMaterial((byte) 5, createMaterialDef("n:w", "n:w", true));    // WATER
            flat.setMaterial((byte) 6, createMaterialDef("n:b", "n:b", false));   // BEDROCK
            flat.setMaterial((byte) 7, createMaterialDef("n:sn", "n:d", true));   // SNOW
            flat.setMaterial((byte) 8, createMaterialDef("n:2", "n:2", false));   // INVISIBLE
            flat.setMaterial((byte) 9, createMaterialDef("n:3", "n:3", false));   // INVISIBLE_SOLID
        } else if (paletteName.equals("legacy")) {
            // Legacy palette
            flat.setMaterial((byte) 1, createMaterialDef("w:310", "w:279", true));  // GRASS
            flat.setMaterial((byte) 2, createMaterialDef("w:279", "w:553", false)); // DIRT
            flat.setMaterial((byte) 3, createMaterialDef("w:553", "w:553", false)); // STONE
            flat.setMaterial((byte) 4, createMaterialDef("w:520", "w:520", false)); // SAND
            flat.setMaterial((byte) 5, createMaterialDef("w:1008", "w:1008", true)); // WATER
            flat.setMaterial((byte) 6, createMaterialDef("w:127", "w:127", false)); // BEDROCK
            flat.setMaterial((byte) 7, createMaterialDef("w:537", "w:279", true));  // SNOW
            flat.setMaterial((byte) 8, createMaterialDef("w:2", "w:2", false));     // INVISIBLE
            flat.setMaterial((byte) 9, createMaterialDef("w:3", "w:3", false));     // INVISIBLE_SOLID
        }
    }

    /**
     * Helper to create MaterialDefinition
     */
    private WFlat.MaterialDefinition createMaterialDef(String blockDef, String nextBlockDef, boolean hasOcean) {
        return WFlat.MaterialDefinition.builder()
                .blockDef(blockDef)
                .nextBlockDef(nextBlockDef)
                .hasOcean(hasOcean)
                .isBlockMapDelta(true)
                .blockAtLevels(new HashMap<>())
                .build();
    }

    /**
     * Validate blockDef format: n:xxx@s:yyy or w:xxx@s:yyy
     */
    private boolean isValidBlockDef(String blockDef) {
        if (blockDef == null || blockDef.isBlank()) {
            return false;
        }
        // Pattern: (n|w):blockid[@s:state]
        // Examples: n:stone, n:stone@s:default, w:310, w:310@s:default
        return blockDef.matches("^[nw]:[a-zA-Z0-9_]+(@s:[a-zA-Z0-9_]+)?$");
    }
}
