package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.generated.types.BlockTypeType;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
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
        int oceanLevel = world.getWaterLevel() != null ? world.getWaterLevel() : 0;
        String oceanBlockId = world.getWaterBlockType();

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
        int oceanLevel = world.getWaterLevel() == null ? 60 : world.getWaterLevel();
        String oceanBlockId = world.getWaterBlockType() == null ? "n:o" : world.getWaterBlockType();

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
                requiredChunkKeys.add(BlockUtil.toChunkKey(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
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
                String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                // Get chunk from cache
                LayerChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, use default level
                    flat.setLevel(localX, localZ, defaultLevel);
                    emptyColumns++;
                    continue;
                }

                // Find highest ground block at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, WorldId.unchecked(worldId));

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
     * First checks for blocks with BlockType.type == GROUND.
     * If none found, falls back to legacy method (any solid block).
     *
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param chunkData Chunk data to search
     * @param worldId World identifier for block type lookup
     * @return Y coordinate of ground surface, or -1 if not found
     */
    private int findGroundLevel(int worldX, int worldZ, LayerChunkData chunkData, WorldId worldId) {
        int highestGroundBlock = -1;
        int highestSolidBlock = -1;

        // Iterate through all blocks in chunk
        for (LayerBlock layerBlock : chunkData.getBlocks()) {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                continue;
            }

            var pos = block.getPosition();

            // Check if block is at our target position
            if (pos.getX() == worldX && pos.getZ() == worldZ) {
                int blockY = pos.getY();
                String blockTypeId = block.getBlockTypeId();

                if (blockTypeId == null || blockTypeId.equals("0") || blockTypeId.isBlank()) {
                    // Air block, skip
                    continue;
                }

                // Track highest solid block (legacy fallback)
                if (blockY > highestSolidBlock) {
                    highestSolidBlock = blockY;
                }

                // Check if this is a ground block
                Optional<WBlockType> blockTypeOpt = blockTypeService.findByBlockId(worldId, blockTypeId);
                if (blockTypeOpt.isPresent()) {
                    WBlockType wBlockType = blockTypeOpt.get();
                    BlockType blockType = wBlockType.getPublicData();

                    if (blockType != null && blockType.getType() == BlockTypeType.GROUND) {
                        // This is a ground block
                        if (blockY > highestGroundBlock) {
                            highestGroundBlock = blockY;
                        }
                    }
                }
            }
        }

        // Return highest ground block if found, otherwise highest solid block
        if (highestGroundBlock != -1) {
            return highestGroundBlock;
        }

        return highestSolidBlock;
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
        int oceanLevel = world.getWaterLevel() == null ? 60 : world.getWaterLevel();
        String oceanBlockId = world.getWaterBlockType() == null ? "n:o" : world.getWaterBlockType();

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
                requiredChunkKeys.add(BlockUtil.toChunkKey(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for border import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
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
                String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                // Get chunk from cache
                LayerChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, keep default level 0
                    continue;
                }

                // Find ground level at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, WorldId.unchecked(worldId));

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
     * Create a WFlat for a HexGrid area with BEDROCK material inside the hex and layer import outside.
     * Creates a flat terrain where:
     * - Positions inside the HexGrid are filled with BEDROCK material at level 0
     * - Positions outside the HexGrid are imported from the layer
     * - unknownProtected is set to true (only HexGrid positions can be modified)
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
        int oceanLevel = world.getWaterLevel() == null ? 60 : world.getWaterLevel();
        String oceanBlockId = world.getWaterBlockType() == null ? "n:o" : world.getWaterBlockType();

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
                .unknownProtected(true)  // Set unknownProtected to true for HexGrid flats
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
                requiredChunkKeys.add(BlockUtil.toChunkKey(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for layer import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int hexCellsSet = 0;
        int outsideCellsImported = 0;

        // Process each cell in the flat
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Check if this position is inside the HexGrid
                boolean isInHex = HexMathUtil.isPointInHex(worldX, worldZ, hexCenterX, hexCenterZ, gridSize);

                if (isInHex) {
                    // Position is inside HexGrid: set BEDROCK material at level 0
                    flat.setLevel(localX, localZ, 0);
                    flat.setColumn(localX, localZ, FlatMaterialService.BEDROCK);
                    hexCellsSet++;
                } else {
                    // Position is outside HexGrid: import from layer
                    // Calculate chunk coordinates
                    int chunkX = world.getChunkX(worldX);
                    int chunkZ = world.getChunkZ(worldZ);
                    String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                    // Get chunk from cache
                    LayerChunkData chunkData = chunkCache.get(chunkKey);

                    if (chunkData != null) {
                        // Find ground level at this position
                        int groundLevel = findGroundLevel(worldX, worldZ, chunkData, WorldId.unchecked(worldId));

                        if (groundLevel != -1) {
                            flat.setLevel(localX, localZ, groundLevel);
                            outsideCellsImported++;
                        }
                        // If groundLevel == -1, keep default level 0
                    }
                    // If no chunk data, keep default level 0
                }
            }
        }

        // Persist to database
        WFlat saved = flatService.create(flat);

        log.info("HexGrid flat creation complete: flatId={}, size={}x{}, hexCells={}, outsideCells={}, unknownProtected=true",
                flatId, sizeX, sizeZ, hexCellsSet, outsideCellsImported);

        return saved;
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
        int oceanLevel = world.getWaterLevel() == null ? 60 : world.getWaterLevel();
        String oceanBlockId = world.getWaterBlockType() == null ? "n:o" : world.getWaterBlockType();

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
                .unknownProtected(true)  // Set unknownProtected to true for HexGrid flats
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
                requiredChunkKeys.add(BlockUtil.toChunkKey(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for import", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
            chunkDataOpt.ifPresent(data -> chunkCache.put(chunkKey, data));
        }

        int importedColumns = 0;
        int outsideColumns = 0;
        int emptyColumns = 0;

        // Step 1: Import ALL columns from layer and set material to 255
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                // Get chunk from cache
                LayerChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, use default level and material 255
                    flat.setLevel(localX, localZ, defaultLevel);
                    flat.setColumn(localX, localZ, 255); // Material 255 (UNKNOWN_NOT_PROTECTED)
                    emptyColumns++;
                    continue;
                }

                // Find highest ground block at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, WorldId.unchecked(worldId));

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
        for (int localX = 0; localX < sizeX; localX++) {
            for (int localZ = 0; localZ < sizeZ; localZ++) {
                // Calculate world coordinates
                int worldX = mountX + localX;
                int worldZ = mountZ + localZ;

                // Check if this position is inside the HexGrid
                boolean isInHex = HexMathUtil.isPointInHex(worldX, worldZ, hexCenterX, hexCenterZ, gridSize);

                if (!isInHex) {
                    // Position is outside HexGrid: Set material to 0 (UNKNOWN_PROTECTED)
                    flat.setColumn(localX, localZ, 0); // Material 0 = NOT_SET
                    outsideColumns++;
                }
            }
        }

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

        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

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
                requiredChunkKeys.add(BlockUtil.toChunkKey(chunkX, chunkZ));
            }
        }

        log.debug("Loading {} chunks for border update", requiredChunkKeys.size());

        // Load all required chunks at once
        java.util.Map<String, LayerChunkData> chunkCache = new java.util.HashMap<>();
        for (String chunkKey : requiredChunkKeys) {
            Optional<LayerChunkData> chunkDataOpt = layerService.loadTerrainChunk(layer.getLayerDataId(), chunkKey);
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
                String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                // Get chunk from cache
                LayerChunkData chunkData = chunkCache.get(chunkKey);

                if (chunkData == null) {
                    // No chunk data, skip this cell
                    continue;
                }

                // Find ground level at this position
                int groundLevel = findGroundLevel(worldX, worldZ, chunkData, WorldId.unchecked(worldId));

                if (groundLevel != -1) {
                    // Update level from layer for border cell
                    flat.setLevel(localX, localZ, groundLevel);
                    borderCellsUpdated++;
                }
            }
        }

        // Persist changes to database
        WFlat updated = flatService.update(flat);

        log.info("Border update complete: flatId={}, borderCellsUpdated={}", flatId, borderCellsUpdated);

        return updated;
    }
}
