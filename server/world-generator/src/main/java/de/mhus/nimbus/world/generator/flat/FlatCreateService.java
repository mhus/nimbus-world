package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for creating WFlat instances.
 * Handles initialization of flat terrain data structures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FlatCreateService {

    private final WWorldService worldService;
    private final WFlatService flatService;
    private final WLayerService layerService;
    private final WBlockTypeService blockTypeService;
    private final WChunkService chunkService;

    /**
     * Create a new WFlat instance with initialized size.
     * Ocean level is loaded from world info.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @param mountX Mount X position
     * @param mountZ Mount Z position
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance
     * @throws IllegalArgumentException if world not found or parameters invalid
     */
    public WFlat createFlat(String worldId, String layerDataId, String flatId,
                           int sizeX, int sizeZ, int mountX, int mountZ,
                           String title, String description) {
        log.debug("Creating flat: worldId={}, layerDataId={}, flatId={}, size={}x{}, mount=({},{}), title={}, description={}",
                worldId, layerDataId, flatId, sizeX, sizeZ, mountX, mountZ, title, description);

        // Load world to get ocean level
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            throw new IllegalArgumentException("World not found: " + worldId);
        }

        WWorld world = worldOpt.get();
        int oceanLevel = world.getOceanLevel() != null ? world.getOceanLevel() : 0;
        String oceanBlockId = world.getSeaBlockType();

        log.debug("Loaded world settings: oceanLevel={}, oceanBlockId={}", oceanLevel, oceanBlockId);

        // Build WFlat instance
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layerDataId)
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(HexMathUtil.getDominantHexForArea(
                        world, TypeUtil.area(mountX, mountZ, sizeX, sizeZ)
                ))
                .build();

        // Initialize with size
        flat.initWithSize(sizeX, sizeZ);

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("Created flat: id={}, worldId={}, layerDataId={}, flatId={}, size={}x{}, title={}",
                saved.getId(), worldId, layerDataId, flatId, sizeX, sizeZ, title);

        return saved;
    }

    /**
     * Create a new WFlat instance with default mount position (0,0).
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @return Created and persisted WFlat instance
     * @throws IllegalArgumentException if world not found or parameters invalid
     */
    public WFlat createFlat(String worldId, String layerDataId, String flatId, int sizeX, int sizeZ) {
        return createFlat(worldId, layerDataId, flatId, sizeX, sizeZ, 0, 0, null, null);
    }

    /**
     * Import WFlat from a WLayer of type GROUND.
     * Scans all blocks in the layer and finds the highest ground block for each column.
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import from
     * @param flatId Flat identifier for the new WFlat
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @param mountX Mount X position (where flat starts in world coordinates)
     * @param mountZ Mount Z position (where flat starts in world coordinates)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with imported height data
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat importFromLayer(String worldId, String layerName, String flatId,
                                 int sizeX, int sizeZ, int mountX, int mountZ,
                                 String title, String description) {
        log.info("Importing flat from layer: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), title={}, description={}",
                worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, title, description);

        var worldIdObj = WorldId.of(worldId).orElseThrow();
        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Check if flat already exists and delete it (in case of retry)
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before re-import: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level and block from world
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance (without persisting yet)
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(HexMathUtil.getDominantHexForArea(
                        world, TypeUtil.area(mountX, mountZ, sizeX, sizeZ)
                ))
                .build();

        // Initialize with size
        flat.initWithSize(sizeX, sizeZ);
        int defaultLevel = oceanLevel - 10; // Default level if no blocks found
        int chunkSize = world.getPublicData().getChunkSize();

        // Calculate which chunks are needed (optimize: only load required chunks once)
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for importFromLayer", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, ChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldIdObj, chunkKey, false);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int importedColumns = 0;
        int emptyColumns = 0;

        // Iterate over all X,Z coordinates in the flat
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = TypeUtil.toStringChunkCoord(chunkX, chunkZ);

                // Get chunk from cache
                ChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, use default level
                    flat.setLevel(localX, localZ, defaultLevel);
                    emptyColumns++;
                    continue;
                }

                // Find highest ground block at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, chunkSize);

                if (groundLevel == -1) {
                    // No ground block found, use default level
                    flat.setLevel(localX, localZ, defaultLevel);
                    emptyColumns++;
                } else {
                    flat.setLevel(localX, localZ, groundLevel);
                    importedColumns++;
                }
            }
        }

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("Import complete: flatId={}, imported={} columns, empty={} columns",
                flatId, importedColumns, emptyColumns);

        return saved;
    }

    /**
     * Find the ground level (Y coordinate of highest ground block) at a specific world position.
     * Uses heightData from chunk for fast lookup.
     *
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param chunkData Chunk data containing heightData
     * @param chunkSize Size of chunks in this world
     * @return Y coordinate of ground surface, or -1 if not found
     */
    private int findGroundLevel(int worldX, int worldZ, ChunkData chunkData, int chunkSize) {
        if (chunkData == null) {
            return -1;
        }

        // Get heightData from chunk
        var heightData = chunkData.getHeightData();
        if (heightData == null || heightData.isEmpty()) {
            return -1;
        }

        // Build key for heightData map using world coordinates: "worldX,worldZ"
        String key = worldX + "," + worldZ;
        int[] heights = heightData.get(key);

        if (heights == null || heights.length < 3) {
            return -1;
        }

        // heightData format: [maxHeight, minHeight, groundLevel, waterLevel?]
        // Return groundLevel (index 2)
        return heights[2];
    }

    /**
     * Create an empty WFlat with BEDROCK material and border imported from layer.
     * Creates a flat terrain with all cells at level 0 and BEDROCK material.
     * Border cells (outer edge) have their height imported from the layer for seamless transitions.
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import border from
     * @param flatId Flat identifier for the new WFlat
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @param mountX Mount X position (where flat starts in world coordinates)
     * @param mountZ Mount Z position (where flat starts in world coordinates)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with BEDROCK material
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat createEmptyFlat(String worldId, String layerName, String flatId,
                                 int sizeX, int sizeZ, int mountX, int mountZ,
                                 String title, String description) {
        log.info("Creating empty flat with border from layer: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), title={}, description={}",
                worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, title, description);

        var worldIdObj = WorldId.of(worldId).orElseThrow();
        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Check if flat already exists and delete it (in case of retry)
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before creation: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level and block from world
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance (without persisting yet)
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(HexMathUtil.getDominantHexForArea(
                        world, TypeUtil.area(mountX, mountZ, sizeX, sizeZ)
                ))
                .build();

        // Initialize with size (sets all levels to 0)
        flat.initWithSize(sizeX, sizeZ);
        int chunkSize = world.getPublicData().getChunkSize();

        // Set all cells to BEDROCK material
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                flat.setColumn(localX, localZ, FlatMaterialService.BEDROCK);
            }
        }

        // Calculate which chunks are needed for border import
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for border import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, ChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldIdObj, chunkKey, false);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int borderCellsImported = 0;

        // Import border cells from layer (outer edge only)
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Check if this is a border cell
                boolean isBorder = (localX == 0 || localX == sizeX - 1 ||
                                   localZ == 0 || localZ == sizeZ - 1);

                if (!isBorder) {
                    continue; // Skip interior cells
                }

                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = TypeUtil.toStringChunkCoord(chunkX, chunkZ);

                // Get chunk from cache
                ChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, keep default level 0
                    continue;
                }

                // Find ground level at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, chunkSize);

                if (groundLevel != -1) {
                    // Set level from layer for border cell
                    flat.setLevel(localX, localZ, groundLevel);
                    borderCellsImported++;
                }
            }
        }

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("Empty flat creation complete: flatId={}, size={}x{}, borderCells={}",
                flatId, sizeX, sizeZ, borderCellsImported);

        return saved;
    }

    /**
     * Create a WFlat for a HexGrid area with auto-calculated size and mount positions.
     * Automatically calculates sizeX, sizeZ, mountX, mountZ from hex grid coordinates.
     * Creates a flat terrain where:
     * - Positions inside the HexGrid are marked with material 255 (NOT_SET_MUTABLE)
     * - Positions outside the HexGrid (corners) are marked with material 0 (NOT_SET)
     * - unknownProtected is set to true (only HexGrid positions can be modified)
     * - The hexGrid coordinate is stored in the WFlat
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import outside positions from
     * @param flatId Flat identifier for the new WFlat
     * @param hexQ HexGrid Q coordinate (axial)
     * @param hexR HexGrid R coordinate (axial)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with HexGrid area
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat createHexGridFlat(String worldId, String layerName, String flatId,
                                   int hexQ, int hexR, String title, String description) {
        log.info("Creating HexGrid flat (auto-size): worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}",
                worldId, layerName, flatId, hexQ, hexR, title, description);

        // Load world to get hexGridSize
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        log.debug("Loaded hexGridSize={} from world", gridSize);

        // Calculate hexagon center in cartesian coordinates
        HexVector2 hexVec = HexVector2.builder().q(hexQ).r(hexR).build();
        double[] center = HexMathUtil.hexToCartesian(hexVec, gridSize);
        double centerX = center[0];
        double centerZ = center[1];

        // Calculate bounding box for pointy-top hexagon with 10-pixel border on each side
        // gridSize is the diameter (not radius!)
        // Radius = gridSize / 2
        // Height (point to point) = 2 * radius = gridSize
        // Width (flat side to flat side) = sqrt(3) * radius = gridSize * sqrt(3) / 2
        double SQRT_3 = Math.sqrt(3.0);
        int sizeX = (int) Math.ceil(gridSize * SQRT_3 / 2.0) + 30;  // +30: +10 safety margin + 20 border (10 per side)
        int sizeZ = gridSize + 30;  // +30: +10 safety margin + 20 border (10 per side)

        // Calculate mount position (top-left corner of bounding box)
        // Shift mount position by 10 pixels to the left and up to accommodate border
        int mountX = (int) Math.floor(centerX - sizeX / 2.0) - 10;
        int mountZ = (int) Math.floor(centerZ - sizeZ / 2.0) - 10;

        log.info("Calculated flat parameters: sizeX={}, sizeZ={}, mount=({},{}), hexCenter=({},{})",
                sizeX, sizeZ, mountX, mountZ, centerX, centerZ);

        // Delegate to existing method with calculated parameters
        return createHexGridFlat(worldId, layerName, flatId,
                sizeX, sizeZ, mountX, mountZ,
                hexQ, hexR, title, description);
    }

    /**
     * Create a WFlat for a HexGrid area with NOT_SET_MUTABLE material inside the hex.
     * Creates a flat terrain where:
     * - Positions inside the HexGrid are marked with material 255 (NOT_SET_MUTABLE) at level 0
     * - Positions outside the HexGrid (corners) are marked with material 0 (NOT_SET)
     * - unknownProtected is set to true (only HexGrid positions can be modified)
     * - The hexGrid coordinate is stored in the WFlat
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import outside positions from
     * @param flatId Flat identifier for the new WFlat
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @param mountX Mount X position (where flat starts in world coordinates)
     * @param mountZ Mount Z position (where flat starts in world coordinates)
     * @param hexQ HexGrid Q coordinate (axial)
     * @param hexR HexGrid R coordinate (axial)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with HexGrid area
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat createHexGridFlat(String worldId, String layerName, String flatId,
                                   int sizeX, int sizeZ, int mountX, int mountZ,
                                   int hexQ, int hexR, String title, String description) {
        log.info("Creating HexGrid flat: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), hex=({},{}), title={}, description={}",
                worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, hexQ, hexR, title, description);

        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Get hexGridSize from world
        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        log.debug("Using hexGridSize={} from world", gridSize);

        // Check if flat already exists and delete it (in case of retry)
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before creation: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level and block from world
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance (without persisting yet)
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(TypeUtil.hexVector2(hexQ, hexR))
                .build();

        // Initialize with size (sets all levels to 0)
        flat.initWithSize(sizeX, sizeZ);
        int chunkSize = world.getPublicData().getChunkSize();

        // Calculate hex center in cartesian coordinates
        de.mhus.nimbus.generated.types.HexVector2 hexPosition =
                de.mhus.nimbus.generated.types.HexVector2.builder().q(hexQ).r(hexR).build();
        double[] hexCenter = HexMathUtil.hexToCartesian(hexPosition, gridSize);
        double hexCenterX = hexCenter[0];
        double hexCenterZ = hexCenter[1];

        log.debug("Hex center in cartesian: ({}, {})", hexCenterX, hexCenterZ);

        // Calculate which chunks are needed for layer import
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for layer import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getWorldId(), layer.getLayerDataId(), chunkKey);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int hexCellsSet = 0;
        int outsideCellsImported = 0;

        // Process each cell in the flat
        // The hex grid is positioned with a 10-pixel offset to allow for border connections
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Adjust coordinates for hex grid check: hex is positioned 10 pixels into the flat
                // to allow for a 10-pixel border on each side for connections
                int hexCheckX = worldX + 10;
                int hexCheckZ = worldZ + 10;

                // Check if this position is inside the HexGrid
                boolean isInHex = HexMathUtil.isPointInHex(hexCheckX, hexCheckZ, hexCenterX, hexCenterZ, gridSize);

                if (isInHex) {
                    // Position is inside HexGrid: mark with NOT_SET_MUTABLE (255) at level 0
                    flat.setLevel(localX, localZ, 0);
                    flat.setColumn(localX, localZ, WFlat.MATERIAL_NOT_SET_MUTABLE);
                    hexCellsSet++;
                } else {
                    // Position is outside HexGrid (corner): mark with NOT_SET (0)
                    // Keep level at 0 and set material to NOT_SET
                    flat.setLevel(localX, localZ, 0);
                    flat.setColumn(localX, localZ, WFlat.MATERIAL_NOT_SET);
                    outsideCellsImported++;
                }
            }
        }

        flat.setUnknownProtected(true);
        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("HexGrid flat creation complete: flatId={}, size={}x{}, hexCells={} (material=NOT_SET_MUTABLE/255), outsideCorners={} (material=NOT_SET/0), unknownProtected=true",
                flatId, sizeX, sizeZ, hexCellsSet, outsideCellsImported);

        return saved;
    }

    /**
     * Import WFlat from layer for a HexGrid area with auto-calculated size and mount positions.
     * Automatically calculates sizeX, sizeZ, mountX, mountZ from hex grid coordinates.
     * Imports all columns from the layer, sets them to material 255 (NOT_SET_MUTABLE) if inside hex,
     * and sets positions outside the HexGrid to material 0 (NOT_SET).
     * Sets unknownProtected = true (only HexGrid positions can be modified).
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import from
     * @param flatId Flat identifier for the new WFlat
     * @param hexQ HexGrid Q coordinate (axial)
     * @param hexR HexGrid R coordinate (axial)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with HexGrid protection
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat importHexGridFlat(String worldId, String layerName, String flatId,
                                   int hexQ, int hexR, String title, String description) {
        log.info("Importing HexGrid flat (auto-size): worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}",
                worldId, layerName, flatId, hexQ, hexR, title, description);

        // Load world to get hexGridSize
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        log.debug("Loaded hexGridSize={} from world", gridSize);

        // Calculate hexagon center in cartesian coordinates
        HexVector2 hexVec = HexVector2.builder().q(hexQ).r(hexR).build();
        double[] center = HexMathUtil.hexToCartesian(hexVec, gridSize);
        double centerX = center[0];
        double centerZ = center[1];

        // Calculate bounding box for pointy-top hexagon with 10-pixel border on each side
        // gridSize is the diameter (not radius!)
        // Radius = gridSize / 2
        // Height (point to point) = 2 * radius = gridSize
        // Width (flat side to flat side) = sqrt(3) * radius = gridSize * sqrt(3) / 2
        double SQRT_3 = Math.sqrt(3.0);
        int sizeX = (int) Math.ceil(gridSize * SQRT_3 / 2.0) + 30;  // +30: +10 safety margin + 20 border (10 per side)
        int sizeZ = gridSize + 30;  // +30: +10 safety margin + 20 border (10 per side)

        // Calculate mount position (top-left corner of bounding box)
        // Shift mount position by 10 pixels to the left and up to accommodate border
        int mountX = (int) Math.floor(centerX - sizeX / 2.0) - 10;
        int mountZ = (int) Math.floor(centerZ - sizeZ / 2.0) - 10;

        log.info("Calculated flat parameters: sizeX={}, sizeZ={}, mount=({},{}), hexCenter=({},{})",
                sizeX, sizeZ, mountX, mountZ, centerX, centerZ);

        // Delegate to existing method with calculated parameters
        return importHexGridFlat(worldId, layerName, flatId,
                sizeX, sizeZ, mountX, mountZ,
                hexQ, hexR, title, description);
    }

    /**
     * Import WFlat from layer for a HexGrid area.
     * Imports all columns from the layer, sets them to material 255 (UNKNOWN_NOT_PROTECTED),
     * then sets positions outside the HexGrid to material 0 (UNKNOWN_PROTECTED).
     * Sets unknownProtected = true (only HexGrid positions can be modified).
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import from
     * @param flatId Flat identifier for the new WFlat
     * @param sizeX Width of the flat (1-800)
     * @param sizeZ Height of the flat (1-800)
     * @param mountX Mount X position (where flat starts in world coordinates)
     * @param mountZ Mount Z position (where flat starts in world coordinates)
     * @param hexQ HexGrid Q coordinate (axial)
     * @param hexR HexGrid R coordinate (axial)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with HexGrid protection
     * @throws IllegalArgumentException if world or layer not found, or layer is not GROUND type
     */
    public WFlat importHexGridFlat(String worldId, String layerName, String flatId,
                                   int sizeX, int sizeZ, int mountX, int mountZ,
                                   int hexQ, int hexR, String title, String description) {
        log.info("Importing HexGrid flat: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), hex=({},{}), title={}, description={}",
                worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, hexQ, hexR, title, description);

        var worldIdObj = WorldId.of(worldId).orElseThrow();

        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Get hexGridSize from world
        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        log.debug("Using hexGridSize={} from world", gridSize);

        // Check if flat already exists and delete it (in case of retry)
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before import: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level and block from world
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance (without persisting yet)
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(TypeUtil.hexVector2(hexQ, hexR)
                )
                .build();

        // Initialize with size
        flat.initWithSize(sizeX, sizeZ);
        int chunkSize = world.getPublicData().getChunkSize();
        int defaultLevel = oceanLevel - 10; // Default level if no blocks found

        // Calculate hex center in cartesian coordinates
        de.mhus.nimbus.generated.types.HexVector2 hexPosition =
                de.mhus.nimbus.generated.types.HexVector2.builder().q(hexQ).r(hexR).build();
        double[] hexCenter = HexMathUtil.hexToCartesian(hexPosition, gridSize);
        double hexCenterX = hexCenter[0];
        double hexCenterZ = hexCenter[1];

        log.debug("Hex center in cartesian: ({}, {})", hexCenterX, hexCenterZ);

        // Calculate which chunks are needed for layer import
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for importHexGridFlat", requiredChunkKeys.size());

        // Load all required chunks at once
//        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        java.util.Map<String, ChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldIdObj, chunkKey, false);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int importedColumns = 0;
        int outsideColumns = 0;
        int emptyColumns = 0;

        long pointCnt = sizeX * sizeZ;
        // Step 1: Import ALL columns from layer and set material to 255
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = TypeUtil.toStringChunkCoord(chunkX, chunkZ);

                // Get chunk from cache
                ChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, use default level and material 255
                    flat.setLevel(localX, localZ, defaultLevel);
                    flat.setColumn(localX, localZ, 255); // Material 255 (UNKNOWN_NOT_PROTECTED)
                    emptyColumns++;
                    continue;
                }

                // Find highest ground block at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, chunkSize);

                if (groundLevel == -1) {
                    // No ground block found, use default level
                    flat.setLevel(localX, localZ, defaultLevel);
                    emptyColumns++;
                } else {
                    flat.setLevel(localX, localZ, groundLevel);
                    importedColumns++;
                }

                // Set material to 255 (UNKNOWN_NOT_PROTECTED) for ALL columns initially
                flat.setColumn(localX, localZ, 255);
            }
        }

        // Step 2: Set positions OUTSIDE HexGrid to material 0 (UNKNOWN_PROTECTED)
        // The hex grid is positioned with a 10-pixel offset to allow for border connections
        log.debug("Step 2: Setting outside positions to material 0. HexCenter: ({}, {}), gridSize: {}", hexCenterX, hexCenterZ, gridSize);
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Adjust coordinates for hex grid check: hex is positioned 10 pixels into the flat
                // to allow for a 10-pixel border on each side for connections
                int hexCheckX = worldX + 10;
                int hexCheckZ = worldZ + 10;

                // Check if this position is inside the HexGrid
                boolean isInHex = HexMathUtil.isPointInHex(hexCheckX, hexCheckZ, hexCenterX, hexCenterZ, gridSize);

                if (!isInHex) {
                    // Position is outside HexGrid: Set material to 0 (UNKNOWN_PROTECTED)
                    flat.setColumn(localX, localZ, 0); // Material 0 = NOT_SET
                    outsideColumns++;
                }
            }
        }

        log.info("HexGrid flat import material summary: total={}, inside (255)={}, outside (0)={}",
                sizeX * sizeZ, (sizeX * sizeZ) - outsideColumns, outsideColumns);

        // after import, set protection
        flat.setUnknownProtected(true);

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("HexGrid flat import complete: flatId={}, imported={} columns, empty={} columns, outside={} columns, unknownProtected=true",
                flatId, importedColumns, emptyColumns, outsideColumns);

        return saved;
    }

    /**
     * Update border of an existing WFlat by reimporting from layer.
     * Updates only the border cells (outer edge) of the flat with data from the layer.
     * The border is the outer edge: localX=0, localX=sizeX-1, localZ=0, localZ=sizeZ-1.
     *
     * @param worldId World ID
     * @param layerName Name of the GROUND layer to import border from
     * @param flatId ID of the existing WFlat to update
     * @return Updated WFlat
     * @throws IllegalArgumentException if flat or layer not found or invalid
     */
    public WFlat updateBorder(String worldId, String layerName, String flatId) {
        log.info("Updating border for flat: worldId={}, layerName={}, flatId={}", worldId, layerName, flatId);

        // Load layer first to get layerDataId
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Load existing flat
        WFlat flat = flatService.findByWorldIdAndLayerDataIdAndFlatId(worldId, layer.getLayerDataId(), flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        int mountX = flat.getMountX();
        int mountZ = flat.getMountZ();
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        var worldIdObj = WorldId.of(worldId).orElseThrow();
        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));
        int chunkSize = world.getPublicData().getChunkSize();

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Calculate which chunks are needed for border import
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for border update", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, ChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
//            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldIdObj, chunkKey, false);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int borderCellsUpdated = 0;

        // Update border cells from layer (outer edge only)
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Check if this is a border cell
                boolean isBorder = (localX == 0 || localX == sizeX - 1 ||
                                   localZ == 0 || localZ == sizeZ - 1);

                if (!isBorder) {
                    continue; // Skip interior cells
                }

                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = TypeUtil.toStringChunkCoord(chunkX, chunkZ);

                // Get chunk from cache
                ChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, skip this cell
                    continue;
                }

                // Find ground level at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, chunkSize);

                if (groundLevel != -1) {
                    // Update level from layer for border cell
                    flat.setLevel(localX, localZ, groundLevel);
                    borderCellsUpdated++;
                }
            }
        }

        // Persist changes to database
        flat.touchUpdate();
        WFlat updated = flatService.update(flat);

        log.info("Border update complete: flatId={}, borderCellsUpdated={}", flatId, borderCellsUpdated);

        return updated;
    }

    /**
     * Create an empty HexGrid flat without any terrain initialization.
     * Creates a flat with all levels set to 0 (default) and no materials set.
     * This is a completely blank HexGrid ready for terrain generation.
     * - Positions inside the HexGrid: level 0, material 255 (NOT_SET_MUTABLE)
     * - Positions outside the HexGrid (corners): level 0, material 0 (NOT_SET)
     * - unknownProtected = true
     *
     * @param worldId World identifier
     * @param layerName Name of the layer (for layerDataId)
     * @param flatId Flat identifier for the new WFlat
     * @param hexQ HexGrid Q coordinate (axial)
     * @param hexR HexGrid R coordinate (axial)
     * @param title Optional title for the flat
     * @param description Optional description for the flat
     * @return Created and persisted WFlat instance with empty HexGrid
     * @throws IllegalArgumentException if world or layer not found
     */
    public WFlat createEmptyHexGridFlat(String worldId, String layerName, String flatId,
                                        int hexQ, int hexR, String title, String description) {
        log.info("Creating empty HexGrid flat: worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}",
                worldId, layerName, flatId, hexQ, hexR, title, description);

        // Load world to get hexGridSize
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        // Load layer to get layerDataId
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        log.debug("Loaded hexGridSize={} from world", gridSize);

        // Calculate hexagon center in cartesian coordinates
        HexVector2 hexVec = HexVector2.builder().q(hexQ).r(hexR).build();
        double[] center = HexMathUtil.hexToCartesian(hexVec, gridSize);
        double centerX = center[0];
        double centerZ = center[1];

        // Calculate bounding box for pointy-top hexagon with 10-pixel border on each side
        double SQRT_3 = Math.sqrt(3.0);
        int sizeX = (int) Math.ceil(gridSize * SQRT_3 / 2.0) + 30;  // +30: +10 safety margin + 20 border (10 per side)
        int sizeZ = gridSize + 30;  // +30: +10 safety margin + 20 border (10 per side)

        // Calculate mount position (top-left corner of bounding box)
        // Shift mount position by 10 pixels to the left and up to accommodate border
        int mountX = (int) Math.floor(centerX - sizeX / 2.0) - 10;
        int mountZ = (int) Math.floor(centerZ - sizeZ / 2.0) - 10;

        log.info("Calculated flat parameters: sizeX={}, sizeZ={}, mount=({},{}), hexCenter=({},{})",
                sizeX, sizeZ, mountX, mountZ, centerX, centerZ);

        // Check if flat already exists and delete it (in case of retry)
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before creation: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level and block from world
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(TypeUtil.hexVector2(hexQ, hexR))
                .build();

        // Initialize with size (sets all levels to 0 by default)
        flat.initWithSize(sizeX, sizeZ);

        // Calculate hex center coordinates
        double hexCenterX = centerX;
        double hexCenterZ = centerZ;

        log.debug("Hex center in cartesian: ({}, {})", hexCenterX, hexCenterZ);

        int hexCellsSet = 0;
        int outsideCellsSet = 0;

        // Process each cell in the flat
        // Set materials: 255 (NOT_SET_MUTABLE) inside hex, 0 (NOT_SET) outside
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Adjust coordinates for hex grid check: hex is positioned 10 pixels into the flat
                int hexCheckX = worldX + 10;
                int hexCheckZ = worldZ + 10;

                // Check if this position is inside the HexGrid
                boolean isInHex = HexMathUtil.isPointInHex(hexCheckX, hexCheckZ, hexCenterX, hexCenterZ, gridSize);

                if (isInHex) {
                    // Position is inside HexGrid: mark with NOT_SET_MUTABLE (255) at level 0
                    // Level is already 0 from initWithSize
                    flat.setColumn(localX, localZ, WFlat.MATERIAL_NOT_SET_MUTABLE);
                    hexCellsSet++;
                } else {
                    // Position is outside HexGrid (corner): mark with NOT_SET (0)
                    // Level is already 0 from initWithSize
                    flat.setColumn(localX, localZ, WFlat.MATERIAL_NOT_SET);
                    outsideCellsSet++;
                }
            }
        }

        flat.setUnknownProtected(true);

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("Empty HexGrid flat creation complete: flatId={}, size={}x{}, hexCells={} (material=NOT_SET_MUTABLE/255), outsideCorners={} (material=NOT_SET/0), all levels=0, unknownProtected=true",
                flatId, sizeX, sizeZ, hexCellsSet, outsideCellsSet);

        return saved;
    }

    /**
     * Create a WFlat for a border between two HexGrid fields.
     * Calculates the border strip location and size automatically.
     *
     * @param worldId World identifier
     * @param layerName Name of the layer to import from
     * @param flatId Flat identifier
     * @param hexQ HexGrid Q coordinate
     * @param hexR HexGrid R coordinate
     * @param border Neighbor direction for the border
     * @param borderSize Width of the border strip in blocks
     * @param title Optional title
     * @param description Optional description
     * @return Created WFlat with border
     */
    public WFlat createGridBorderFlat(String worldId, String layerName, String flatId,
                                      int hexQ, int hexR, WHexGrid.SIDE border, int borderSize,
                                      String title, String description) {
        log.info("Creating grid border flat: worldId={}, layerName={}, flatId={}, hex=({},{}), border={}, size={}",
                worldId, layerName, flatId, hexQ, hexR, border, borderSize);

        var worldIdObj = WorldId.of(worldId).orElseThrow();
        // Load world to get hexGridSize
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        // Calculate hex centers
        HexVector2 hex1 = HexVector2.builder().q(hexQ).r(hexR).build();
        HexVector2 hex2 = HexMathUtil.getNeighborPosition(hex1, border);

        double[] center1 = HexMathUtil.hexToCartesian(hex1, gridSize);
        double[] center2 = HexMathUtil.hexToCartesian(hex2, gridSize);

        // Calculate border line endpoints (midpoint between the two hex centers)
        double midX = (center1[0] + center2[0]) / 2.0;
        double midZ = (center1[1] + center2[1]) / 2.0;

        // Calculate direction perpendicular to the line connecting the centers
        double dirX = center2[0] - center1[0];
        double dirZ = center2[1] - center1[1];
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        dirX /= length;
        dirZ /= length;

        // Perpendicular direction
        double perpX = -dirZ;
        double perpZ = dirX;

        // Calculate border line endpoints (extended by borderSize/2 beyond the hex boundary)
        double radius = gridSize / 2.0;
        double extension = borderSize / 2.0;
        double lineLength = radius + extension;

        double line1X = midX - perpX * lineLength;
        double line1Z = midZ - perpZ * lineLength;
        double line2X = midX + perpX * lineLength;
        double line2Z = midZ + perpZ * lineLength;

        log.debug("Border line: ({},{}) to ({},{})", line1X, line1Z, line2X, line2Z);

        // Calculate bounding rectangle with margin
        double minX = Math.min(line1X, line2X) - borderSize;
        double maxX = Math.max(line1X, line2X) + borderSize;
        double minZ = Math.min(line1Z, line2Z) - borderSize;
        double maxZ = Math.max(line1Z, line2Z) + borderSize;

        int sizeX = (int) Math.ceil(maxX - minX);
        int sizeZ = (int) Math.ceil(maxZ - minZ);
        int mountX = (int) Math.floor(minX);
        int mountZ = (int) Math.floor(minZ);

        log.info("Calculated border rectangle: sizeX={}, sizeZ={}, mount=({},{})", sizeX, sizeZ, mountX, mountZ);

        // Check if flat already exists and delete it
        Optional<WFlat> existingFlat = flatService.findByWorldIdAndLayerDataIdAndFlatId(
                worldId, layer.getLayerDataId(), flatId);
        if (existingFlat.isPresent()) {
            log.info("Flat already exists, deleting before import: flatId={}", flatId);
            flatService.deleteById(existingFlat.get().getId());
        }

        // Get ocean level
        int oceanLevel = world.getOceanLevel() == null ? 60 : world.getOceanLevel();
        String oceanBlockId = world.getSeaBlockType() == null ? "n:o" : world.getSeaBlockType();

        // Build WFlat instance
        WFlat flat = WFlat.builder()
                .worldId(worldId)
                .layerDataId(layer.getLayerDataId())
                .flatId(flatId)
                .title(title)
                .description(description)
                .mountX(mountX)
                .mountZ(mountZ)
                .oceanLevel(oceanLevel)
                .oceanBlockId(oceanBlockId)
                .hexGrid(TypeUtil.hexVector2(hexQ, hexR))
                .build();

        // Initialize with size
        flat.initWithSize(sizeX, sizeZ);
        int chunkSize = world.getPublicData().getChunkSize();
        int defaultLevel = oceanLevel - 10;

        // Import from layer (all positions start with NOT_SET material 0)
        java.util.Set<String> requiredChunkKeys = new java.util.HashSet<>();
        int minChunkX = world.getChunkX(mountX);
        int minChunkZ = world.getChunkZ(mountZ);
        int maxChunkX = world.getChunkX(mountX + sizeX - 1);
        int maxChunkZ = world.getChunkZ(mountZ + sizeZ - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                requiredChunkKeys.add(TypeUtil.toStringChunkCoord(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for createGridBorderFlat", requiredChunkKeys.size());

        // Load all required chunks
        java.util.Map<String, ChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldIdObj, chunkKey, false);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        // Import levels from layer
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = mountX + x;
                int worldZ = mountZ + z;

                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = TypeUtil.toStringChunkCoord(chunkX, chunkZ);

                ChunkData chunkData = chunkCache.get(chunkKey);

                // Find ground level at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, chunkSize);

                if (groundLevel == -1) {
                    // No ground block found, use default level
                    flat.setLevel(x, z, defaultLevel);
                } else {
                    flat.setLevel(x, z, groundLevel);
                }

                // Set material to NOT_SET (0) initially
                flat.setColumn(x, z, WFlat.MATERIAL_NOT_SET);
            }
        }

        // Fill border strip with NOT_SET_MUTABLE (255)
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = mountX + x;
                int worldZ = mountZ + z;

                // Calculate distance from point to border line
                // Distance from point (worldX, worldZ) to line from (line1X, line1Z) to (line2X, line2Z)
                double dx = line2X - line1X;
                double dz = line2Z - line1Z;
                double lineLen = Math.sqrt(dx * dx + dz * dz);

                if (lineLen > 0) {
                    // Normalize direction vector
                    dx /= lineLen;
                    dz /= lineLen;

                    // Vector from line1 to point
                    double px = worldX - line1X;
                    double pz = worldZ - line1Z;

                    // Project point onto line
                    double t = px * dx + pz * dz;
                    t = Math.max(0, Math.min(lineLen, t)); // Clamp to line segment

                    // Closest point on line
                    double closestX = line1X + t * dx;
                    double closestZ = line1Z + t * dz;

                    // Distance to line
                    double dist = Math.sqrt(Math.pow(worldX - closestX, 2) + Math.pow(worldZ - closestZ, 2));

                    // If within border width, set to NOT_SET_MUTABLE
                    if (dist <= borderSize / 2.0) {
                        flat.setColumn(x, z, WFlat.MATERIAL_NOT_SET_MUTABLE);
                    }
                }
            }
        }

        flat.setUnknownProtected(true);
        // Persist to database
        WFlat created = flatService.create(flat);

        log.info("Grid border flat created: flatId={}, id={}, size={}x{}, mount=({},{})",
                flatId, created.getId(), sizeX, sizeZ, mountX, mountZ);

        return created;
    }
}
