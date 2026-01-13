package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.layer.*;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.*;

/**
 * REST controller for block grid visualization.
 * Provides endpoints to load block coordinates for BlockGridEditor.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/layers/{layerId}/grid")
@RequiredArgsConstructor
@Slf4j
public class ELayerBlockGridController {

    private final WLayerRepository layerRepository;
    private final WLayerTerrainRepository terrainRepository;
    private final WLayerModelRepository modelRepository;
    private final WWorldRepository worldRepository;
    private final StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get block coordinates from WLayerTerrain chunks within a specific area.
     * Loads only chunks that intersect with the requested cubic area.
     *
     * @param worldId World ID
     * @param layerId Layer ID
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param radius Radius in blocks (half-size of the cubic area)
     * @return List of block coordinates with optional color
     */
    @GetMapping("/terrain/blocks")
    public ResponseEntity<?> getTerrainBlocks(
            @PathVariable String worldId,
            @PathVariable String layerId,
            @RequestParam(required = false, defaultValue = "0") int centerX,
            @RequestParam(required = false, defaultValue = "0") int centerY,
            @RequestParam(required = false, defaultValue = "0") int centerZ,
            @RequestParam(required = false, defaultValue = "16") int radiusXZ,
            @RequestParam(required = false, defaultValue = "32") int radiusY
    ) {
        log.debug("Loading terrain blocks for worldId={}, layerId={}, center=({},{},{}), radiusXZ={}, radiusY={}",
                worldId, layerId, centerX, centerY, centerZ, radiusXZ, radiusY);

        // Load layer
        Optional<WLayer> layerOpt = layerRepository.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();

        // Both GROUND and MODEL layers can have terrain data
        // GROUND: terrain is the primary storage
        // MODEL: terrain is the projection/cache of models after sync

        // Load world to get chunkSize
        Optional<WWorld> worldOpt = worldRepository.findByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        int chunkSize = worldOpt.get().getPublicData().getChunkSize();

        // Calculate which chunks intersect with the requested area
        int minX = centerX - radiusXZ;
        int maxX = centerX + radiusXZ;
        int minZ = centerZ - radiusXZ;
        int maxZ = centerZ + radiusXZ;

        int minChunkX = Math.floorDiv(minX, chunkSize);
        int maxChunkX = Math.floorDiv(maxX, chunkSize);
        int minChunkZ = Math.floorDiv(minZ, chunkSize);
        int maxChunkZ = Math.floorDiv(maxZ, chunkSize);

        log.debug("Loading chunks from ({},{}) to ({},{})", minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        // Collect block coordinates from relevant chunks only
        List<Map<String, Object>> blockCoordinates = new ArrayList<>();
        int chunksChecked = 0;
        int chunksFound = 0;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunksChecked++;
                String chunkKey = chunkX + ":" + chunkZ;

                // Load this specific chunk
                Optional<WLayerTerrain> terrainOpt = terrainRepository.findByLayerDataIdAndChunkKey(
                        layer.getLayerDataId(), chunkKey);

                if (terrainOpt.isEmpty()) {
                    log.trace("Chunk {} not found for layerDataId={}", chunkKey, layer.getLayerDataId());
                    continue;
                }

                chunksFound++;

                WLayerTerrain terrain = terrainOpt.get();
                if (terrain.getStorageId() == null) {
                    continue;
                }

                try {
                    // Load chunk data from storage
                    InputStream stream = storageService.load(terrain.getStorageId());
                    if (stream == null) {
                        log.warn("Chunk data not found for storageId={}", terrain.getStorageId());
                        continue;
                    }

                    // Decompress if needed
                    InputStream dataStream = stream;
                    if (terrain.isCompressed()) {
                        dataStream = new java.util.zip.GZIPInputStream(stream);
                    }

                    LayerChunkData chunkData = objectMapper.readValue(dataStream, LayerChunkData.class);
                    dataStream.close();

                    // Extract blocks from chunk data
                    // NOTE: Positions in LayerChunkData are already absolute world coordinates!
                    if (chunkData.getBlocks() != null) {
                        for (LayerBlock layerBlock : chunkData.getBlocks()) {
                            if (layerBlock.getBlock() == null) {
                                continue;
                            }

                            var position = layerBlock.getBlock().getPosition();
                            if (position == null) {
                                continue;
                            }

                            // Positions are already in world coordinates
                            int worldX = (int) position.getX();
                            int worldY = (int) position.getY();
                            int worldZ = (int) position.getZ();

                            // Add all blocks from loaded chunks (no additional filtering)
                            Map<String, Object> coord = new HashMap<>();
                            coord.put("x", worldX);
                            coord.put("y", worldY);
                            coord.put("z", worldZ);

                            // Optional: Add color based on group
                            if (layerBlock.getGroup() > 0) {
                                coord.put("color", getGroupColor(layerBlock.getGroup()));
                            }

                            blockCoordinates.add(coord);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to load chunk data for storageId={}", terrain.getStorageId(), e);
                }
            }
        }

        log.info("Terrain blocks: checked {} chunks, found {} chunks, returning {} block coordinates (center={},{},{}, radiusXZ={}, radiusY={})",
                chunksChecked, chunksFound, blockCoordinates.size(), centerX, centerY, centerZ, radiusXZ, radiusY);

        // If no blocks found and center is at origin, try to find any chunk as a hint
        String hint = null;
        if (blockCoordinates.isEmpty() && centerX == 0 && centerY == 64 && centerZ == 0) {
            List<WLayerTerrain> anyChunks = terrainRepository.findByLayerDataId(layer.getLayerDataId());
            if (!anyChunks.isEmpty()) {
                hint = "No blocks at default center (0,64,0). Found " + anyChunks.size() + " chunks total. Try navigating to find blocks.";
            } else {
                hint = "No terrain chunks found for this layer.";
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("blocks", blockCoordinates);
        response.put("count", blockCoordinates.size());
        response.put("chunksChecked", chunksChecked);
        response.put("chunksFound", chunksFound);
        if (hint != null) {
            response.put("hint", hint);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed block information from WLayerTerrain.
     *
     * @param worldId World ID
     * @param layerId Layer ID
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return Block details including LayerBlock wrapper
     */
    @GetMapping("/terrain/block/{x}/{y}/{z}")
    public ResponseEntity<?> getTerrainBlockDetails(
            @PathVariable String worldId,
            @PathVariable String layerId,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable int z
    ) {
        log.debug("Loading terrain block details for worldId={}, layerId={}, pos=({},{},{})",
                worldId, layerId, x, y, z);

        // Load layer
        Optional<WLayer> layerOpt = layerRepository.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();

        // Both GROUND and MODEL layers can have terrain data

        // Load world to get chunkSize
        Optional<WWorld> worldOpt = worldRepository.findByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        int chunkSize = worldOpt.get().getPublicData().getChunkSize();

        // Calculate chunk coordinates
        int chunkX = Math.floorDiv(x, chunkSize);
        int chunkZ = Math.floorDiv(z, chunkSize);
        String chunkKey = chunkX + ":" + chunkZ;

        // Load terrain chunk
        Optional<WLayerTerrain> terrainOpt = terrainRepository.findByLayerDataIdAndChunkKey(
                layer.getLayerDataId(), chunkKey);

        if (terrainOpt.isEmpty() || terrainOpt.get().getStorageId() == null) {
            return ResponseEntity.notFound().build();
        }

        WLayerTerrain terrain = terrainOpt.get();

        try {
            // Load chunk data from storage
            InputStream stream = storageService.load(terrain.getStorageId());
            if (stream == null) {
                return ResponseEntity.notFound().build();
            }

            // Decompress if needed
            InputStream dataStream = stream;
            if (terrain.isCompressed()) {
                dataStream = new java.util.zip.GZIPInputStream(stream);
            }

            LayerChunkData chunkData = objectMapper.readValue(dataStream, LayerChunkData.class);
            dataStream.close();

            // Find block at specified position (positions in chunk are already absolute world coordinates)
            if (chunkData.getBlocks() != null) {
                for (LayerBlock layerBlock : chunkData.getBlocks()) {
                    if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                        continue;
                    }

                    var pos = layerBlock.getBlock().getPosition();
                    if ((int) pos.getX() == x && (int) pos.getY() == y && (int) pos.getZ() == z) {
                        // Return LayerBlock wrapper with block and metadata
                        return ResponseEntity.ok(Map.of(
                                "block", layerBlock.getBlock(),
                                "group", layerBlock.getGroup(),
                                "metadata", layerBlock.getMetadata() != null ? layerBlock.getMetadata() : ""
                        ));
                    }
                }
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to load block details", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all block coordinates from WLayerModel.
     * Returns only coordinates and optional color for each block.
     *
     * @param worldId World ID
     * @param layerId Layer ID
     * @param modelId Model ID
     * @return List of block coordinates with optional color
     */
    @GetMapping("/models/{modelId}/blocks")
    public ResponseEntity<?> getModelBlocks(
            @PathVariable String worldId,
            @PathVariable String layerId,
            @PathVariable String modelId
    ) {
        log.debug("Loading model blocks for worldId={}, layerId={}, modelId={}",
                worldId, layerId, modelId);

        // Load layer
        Optional<WLayer> layerOpt = layerRepository.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return ResponseEntity.badRequest().body(Map.of("error", "Layer is not MODEL type"));
        }

        // Load model
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayerModel model = modelOpt.get();

        // Collect all block coordinates (relative positions from model)
        List<Map<String, Object>> blockCoordinates = new ArrayList<>();

        if (model.getContent() != null) {
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                    continue;
                }

                var position = layerBlock.getBlock().getPosition();

                Map<String, Object> coord = new HashMap<>();
                coord.put("x", (int) position.getX());
                coord.put("y", (int) position.getY());
                coord.put("z", (int) position.getZ());

                // Optional: Add color based on group
                if (layerBlock.getGroup() > 0) {
                    coord.put("color", getGroupColor(layerBlock.getGroup()));
                }

                blockCoordinates.add(coord);
            }
        }

        log.debug("Returning {} block coordinates from model", blockCoordinates.size());

        return ResponseEntity.ok(Map.of(
                "blocks", blockCoordinates,
                "count", blockCoordinates.size(),
                "mountPoint", Map.of(
                        "x", model.getMountX(),
                        "y", model.getMountY(),
                        "z", model.getMountZ()
                ),
                "rotation", model.getRotation()
        ));
    }

    /**
     * Get detailed block information from WLayerModel.
     *
     * @param worldId World ID
     * @param layerId Layer ID
     * @param modelId Model ID
     * @param x Block X coordinate (relative to mount point)
     * @param y Block Y coordinate
     * @param z Block Z coordinate (relative to mount point)
     * @return Block details including LayerBlock wrapper
     */
    @GetMapping("/models/{modelId}/block/{x}/{y}/{z}")
    public ResponseEntity<?> getModelBlockDetails(
            @PathVariable String worldId,
            @PathVariable String layerId,
            @PathVariable String modelId,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable int z
    ) {
        log.debug("Loading model block details for worldId={}, layerId={}, modelId={}, pos=({},{},{})",
                worldId, layerId, modelId, x, y, z);

        // Load layer
        Optional<WLayer> layerOpt = layerRepository.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return ResponseEntity.badRequest().body(Map.of("error", "Layer is not MODEL type"));
        }

        // Load model
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayerModel model = modelOpt.get();

        // Find block at specified relative position
        if (model.getContent() != null) {
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                    continue;
                }

                var pos = layerBlock.getBlock().getPosition();
                if ((int) pos.getX() == x && (int) pos.getY() == y && (int) pos.getZ() == z) {
                    // Return LayerBlock wrapper with block and metadata
                    return ResponseEntity.ok(Map.of(
                            "block", layerBlock.getBlock(),
                            "group", layerBlock.getGroup(),
                            "metadata", layerBlock.getMetadata() != null ? layerBlock.getMetadata() : ""
                    ));
                }
            }
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get color for group ID (simple mapping for visualization).
     */
    private String getGroupColor(int groupId) {
        // Simple color mapping based on group ID
        String[] colors = {
                "#3b82f6", // blue
                "#ef4444", // red
                "#10b981", // green
                "#f59e0b", // amber
                "#8b5cf6", // purple
                "#ec4899", // pink
                "#06b6d4", // cyan
                "#f97316"  // orange
        };
        return colors[groupId % colors.length];
    }
}
