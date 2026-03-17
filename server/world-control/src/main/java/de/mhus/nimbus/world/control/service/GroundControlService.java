package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.BlockTypeType;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.*;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service to ensure ground layers have no holes.
 * Checks a chunk in a GROUND layer and fills gaps both vertically and horizontally,
 * using neighboring chunk edges as height references.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroundControlService {

    public static final int SIDE_NORTH = 1;
    public static final int SIDE_EAST = 2;
    public static final int SIDE_SOUTH = 4;
    public static final int SIDE_WEST = 8;
    public static final int SIDE_ALL = SIDE_NORTH | SIDE_EAST | SIDE_SOUTH | SIDE_WEST;
    public static final int SIDE_NONE = 0;

    public static final String GROUP_GROUND_CONTROL = "groundControl";

    private final WLayerService layerService;
    private final WWorldService worldService;
    private final WBlockTypeService blockTypeService;

    /**
     * Check and repair a ground chunk so the ground surface has no holes.
     *
     * @param worldId       World identifier
     * @param layerDataId   Layer data ID identifying the terrain layer
     * @param cx            Chunk X coordinate
     * @param cz            Chunk Z coordinate
     * @param sides         Bit flags indicating which neighbor edges to load (SIDE_NORTH, SIDE_EAST, SIDE_SOUTH, SIDE_WEST)
     * @param cleanupBlocks If true, remove all blocks below the ground surface
     * @return true if the chunk was modified, false if unchanged or aborted
     */
    @Transactional
    public boolean checkGround(String worldId, String layerDataId, int cx, int cz, int sides, boolean cleanupBlocks) {
        // Validate layer is GROUND type
        Optional<WLayer> layerOpt = layerService.findByWorldIdAndLayerDataId(worldId, layerDataId);
        if (layerOpt.isEmpty()) {
            log.warn("Layer not found: worldId={} layerDataId={}", worldId, layerDataId);
            return false;
        }
        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.GROUND) {
            log.warn("Layer is not GROUND type: layerDataId={} type={}", layerDataId, layer.getLayerType());
            return false;
        }

        // Load world for chunk size
        WWorld world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            log.warn("World not found: {}", worldId);
            return false;
        }
        int chunkSize = world.getPublicData().getChunkSize();

        // Resolve GROUND block type IDs for this world
        WorldId wid = WorldId.of(worldId).orElseThrow();
        Set<String> groundBlockTypeIds = resolveGroundBlockTypeIds(wid);
        if (groundBlockTypeIds.isEmpty()) {
            log.warn("No GROUND block types found for world: {} chunk {}:{}", worldId, cx, cz);
            return false;
        }

        // Load chunk data
        String chunkKey = cx + ":" + cz;
        Optional<LayerChunkData> chunkOpt = layerService.loadTerrainChunk(worldId, layerDataId, chunkKey);
        if (chunkOpt.isEmpty()) {
            log.debug("Chunk not found, nothing to check: {}", chunkKey);
            return false;
        }
        LayerChunkData chunkData = chunkOpt.get();

        // Check if chunk has at least one GROUND block
        boolean hasGround = false;
        for (LayerBlock lb : chunkData.getBlocks()) {
            if (lb.getBlock() != null && groundBlockTypeIds.contains(lb.getBlock().getBlockTypeId())) {
                hasGround = true;
                break;
            }
        }
        if (!hasGround) {
            log.warn("Chunk {} has no GROUND blocks, skipping", chunkKey);
            return false;
        }

        // Build height map of existing GROUND blocks: key "x,z" -> sorted set of Y values
        int worldXStart = cx * chunkSize;
        int worldZStart = cz * chunkSize;
        Map<String, TreeSet<Integer>> groundHeights = new LinkedHashMap<>();
        // Also track which block type is used at each position for filling
        Map<String, String> blockTypeAtPos = new HashMap<>();

        for (LayerBlock lb : chunkData.getBlocks()) {
            Block block = lb.getBlock();
            if (block == null || block.getPosition() == null) continue;
            if (!groundBlockTypeIds.contains(block.getBlockTypeId())) continue;

            int x = block.getPosition().getX();
            int z = block.getPosition().getZ();
            int y = block.getPosition().getY();
            String posKey = x + "," + z;
            groundHeights.computeIfAbsent(posKey, k -> new TreeSet<>()).add(y);
            blockTypeAtPos.putIfAbsent(posKey, block.getBlockTypeId());
        }

        // Build epoch fallback chain: collect GROUND layers sorted by epoch descending
        // so we can fall back to lower-epoch layers when neighbor chunks are missing
        List<WLayer> groundLayersByEpochDesc = resolveGroundLayerFallbackChain(worldId, layer);

        // Load neighbor edge heights
        Map<String, Integer> edgeHeights = loadEdgeHeights(worldId, layerDataId, cx, cz, chunkSize, sides, groundBlockTypeIds, groundLayersByEpochDesc);

        // Determine target height for each x,z position in the chunk
        // First pass: collect all known heights (from existing blocks + edges)
        Map<String, Integer> targetMinY = new LinkedHashMap<>();
        Map<String, Integer> targetMaxY = new LinkedHashMap<>();

        for (int lx = 0; lx < chunkSize; lx++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int wx = worldXStart + lx;
                int wz = worldZStart + lz;
                String posKey = wx + "," + wz;

                TreeSet<Integer> heights = groundHeights.get(posKey);
                if (heights != null && !heights.isEmpty()) {
                    targetMinY.put(posKey, heights.first());
                    targetMaxY.put(posKey, heights.last());
                }
            }
        }

        // Fill horizontal gaps by interpolating from neighbors and edges (Laplace interpolation)
        fillHorizontalGaps(targetMinY, targetMaxY, worldXStart, worldZStart, chunkSize, edgeHeights);

        // Extend columns vertically to close diagonal gaps between adjacent positions.
        // For a watertight heightmap surface, each column must reach down to the
        // lowest neighbor height. Without this, height differences between adjacent
        // columns create visible holes when viewed from the side.
        extendColumnsToNeighbors(targetMinY, targetMaxY, worldXStart, worldZStart, chunkSize, edgeHeights);

        // Determine a default block type for filling
        String defaultGroundBlockTypeId = findMostCommonBlockType(blockTypeAtPos, groundBlockTypeIds);

        // Build new block list
        List<LayerBlock> newBlocks = new ArrayList<>();

        // Fill vertical columns and create blocks
        for (int lx = 0; lx < chunkSize; lx++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int wx = worldXStart + lx;
                int wz = worldZStart + lz;
                String posKey = wx + "," + wz;

                Integer minY = targetMinY.get(posKey);
                Integer maxY = targetMaxY.get(posKey);
                if (minY == null || maxY == null) continue;

                String blockTypeId = blockTypeAtPos.getOrDefault(posKey, defaultGroundBlockTypeId);
                TreeSet<Integer> existingY = groundHeights.getOrDefault(posKey, new TreeSet<>());

                // Fill from minY to maxY
                for (int y = minY; y <= maxY; y++) {
                    if (!existingY.contains(y)) {
                        newBlocks.add(createGroundLayerBlock(wx, y, wz, blockTypeId));
                    }
                }
            }
        }

        // Retain existing GROUND blocks within range and non-GROUND blocks
        boolean modified = !newBlocks.isEmpty();
        List<LayerBlock> resultBlocks = new ArrayList<>();

        for (LayerBlock lb : chunkData.getBlocks()) {
            Block block = lb.getBlock();
            if (block == null || block.getPosition() == null) {
                resultBlocks.add(lb);
                continue;
            }

            int x = block.getPosition().getX();
            int y = block.getPosition().getY();
            int z = block.getPosition().getZ();

            if (groundBlockTypeIds.contains(block.getBlockTypeId())) {
                String posKey = x + "," + z;
                Integer minY = targetMinY.get(posKey);

                if (cleanupBlocks && minY != null && y < minY) {
                    // Below ground surface, remove
                    modified = true;
                    continue;
                }
                resultBlocks.add(lb);
            } else {
                if (cleanupBlocks) {
                    String posKey = x + "," + z;
                    Integer minY = targetMinY.get(posKey);
                    if (minY != null && y < minY) {
                        modified = true;
                        continue;
                    }
                }
                resultBlocks.add(lb);
            }
        }

        // Add fill blocks
        resultBlocks.addAll(newBlocks);

        if (!modified) {
            log.debug("Chunk {} unchanged", chunkKey);
            return false;
        }

        // Save updated chunk
        chunkData.setBlocks(resultBlocks);
        layerService.saveTerrainChunk(worldId, layerDataId, chunkKey, chunkData);
        log.info("Chunk {} repaired: {} blocks added/removed", chunkKey, newBlocks.size());
        return true;
    }

    /**
     * Check and repair all ground chunks belonging to a hex grid cell.
     * Finds the GROUND layer for the given epoch, determines all chunks
     * covered by the hex at (q, r), and runs checkGround on each.
     *
     * @param worldId World identifier
     * @param epoch   Epoch to find the GROUND layer for
     * @param q       Hex axial Q coordinate
     * @param r       Hex axial R coordinate
     * @return number of chunks that were modified
     */
    @Transactional
    public int checkHexGridGround(String worldId, int epoch, int q, int r) {
        // Load world
        WWorld world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            log.warn("World not found: {}", worldId);
            return 0;
        }

        int chunkSize = world.getPublicData().getChunkSize();
        int hexGridSize = world.getPublicData().getHexGridSize();

        // Find the GROUND layer for this epoch
        WLayer groundLayer = findGroundLayerForEpoch(worldId, epoch);
        if (groundLayer == null) {
            log.warn("No GROUND layer found for worldId={} epoch={}", worldId, epoch);
            return 0;
        }

        String layerDataId = groundLayer.getLayerDataId();

        // Get all chunk keys affected by this hex (all positions inside the hex mapped to chunks)
        HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();
        Set<String> chunkKeys = new HashSet<>();
        var posIterator = HexMathUtil.createFlatPositionIterator(hexPos, hexGridSize);
        while (posIterator.hasNext()) {
            var pos = posIterator.next();
            int cx2 = Math.floorDiv(pos.getX(), chunkSize);
            int cz2 = Math.floorDiv(pos.getZ(), chunkSize);
            chunkKeys.add(cx2 + ":" + cz2);
        }

        if (chunkKeys.isEmpty()) {
            log.debug("No affected chunks for hex ({},{}) in worldId={}", q, r, worldId);
            return 0;
        }

        log.info("checkHexGridGround: worldId={} epoch={} hex=({},{}) chunks={}", worldId, epoch, q, r, chunkKeys.size());

        int modified = 0;
        for (String chunkKey : chunkKeys) {
            String[] parts = chunkKey.split(":");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);

            if (checkGround(worldId, layerDataId, cx, cz, SIDE_ALL, true)) {
                modified++;
            }
        }

        log.info("checkHexGridGround completed: worldId={} hex=({},{}) modified={}/{}", worldId, q, r, modified, chunkKeys.size());
        return modified;
    }

    /**
     * Check and repair all existing chunks of a GROUND layer.
     *
     * @param worldId       World identifier
     * @param layerDataId   Layer data ID of the GROUND layer
     * @param sides         Bit flags for neighbor sides per chunk
     * @param cleanupBlocks If true, remove blocks below ground surface
     * @return number of chunks that were modified
     */
    @Transactional
    public int checkLayerGround(String worldId, String layerDataId, int sides, boolean cleanupBlocks) {
        // Validate layer
        Optional<WLayer> layerOpt = layerService.findByWorldIdAndLayerDataId(worldId, layerDataId);
        if (layerOpt.isEmpty()) {
            log.warn("Layer not found: worldId={} layerDataId={}", worldId, layerDataId);
            return 0;
        }
        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.GROUND) {
            log.warn("Layer is not GROUND type: layerDataId={} type={}", layerDataId, layer.getLayerType());
            return 0;
        }

        // Get all chunk keys for this layer
        List<String> chunkKeys = layerService.findTerrainChunkKeys(worldId, layerDataId);
        if (chunkKeys.isEmpty()) {
            log.debug("No chunks in layer: layerDataId={}", layerDataId);
            return 0;
        }

        log.info("checkLayerGround: worldId={} layerDataId={} chunks={} sides={} cleanup={}",
                worldId, layerDataId, chunkKeys.size(), sides, cleanupBlocks);

        int modified = 0;
        for (String chunkKey : chunkKeys) {
            String[] parts = chunkKey.split(":");
            if (parts.length != 2) {
                log.warn("Invalid chunkKey format: {}", chunkKey);
                continue;
            }
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);

            if (checkGround(worldId, layerDataId, cx, cz, sides, cleanupBlocks)) {
                modified++;
            }
        }

        log.info("checkLayerGround completed: worldId={} layerDataId={} modified={}/{}", worldId, layerDataId, modified, chunkKeys.size());
        return modified;
    }

    /**
     * Check and repair all GROUND layers of a world.
     *
     * @param worldId       World identifier
     * @param sides         Bit flags for neighbor sides per chunk
     * @param cleanupBlocks If true, remove blocks below ground surface
     * @return total number of chunks that were modified across all layers
     */
    @Transactional
    public int checkWorldGround(String worldId, int sides, boolean cleanupBlocks) {
        List<WLayer> allLayers = layerService.findLayersByWorld(worldId);
        List<WLayer> groundLayers = allLayers.stream()
                .filter(l -> l.getLayerType() == LayerType.GROUND)
                .filter(WLayer::isEnabled)
                .toList();

        if (groundLayers.isEmpty()) {
            log.warn("No GROUND layers found for worldId={}", worldId);
            return 0;
        }

        log.info("checkWorldGround: worldId={} groundLayers={} sides={} cleanup={}",
                worldId, groundLayers.size(), sides, cleanupBlocks);

        int totalModified = 0;
        for (WLayer layer : groundLayers) {
            int modified = checkLayerGround(worldId, layer.getLayerDataId(), sides, cleanupBlocks);
            totalModified += modified;
        }

        log.info("checkWorldGround completed: worldId={} totalModified={}", worldId, totalModified);
        return totalModified;
    }

    /**
     * Find the GROUND layer for a specific epoch.
     * Assumption: there is exactly one GROUND layer per epoch.
     *
     * @param worldId World identifier
     * @param epoch   Epoch number
     * @return the GROUND layer, or null if not found
     */
    private WLayer findGroundLayerForEpoch(String worldId, int epoch) {
        List<WLayer> layers = layerService.findByWorldId(worldId, epoch);
        return layers.stream()
                .filter(l -> l.getLayerType() == LayerType.GROUND)
                .filter(WLayer::isEnabled)
                .findFirst()
                .orElse(null);
    }

    /**
     * Load edge heights from neighboring chunks for reference.
     * If a neighbor chunk is not found in the current layer, falls back to
     * GROUND layers with lower epochs (relevant when layers at higher epochs
     * don't cover all chunks).
     *
     * @return map of "x,z" -> highest Y height for edge positions adjacent to the target chunk
     */
    private Map<String, Integer> loadEdgeHeights(String worldId, String layerDataId,
                                                  int cx, int cz, int chunkSize, int sides,
                                                  Set<String> groundBlockTypeIds,
                                                  List<WLayer> groundLayersByEpochDesc) {
        Map<String, Integer> edgeHeights = new HashMap<>();

        int worldXStart = cx * chunkSize;
        int worldZStart = cz * chunkSize;

        // North neighbor (cz - 1): take the south edge (last row, z = worldZStart - 1)
        if ((sides & SIDE_NORTH) != 0) {
            loadNeighborEdgeWithFallback(worldId, layerDataId, cx, cz - 1, chunkSize, groundBlockTypeIds, edgeHeights,
                    worldXStart, worldXStart + chunkSize - 1,
                    worldZStart - 1, worldZStart - 1,
                    groundLayersByEpochDesc);
        }

        // South neighbor (cz + 1): take the north edge (first row, z = worldZStart + chunkSize)
        if ((sides & SIDE_SOUTH) != 0) {
            loadNeighborEdgeWithFallback(worldId, layerDataId, cx, cz + 1, chunkSize, groundBlockTypeIds, edgeHeights,
                    worldXStart, worldXStart + chunkSize - 1,
                    worldZStart + chunkSize, worldZStart + chunkSize,
                    groundLayersByEpochDesc);
        }

        // West neighbor (cx - 1): take the east edge (last column, x = worldXStart - 1)
        if ((sides & SIDE_WEST) != 0) {
            loadNeighborEdgeWithFallback(worldId, layerDataId, cx - 1, cz, chunkSize, groundBlockTypeIds, edgeHeights,
                    worldXStart - 1, worldXStart - 1,
                    worldZStart, worldZStart + chunkSize - 1,
                    groundLayersByEpochDesc);
        }

        // East neighbor (cx + 1): take the west edge (first column, x = worldXStart + chunkSize)
        if ((sides & SIDE_EAST) != 0) {
            loadNeighborEdgeWithFallback(worldId, layerDataId, cx + 1, cz, chunkSize, groundBlockTypeIds, edgeHeights,
                    worldXStart + chunkSize, worldXStart + chunkSize,
                    worldZStart, worldZStart + chunkSize - 1,
                    groundLayersByEpochDesc);
        }

        return edgeHeights;
    }

    /**
     * Extend each column's minY downward so adjacent columns overlap vertically.
     * For a watertight voxel heightmap, each column must extend down to the lowest
     * of its 4 direct neighbors' maxY. This prevents diagonal holes between columns
     * at different heights.
     *
     * Also considers edge heights from neighboring chunks for boundary positions.
     */
    private void extendColumnsToNeighbors(Map<String, Integer> targetMinY, Map<String, Integer> targetMaxY,
                                           int worldXStart, int worldZStart, int chunkSize,
                                           Map<String, Integer> edgeHeights) {
        // Merge edge heights with targetMaxY for a combined reference
        Map<String, Integer> allHeights = new HashMap<>(edgeHeights);
        allHeights.putAll(targetMaxY);

        for (int lx = 0; lx < chunkSize; lx++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int wx = worldXStart + lx;
                int wz = worldZStart + lz;
                String posKey = wx + "," + wz;

                Integer maxY = targetMaxY.get(posKey);
                if (maxY == null) continue;

                // Find lowest neighbor height (4 direct neighbors)
                int lowestNeighbor = maxY;
                Integer n;
                n = allHeights.get((wx - 1) + "," + wz);
                if (n != null && n < lowestNeighbor) lowestNeighbor = n;
                n = allHeights.get((wx + 1) + "," + wz);
                if (n != null && n < lowestNeighbor) lowestNeighbor = n;
                n = allHeights.get(wx + "," + (wz - 1));
                if (n != null && n < lowestNeighbor) lowestNeighbor = n;
                n = allHeights.get(wx + "," + (wz + 1));
                if (n != null && n < lowestNeighbor) lowestNeighbor = n;

                // Extend minY down to lowest neighbor
                Integer currentMinY = targetMinY.get(posKey);
                if (currentMinY == null || lowestNeighbor < currentMinY) {
                    targetMinY.put(posKey, lowestNeighbor);
                }
            }
        }
    }

    /**
     * Try to load neighbor edge from the current layer first, then fall back
     * to GROUND layers with lower epochs if not found.
     */
    private void loadNeighborEdgeWithFallback(String worldId, String layerDataId,
                                               int neighborCx, int neighborCz,
                                               int chunkSize, Set<String> groundBlockTypeIds,
                                               Map<String, Integer> edgeHeights,
                                               int filterXMin, int filterXMax,
                                               int filterZMin, int filterZMax,
                                               List<WLayer> groundLayersByEpochDesc) {
        String neighborKey = neighborCx + ":" + neighborCz;

        // Try current layer first
        if (extractEdgeFromChunk(worldId, layerDataId, neighborKey, groundBlockTypeIds, edgeHeights,
                filterXMin, filterXMax, filterZMin, filterZMax)) {
            return;
        }

        // Neighbor chunk not found in current layer – fall back through lower-epoch GROUND layers
        for (WLayer fallbackLayer : groundLayersByEpochDesc) {
            if (fallbackLayer.getLayerDataId().equals(layerDataId)) continue; // skip self
            if (extractEdgeFromChunk(worldId, fallbackLayer.getLayerDataId(), neighborKey, groundBlockTypeIds, edgeHeights,
                    filterXMin, filterXMax, filterZMin, filterZMax)) {
                log.debug("Neighbor {} found in fallback layer {} (epoch {})", neighborKey,
                        fallbackLayer.getName(), fallbackLayer.getEpoches());
                return;
            }
        }

        log.debug("Neighbor chunk {} not found in any GROUND layer", neighborKey);
    }

    /**
     * Extract edge block heights from a terrain chunk.
     *
     * @return true if the chunk was found (even if no matching edge blocks), false if chunk not found
     */
    private boolean extractEdgeFromChunk(String worldId, String layerDataId, String chunkKey,
                                          Set<String> groundBlockTypeIds,
                                          Map<String, Integer> edgeHeights,
                                          int filterXMin, int filterXMax,
                                          int filterZMin, int filterZMax) {
        Optional<LayerChunkData> chunkOpt = layerService.loadTerrainChunk(worldId, layerDataId, chunkKey);
        if (chunkOpt.isEmpty()) return false;

        LayerChunkData chunkData = chunkOpt.get();
        for (LayerBlock lb : chunkData.getBlocks()) {
            Block block = lb.getBlock();
            if (block == null || block.getPosition() == null) continue;
            if (!groundBlockTypeIds.contains(block.getBlockTypeId())) continue;

            int x = block.getPosition().getX();
            int z = block.getPosition().getZ();
            int y = block.getPosition().getY();

            if (x >= filterXMin && x <= filterXMax && z >= filterZMin && z <= filterZMax) {
                String posKey = x + "," + z;
                Integer existing = edgeHeights.get(posKey);
                if (existing == null || y > existing) {
                    edgeHeights.put(posKey, y);
                }
            }
        }
        return true;
    }

    /**
     * Build a fallback chain of GROUND layers sorted by epoch descending.
     * Assumption: there is exactly one GROUND layer per epoch.
     * Layers with higher epochs come first so that when iterating for fallback,
     * we naturally proceed from higher to lower epochs.
     * Only includes layers whose max epoch is lower than the current layer's max epoch.
     */
    private List<WLayer> resolveGroundLayerFallbackChain(String worldId, WLayer currentLayer) {
        int currentMaxEpoch = currentLayer.getEpoches().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (currentMaxEpoch == 0) {
            return List.of(); // lowest epoch, no fallback needed
        }

        List<WLayer> allLayers = layerService.findLayersByWorld(worldId);
        return allLayers.stream()
                .filter(l -> l.getLayerType() == LayerType.GROUND)
                .filter(l -> l.isEnabled())
                .filter(l -> !l.getLayerDataId().equals(currentLayer.getLayerDataId()))
                .filter(l -> {
                    int maxEpoch = l.getEpoches().stream().mapToInt(Integer::intValue).max().orElse(0);
                    return maxEpoch < currentMaxEpoch;
                })
                .sorted((a, b) -> {
                    int epochA = a.getEpoches().stream().mapToInt(Integer::intValue).max().orElse(0);
                    int epochB = b.getEpoches().stream().mapToInt(Integer::intValue).max().orElse(0);
                    return Integer.compare(epochB, epochA); // descending
                })
                .toList();
    }

    /**
     * Fill horizontal gaps in the height map by interpolating from neighbors and edge heights.
     * For positions without any GROUND blocks, estimate height from adjacent positions.
     */
    private void fillHorizontalGaps(Map<String, Integer> targetMinY, Map<String, Integer> targetMaxY,
                                     int worldXStart, int worldZStart, int chunkSize,
                                     Map<String, Integer> edgeHeights) {

        // Merge edge heights into a combined lookup for reference
        // Edge heights provide the reference Y for chunk boundary positions
        Map<String, Integer> referenceHeights = new HashMap<>(edgeHeights);
        for (var entry : targetMaxY.entrySet()) {
            referenceHeights.put(entry.getKey(), entry.getValue());
        }

        // Iterative flood-fill: spread heights to missing positions from known neighbors
        boolean changed = true;
        int maxIterations = chunkSize * 2; // prevent infinite loops
        int iteration = 0;

        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;

            for (int lx = 0; lx < chunkSize; lx++) {
                for (int lz = 0; lz < chunkSize; lz++) {
                    int wx = worldXStart + lx;
                    int wz = worldZStart + lz;
                    String posKey = wx + "," + wz;

                    if (targetMinY.containsKey(posKey)) continue;

                    // Look at 4 direct neighbors (including edges)
                    List<Integer> neighborYs = new ArrayList<>(4);
                    addIfPresent(neighborYs, referenceHeights, (wx - 1) + "," + wz);
                    addIfPresent(neighborYs, referenceHeights, (wx + 1) + "," + wz);
                    addIfPresent(neighborYs, referenceHeights, wx + "," + (wz - 1));
                    addIfPresent(neighborYs, referenceHeights, wx + "," + (wz + 1));

                    if (!neighborYs.isEmpty()) {
                        // Use average of neighbors as fill height
                        int avgY = (int) Math.round(neighborYs.stream().mapToInt(Integer::intValue).average().orElse(0));
                        targetMinY.put(posKey, avgY);
                        targetMaxY.put(posKey, avgY);
                        referenceHeights.put(posKey, avgY);
                        changed = true;
                    }
                }
            }
        }
    }

    private void addIfPresent(List<Integer> list, Map<String, Integer> map, String key) {
        Integer val = map.get(key);
        if (val != null) {
            list.add(val);
        }
    }

    /**
     * Find the most commonly used block type ID among existing blocks.
     */
    private String findMostCommonBlockType(Map<String, String> blockTypeAtPos, Set<String> groundBlockTypeIds) {
        Map<String, Integer> counts = new HashMap<>();
        for (String btId : blockTypeAtPos.values()) {
            counts.merge(btId, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(groundBlockTypeIds.iterator().next());
    }

    /**
     * Resolve all block type IDs with type GROUND for the given world.
     */
    private Set<String> resolveGroundBlockTypeIds(WorldId worldId) {
        Set<String> ids = new HashSet<>();
        List<WBlockType> blockTypes = blockTypeService.lookupBlockTypes(worldId);
        for (WBlockType bt : blockTypes) {
            if (bt.isEnabled() && bt.getPublicData() != null && bt.getPublicData().getType() == BlockTypeType.GROUND) {
                ids.add(bt.getBlockId());
            }
        }
        return ids;
    }

    /**
     * Create a LayerBlock with a GROUND block at the given position.
     */
    private LayerBlock createGroundLayerBlock(int x, int y, int z, String blockTypeId) {
        Block block = Block.builder()
                .position(Vector3Int.builder().x(x).y(y).z(z).build())
                .blockTypeId(blockTypeId)
                .build();
        return LayerBlock.builder()
                .block(block)
                .group(GROUP_GROUND_CONTROL)
                .build();
    }
}
