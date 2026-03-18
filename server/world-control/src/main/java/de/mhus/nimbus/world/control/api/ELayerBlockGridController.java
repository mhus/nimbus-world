package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.layer.*;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for block grid visualization.
 * Provides endpoints to load block coordinates for BlockGridEditor.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/layers/{layerId}/grid")
@RequiredArgsConstructor
@Slf4j
@RequireWorldRole(WorldRoles.EDITOR)
public class ELayerBlockGridController {

    private final WLayerService layerService;
    private final WWorldService worldService;

    /**
     * Get block coordinates from WLayerTerrain chunks within a specific area.
     * Loads only chunks that intersect with the requested cubic area.
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
        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();

        // Load world to get chunkSize
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
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

                // Load chunk data via service
                Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(
                        layer.getWorldId(), layer.getLayerDataId(), chunkKey);

                if (chunkDataOpt.isEmpty()) {
                    log.trace("Chunk {} not found for layerDataId={}", chunkKey, layer.getLayerDataId());
                    continue;
                }

                chunksFound++;
                LayerChunkData chunkData = chunkDataOpt.get();

                // Extract blocks from chunk data
                if (chunkData.getBlocks() != null) {
                    for (LayerBlock layerBlock : chunkData.getBlocks()) {
                        if (layerBlock.getBlock() == null) continue;

                        var position = layerBlock.getBlock().getPosition();
                        if (position == null) continue;

                        int worldX = (int) position.getX();
                        int worldY = (int) position.getY();
                        int worldZ = (int) position.getZ();

                        Map<String, Object> coord = new HashMap<>();
                        coord.put("x", worldX);
                        coord.put("y", worldY);
                        coord.put("z", worldZ);

                        String groupId = layerBlock.getGroup();
                        if (groupId != null && !groupId.isEmpty()) {
                            coord.put("color", getGroupColor(groupId));
                        }

                        blockCoordinates.add(coord);
                    }
                }
            }
        }

        log.info("Terrain blocks: checked {} chunks, found {} chunks, returning {} block coordinates (center={},{},{}, radiusXZ={}, radiusY={})",
                chunksChecked, chunksFound, blockCoordinates.size(), centerX, centerY, centerZ, radiusXZ, radiusY);

        // If no blocks found and center is at origin, provide a hint
        String hint = null;
        if (blockCoordinates.isEmpty() && centerX == 0 && centerY == 64 && centerZ == 0) {
            long chunkCount = layerService.countTerrainChunks(layer.getWorldId(), layer.getLayerDataId());
            if (chunkCount > 0) {
                hint = "No blocks at default center (0,64,0). Found " + chunkCount + " chunks total. Try navigating to find blocks.";
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

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();

        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        int chunkSize = worldOpt.get().getPublicData().getChunkSize();

        int chunkX = Math.floorDiv(x, chunkSize);
        int chunkZ = Math.floorDiv(z, chunkSize);
        String chunkKey = chunkX + ":" + chunkZ;

        Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(
                layer.getWorldId(), layer.getLayerDataId(), chunkKey);

        if (chunkDataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LayerChunkData chunkData = chunkDataOpt.get();

        if (chunkData.getBlocks() != null) {
            for (LayerBlock layerBlock : chunkData.getBlocks()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) continue;

                var pos = layerBlock.getBlock().getPosition();
                if ((int) pos.getX() == x && (int) pos.getY() == y && (int) pos.getZ() == z) {
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
     * Get all block coordinates from WLayerModel.
     */
    @GetMapping("/models/{modelId}/blocks")
    public ResponseEntity<?> getModelBlocks(
            @PathVariable String worldId,
            @PathVariable String layerId,
            @PathVariable String modelId
    ) {
        log.debug("Loading model blocks for worldId={}, layerId={}, modelId={}",
                worldId, layerId, modelId);

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return ResponseEntity.badRequest().body(Map.of("error", "Layer is not MODEL type"));
        }

        Optional<WLayerModel> modelOpt = layerService.loadModelById(modelId);
        if (modelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayerModel model = modelOpt.get();

        List<Map<String, Object>> blockCoordinates = new ArrayList<>();

        if (model.getContent() != null) {
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) continue;

                var position = layerBlock.getBlock().getPosition();

                Map<String, Object> coord = new HashMap<>();
                coord.put("x", (int) position.getX());
                coord.put("y", (int) position.getY());
                coord.put("z", (int) position.getZ());

                String groupId = layerBlock.getGroup();
                if (groupId != null && !groupId.isEmpty()) {
                    coord.put("color", getGroupColor(groupId));
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

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return ResponseEntity.badRequest().body(Map.of("error", "Layer is not MODEL type"));
        }

        Optional<WLayerModel> modelOpt = layerService.loadModelById(modelId);
        if (modelOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WLayerModel model = modelOpt.get();

        if (model.getContent() != null) {
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) continue;

                var pos = layerBlock.getBlock().getPosition();
                if ((int) pos.getX() == x && (int) pos.getY() == y && (int) pos.getZ() == z) {
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

    private String getGroupColor(String groupId) {
        String[] colors = {
                "#3b82f6", "#ef4444", "#10b981", "#f59e0b",
                "#8b5cf6", "#ec4899", "#06b6d4", "#f97316"
        };
        int hash = Math.abs(groupId.hashCode());
        return colors[hash % colors.length];
    }
}
