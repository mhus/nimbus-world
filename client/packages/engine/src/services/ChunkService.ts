/**
 * ChunkService - Chunk management and loading
 *
 * Manages chunk registration, loading, unloading, and provides
 * access to chunk data for rendering and gameplay.
 */

import {
  BaseMessage,
  MessageType,
  ChunkCoordinate,
  ChunkRegisterData,
  ChunkDataTransferObject,
  Block,
  Item,
  Shape,
  getLogger,
  ExceptionHandler,
  Backdrop,
  Vector3,
  AudioType,
  itemToBlock,
  type AudioDefinition, ItemBlockRef,
  normalizeBlockTypeId,
  getBlockTypeGroup, BlockType,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { NetworkService } from './NetworkService';
import type { ClientChunkData, ClientHeightData } from '../types/ClientChunk';
import { ClientChunk } from '../types/ClientChunk';
import type { ClientBlock } from '../types/ClientBlock';
import { DisposableResources } from '../rendering/DisposableResources';
import {
  worldToChunk,
  getChunkKey,
} from '../utils/ChunkUtils';
import { mergeBlockModifier, getBlockPositionKey } from '../utils/BlockModifierMerge';

const logger = getLogger('ChunkService');

/**
 * Default backdrop configuration used when chunk data doesn't provide backdrop
 */
// const DEFAULT_BACKDROP: Backdrop = {
//     id: 'fadeout'
// };

const DEFAULT_BACKDROP: Backdrop = {
  id: 'none'
};

const ERROR_BLOCK_TYPE: BlockType = {
    id: 'error:block',
    description: 'This block type is used when the specified block type cannot be found.',
    modifiers: {
      0: {
        visibility: {
            shape: Shape.CUBE,
            textures: {
              0: {
                path: '',
                color: '#ff0000'
              }
            }
        }
      }
    }
}
/**
 * Event listener
 */
type EventListener = (...args: any[]) => void;

/**
 * ChunkService - Manages chunks on the client
 *
 * Features:
 * - Chunk registration with server
 * - Dynamic chunk loading based on player position
 * - Chunk unloading for distant chunks
 * - Block access API
 * - Browser-specific render distances
 * - Event emission for rendering
 */
export class ChunkService {
  private chunks = new Map<string, ClientChunk>();
  private eventListeners: Map<string, EventListener[]> = new Map();

  private renderDistance: number;
  private unloadDistance: number;

  // Track if initial chunks are loaded (to enable physics)
  private initialChunksLoaded: boolean = false;

  constructor(
    private networkService: NetworkService,
    private appContext: AppContext
  ) {
    // Load render and unload distance from config (from URL query parameters)
    this.renderDistance = appContext.config?.renderDistance ?? 1;
    this.unloadDistance = appContext.config?.unloadDistance ?? 2;

    logger.info('ChunkService initialized', {
      renderDistance: this.renderDistance,
      unloadDistance: this.unloadDistance,
    });

  }

  /**
   * Register chunks with server for updates
   *
   * Server will automatically send chunk data for registered chunks.
   * Filters out already-registered chunks to avoid duplicate requests.
   *
   * @param cx
   */
  async registerChunks(cx:number, cz: number): Promise<void> {
      const d = {
          cx: cx,
          cz: cz,
          hr: this.renderDistance,
          lr: this.unloadDistance
      }
    try {
      // Send registration message (send all coords, not just new ones)
      const message: BaseMessage<ChunkRegisterData> = {
        t: MessageType.CHUNK_REGISTER,
        d: d
      };

      this.networkService.send(message);

      logger.debug('Registered chunks', d);
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'ChunkService.registerChunks', d);
    }
  }

  /**
   * Update chunks around a world position
   *
   * Calculates which chunks should be loaded based on render distance
   * and registers them with the server.
   *
   * @param worldX - World X coordinate
   * @param worldZ - World Z coordinate
   */
  updateChunksAroundPosition(worldX: number, worldZ: number): void {
    try {
      const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
      const playerChunk = worldToChunk(worldX, worldZ, chunkSize);
      const chunkX = playerChunk.cx;
      const chunkZ = playerChunk.cz;

      this.registerChunks(chunkX, chunkZ);
      this.unloadDistantChunks(playerChunk.cx, playerChunk.cz);
    } catch (error) {
      ExceptionHandler.handle(error, 'ChunkService.updateChunksAroundPosition', {
        worldX,
        worldZ,
      });
    }
  }

  /**
   * Unload chunks that are too far from player
   *
   * @param playerCx - Player chunk X coordinate
   * @param playerCz - Player chunk Z coordinate
   */
  unloadDistantChunks(playerCx: number, playerCz: number): void {
    try {
      // First, collect all chunks to unload (don't delete during iteration)
      const chunksToUnload: Array<{ key: string; chunk: ClientChunk; distance: number }> = [];

      for (const [key, chunk] of this.chunks) {
        const distance = Math.max(
          Math.abs(chunk.data.transfer.cx - playerCx),
          Math.abs(chunk.data.transfer.cz - playerCz)
        );

        if (distance > this.unloadDistance) {
          chunksToUnload.push({ key, chunk, distance });
        }
      }

      // Now unload all collected chunks
      for (const { key, chunk, distance } of chunksToUnload) {
        const cx = chunk.data.transfer.cx;
        const cz = chunk.data.transfer.cz;

        // Emit event for rendering cleanup BEFORE deleting chunk
        // so RenderService can still access DisposableResources
        this.emit('chunk:unloaded', { cx, cz });

        // Now delete the chunk
        this.chunks.delete(key);

        logger.debug('Unloaded chunk', {
          cx,
          cz,
          distance,
        });
      }

      if (chunksToUnload.length > 0) {
        logger.debug('Unloaded distant chunks', { count: chunksToUnload.length });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ChunkService.unloadDistantChunks', {
        playerCx,
        playerCz,
      });
    }
  }

  /**
   * Handle chunk update from server (called by ChunkMessageHandler)
   *
   * @param chunks - Array of chunk data from server
   */
  async onChunkUpdate(chunks: ChunkDataTransferObject[]): Promise<void> {
    try {
      // Process chunks sequentially to maintain order
      for (const chunkData of chunks) {
        const key = getChunkKey(chunkData.cx, chunkData.cz);

        // Check if chunk already exists
        const existingChunk = this.chunks.get(key);

        // Decompress chunk data if compressed
        if (chunkData.c) {
          try {
            logger.debug('Decompressing chunk data in ChunkService', {
              cx: chunkData.cx,
              cz: chunkData.cz,
              compressedSize: chunkData.c.length,
            });

            // Decompress the complete ChunkData object
            const decompressedStream = new Response(
              new Blob([chunkData.c as BlobPart]).stream().pipeThrough(new DecompressionStream('gzip'))
            );
            const decompressedText = await decompressedStream.text();
            const cChunk = JSON.parse(decompressedText);

            // Extract fields from decompressed ChunkData and map to ChunkDataTransferObject
            chunkData.b = cChunk.blocks || [];
            chunkData.h = cChunk.heightData || [];
            chunkData.backdrop = cChunk.backdrop;

            // Important: free memory
            chunkData.c = undefined as any;

            logger.debug('Chunk decompressed in ChunkService', {
              cx: chunkData.cx,
              cz: chunkData.cz,
              blocks: chunkData.b.length,
              heightData: chunkData.h?.length || 0,
            });
          } catch (error) {
            logger.error('Failed to decompress chunk data in ChunkService', {
              cx: chunkData.cx,
              cz: chunkData.cz,
              error: (error as Error).message,
            });
            console.error(error);
            continue;
          }
        }

        // Process blocks into ClientBlocks with merged modifiers
        const clientChunkData = await this.processChunkData(chunkData);

        if (existingChunk) {
          // Update existing chunk
          logger.debug('Updating existing chunk with new data', {
            cx: chunkData.cx,
            cz: chunkData.cz,
            oldBlocks: existingChunk.data.data.size,
            newBlocks: clientChunkData.data.size,
          });

          // Replace chunk data
          existingChunk.data = clientChunkData;
          existingChunk.isRendered = false;
          existingChunk.isLoaded = true; // All data (blocks + heightData) fully loaded

          // Emit event for re-rendering
          this.emit('chunk:updated', existingChunk);

          // Reload permanent audio for updated chunk (non-blocking)
          this.loadPermanentAudioForChunk(existingChunk).catch(error => {
            logger.warn('Failed to reload permanent audio for updated chunk (non-blocking)', {
              cx: chunkData.cx,
              cz: chunkData.cz,
              error: (error as Error).message,
            });
          });

          logger.info('Chunk updated and marked for re-rendering', {
            cx: chunkData.cx,
            cz: chunkData.cz,
          });
        } else {
          // Create new chunk
          const clientChunk: ClientChunk = new ClientChunk(clientChunkData, this.appContext.worldInfo?.chunkSize || 16);

          this.chunks.set(key, clientChunk);

          // Mark as loaded AFTER adding to map to ensure atomicity
          clientChunk.isLoaded = true; // All data (blocks + heightData) fully loaded

          // Emit event for rendering
          this.emit('chunk:loaded', clientChunk);

          // Load permanent audio for this chunk (non-blocking)
          this.loadPermanentAudioForChunk(clientChunk).catch(error => {
            logger.warn('Failed to load permanent audio for chunk (non-blocking)', {
              cx: chunkData.cx,
              cz: chunkData.cz,
              error: (error as Error).message,
            });
          });

          logger.debug('Chunk loaded', {
            cx: chunkData.cx,
            cz: chunkData.cz,
            blocks: chunkData.b.length,
            clientBlocks: clientChunkData.data.size,
          });
        }
      }

      // Check if initial player spawn is ready
      this.checkInitialSpawnReady();
    } catch (error) {
      ExceptionHandler.handle(error, 'ChunkService.onChunkUpdate', {
        count: chunks.length,
      });
    }
  }

  /**
   * Check if initial player spawn is ready
   *
   * Starts teleportation mode for initial spawn on first call
   */
  private checkInitialSpawnReady(): void {
    // Only trigger once
    if (this.initialChunksLoaded) {
      return;
    }

    // Need player service
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return;
    }

    // Get physics service
    const physicsService = this.appContext.services.physics;
    if (!physicsService) {
      return;
    }

    // Mark as handled
    this.initialChunksLoaded = true;

    // Start teleportation mode - PhysicsService will handle the rest
    // It will check for chunk, heightData, blocks and position player automatically
    const playerEntity = physicsService.getEntity('player');
    if (playerEntity) {
      physicsService.teleport(playerEntity, playerEntity.position);
      logger.debug('Initial spawn - teleportation mode started');
    } else {
      logger.warn('Player entity not found for initial spawn');
    }
  }

  private processStatusData(chunkData: ChunkDataTransferObject): Map<string, number> {
      const statusData = new Map<string, number>();
      // Extract status from blocks instead of non-existent 's' property
      for (const block of chunkData.b || []) {
        if (block.status !== undefined) {
          const posKey = getBlockPositionKey(block.position.x, block.position.y, block.position.z);
          statusData.set(posKey, block.status);
        }
      }
      return statusData;
  }

  /**
   * Process chunk data from server into ClientChunkData with ClientBlocks
   *
   * Performance-optimized: Processes blocks and height data in a single pass
   *
   * @param chunkData Raw chunk data from server
   * @returns Processed ClientChunkData with merged modifiers and height data
   */
  private async processChunkData(chunkData: ChunkDataTransferObject): Promise<ClientChunkData> {

    // Normalize blockTypeId for all blocks (convert legacy numbers to strings)
    if (chunkData.b) {
      for (const block of chunkData.b) {
          block.blockTypeId = normalizeBlockTypeId(block.blockTypeId);
      }
    }

    const clientBlocksMap = new Map<string, ClientBlock>();
    const blockTypeService = this.appContext.services.blockType;
    const statusData = this.processStatusData(chunkData);
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;

    // Pre-load all BlockType groups needed for this chunk
    if (blockTypeService && chunkData.b) {
      const groupsToLoad = new Set<string>();
      for (const block of chunkData.b) {
        const group = getBlockTypeGroup(block.blockTypeId);
        groupsToLoad.add(group);
      }

      // Load all groups in parallel
      await Promise.all(
        Array.from(groupsToLoad).map(group => blockTypeService.ensureGroupLoaded(group))
      );

      logger.debug('Pre-loaded BlockType groups for chunk', {
        cx: chunkData.cx,
        cz: chunkData.cz,
        groups: Array.from(groupsToLoad),
      });
    }

    // World bounds
    const worldMaxY = this.appContext.worldInfo?.stop?.y ?? 1000;
    const worldMinY = this.appContext.worldInfo?.start?.y ?? -100;

    // Height data structures for single-pass calculation
    const heightData = new Map<string, ClientHeightData>();

    // Track blocks per column for height calculation (localX, localZ -> blocks)
    // Note: maxY is usually worldMaxY, but if blocks exceed it, we add 10 blocks headroom
    const columnBlocks = new Map<string, {
      blocks: Block[],
      minY: number,
      highestBlockY: number,  // Track highest block for worldMaxY override check
      groundLevel: number | null,
      waterLevel: number | null
    }>();

    // STEP 1: Use server-provided height data if available
    if (chunkData.h) {
      for (const entry of chunkData.h) {
        const [x, z, maxHeight, groundLevel, waterLevel] = entry;
        const heightKey = `${x},${z}`;
        // Server provides: [x, z, maxHeight, groundLevel, waterLevel]
        // ClientHeightData format: [x, z, maxHeight, minHeight, groundLevel, waterHeight]
        // Use worldMinY as minHeight since server doesn't provide it
        heightData.set(heightKey, [x, z, maxHeight, worldMinY, groundLevel, waterLevel]);
      }
    }

    if (!blockTypeService) {
      logger.warn('BlockTypeService not available - cannot process blocks');
      return {
        transfer: chunkData,
        data: clientBlocksMap,
        hightData: heightData,
        statusData: statusData
      };
    }

    // STEP 1.5: Preload all BlockTypes needed for this chunk
    // Collect unique BlockType IDs from blocks AND items
    const blockTypeIds = new Set(
      (chunkData.b || []).map(block => normalizeBlockTypeId(block.blockTypeId))
    );
    if (chunkData.i && chunkData.i.length > 0) {
      // All items use BlockType 1 (ITEM type)
      blockTypeIds.add('1');
    }
    logger.debug('Preloading BlockTypes for chunk', {
      cx: chunkData.cx,
      cz: chunkData.cz,
      uniqueBlockTypes: blockTypeIds.size,
    });

    // Preload all required chunks in parallel
    await blockTypeService.preloadBlockTypes(Array.from(blockTypeIds));

    // STEP 1.6: Preload unique audio files for all blocks in this chunk
    // This prevents race conditions and reduces redundant function calls
    const audioService = this.appContext.services.audio;
    if (audioService) {
      const uniqueAudioPaths = new Map<string, AudioType | string>();

      // Collect all unique audio paths from blocks (with merged modifiers)
      for (const block of chunkData.b) {
        const blockType = await blockTypeService.getBlockType(block.blockTypeId);
        if (!blockType) {
          continue;
        }

        // Get position key for this block
        const posKey = getBlockPositionKey(block.position.x, block.position.y, block.position.z);

        // Merge block modifiers to get final audio definitions (same as in block processing)
        const currentModifier = mergeBlockModifier(this.appContext, block, blockType, statusData.get(posKey));
        const audioModifier = currentModifier.audio;

        if (audioModifier && audioModifier.length > 0) {
          // Filter for STEPS and COLLISION audio (same as loadBlockAudio logic)
          const audioToPreload = audioModifier.filter(
            (def: AudioDefinition) => (def.type === AudioType.STEPS || def.type === AudioType.COLLISION) && def.enabled
          );

          audioToPreload.forEach((audioDef: AudioDefinition) => {
            // Store path with type for proper pool sizing
            uniqueAudioPaths.set(audioDef.path, audioDef.type);
          });
        }
      }

      // Preload all unique audio files in parallel
      if (uniqueAudioPaths.size > 0) {
        logger.debug('Preloading audio files for chunk', {
          cx: chunkData.cx,
          cz: chunkData.cz,
          uniqueAudioFiles: uniqueAudioPaths.size,
        });

        const audioLoadPromises = Array.from(uniqueAudioPaths.entries()).map(([path, type]) => {
          // Use initial pool size based on audio type (matches loadBlockAudio logic)
          const initialPoolSize = type === AudioType.STEPS ? 3 : 1;
          return audioService.loadSoundIntoPool(path, initialPoolSize).catch((error: Error) => {
            logger.warn('Failed to preload audio file (non-blocking)', {
              path,
              type,
              error: error.message,
            });
          });
        });

        await Promise.all(audioLoadPromises);
      }
    }

    // STEP 2: Process each block - creating ClientBlocks AND collecting height data in ONE pass
    // Process in batches to avoid blocking main thread
    const BATCH_SIZE = 50;
    const blocks = chunkData.b;

    for (let batchStart = 0; batchStart < blocks.length; batchStart += BATCH_SIZE) {
      const batchEnd = Math.min(batchStart + BATCH_SIZE, blocks.length);

      // Process batch
      for (let i = batchStart; i < batchEnd; i++) {
        const block = blocks[i];

        // Get position key for this block
        const posKey = getBlockPositionKey(block.position.x, block.position.y, block.position.z);

        const blockType = await blockTypeService.getBlockType(block.blockTypeId);
        if (!blockType) {
          logger.warn('BlockType not found for block', {
            blockTypeId: block.blockTypeId,
            position: block.position,
            // loadedGroups: blockTypeService.getLoadedGroups(),
            // knownBlockTypeIds: blockTypeService.getAllBlockTypeIds(),
            // knownBlockTypeCount: blockTypeService.getBlockTypeCount(),
          });

          const clientBlock: ClientBlock = {
            block,
            chunk: { cx: chunkData.cx, cz: chunkData.cz },
            blockType: ERROR_BLOCK_TYPE,
            currentModifier: ERROR_BLOCK_TYPE.modifiers[0],
            isVisible: true,
            isDirty: false,
            lastUpdate: Date.now(),
          };
          clientBlocksMap.set(posKey, clientBlock);
          continue;
        }

      // Merge block modifiers according to priority rules
      const currentModifier = mergeBlockModifier(this.appContext, block, blockType, statusData.get(posKey));

      // Create ClientBlock
      const clientBlock: ClientBlock = {
        block,
        chunk: { cx: chunkData.cx, cz: chunkData.cz },
        blockType,
        currentModifier,
        isVisible: true,
        isDirty: false,
        lastUpdate: Date.now(),
      };

      // Add to map with position key
      clientBlocksMap.set(posKey, clientBlock);

      // Note: Audio files are now preloaded in batch before this loop (STEP 1.6)
      // This prevents race conditions and reduces redundant function calls

      // PERFORMANCE: Calculate height data in same pass
      // Calculate local x, z coordinates within chunk
      const localX = ((block.position.x % chunkSize) + chunkSize) % chunkSize;
      const localZ = ((block.position.z % chunkSize) + chunkSize) % chunkSize;
      const columnKey = `${Math.floor(localX)},${Math.floor(localZ)}`;

      // Skip if server already provided height data for this column
      if (heightData.has(columnKey)) {
        continue;
      }

      // Track blocks in this column for height calculation
      if (!columnBlocks.has(columnKey)) {
        columnBlocks.set(columnKey, {
          blocks: [],
          minY: Infinity,
          highestBlockY: -Infinity,
          groundLevel: null,
          waterLevel: null
        });
      }

      const columnData = columnBlocks.get(columnKey)!;
      columnData.blocks.push(block);

      // Update minY and highestBlockY (more efficient than separate pass later)
      if (block.position.y < columnData.minY) {
        columnData.minY = block.position.y;
        // Ground level is the lowest block (first from bottom)
        columnData.groundLevel = block.position.y;
      }
      if (block.position.y > columnData.highestBlockY) {
        columnData.highestBlockY = block.position.y;
      }

      // Check for water blocks (check shape first, then description fallback)
      const modifier = clientBlock.currentModifier;
      const shape = modifier?.visibility?.shape;
      const isWater = shape === Shape.OCEAN ||
                        shape === Shape.WATER ||
                        shape === Shape.RIVER ||
                        shape === Shape.OCEAN_MAELSTROM ||
                        shape === Shape.OCEAN_COAST;

      if (isWater) {
        if (columnData.waterLevel === null || block.position.y > columnData.waterLevel) {
          columnData.waterLevel = block.position.y;
        }
      }
      }

      // Yield to main thread after each batch (except last batch)
      if (batchEnd < blocks.length) {
        await new Promise(resolve => setTimeout(resolve, 0));
      }
    }

    // STEP 3: Finalize height data for columns with blocks (already calculated during block processing)
    for (const [columnKey, columnData] of columnBlocks.entries()) {
      const [xStr, zStr] = columnKey.split(',');
      const x = parseInt(xStr, 10);
      const z = parseInt(zStr, 10);

      // Calculate maxHeight:
      // - Usually worldMaxY
      // - Exception: If highest block exceeds worldMaxY, use highestBlock + 10 for headroom
      let maxHeight = worldMaxY;
      if (columnData.highestBlockY !== -Infinity && columnData.highestBlockY > worldMaxY) {
        maxHeight = columnData.highestBlockY + 10;
        logger.warn('Block exceeds worldMaxY, adding headroom', {
          cx: chunkData.cx,
          cz: chunkData.cz,
          columnKey,
          highestBlock: columnData.highestBlockY,
          worldMaxY,
          newMaxHeight: maxHeight
        });
      }

      heightData.set(columnKey, [
        x,
        z,
        maxHeight,
        columnData.minY !== Infinity ? columnData.minY : worldMinY,
        columnData.groundLevel !== null ? columnData.groundLevel : worldMinY,
        columnData.waterLevel !== null ? columnData.waterLevel : undefined
      ]);
    }

    // STEP 3.5: Process items list - add items only at AIR positions
    if (chunkData.i && chunkData.i.length > 0) {
      logger.debug('Processing items for chunk', {
        cx: chunkData.cx,
        cz: chunkData.cz,
        itemCount: chunkData.i.length,
        firstItem: chunkData.i[0],
        firstItemKeys: chunkData.i[0] ? Object.keys(chunkData.i[0]) : [],
      });

      // Process items (now Item[], no Block wrapper)
      const itemList = chunkData.i;
      for (let batchStart = 0; batchStart < itemList.length; batchStart += BATCH_SIZE) {
        const batchEnd = Math.min(batchStart + BATCH_SIZE, itemList.length);

        for (let i = batchStart; i < batchEnd; i++) {
          const item = itemList[i];

          logger.debug('Processing item', {
            index: i,
            itemId: item.id,
            position: item.position,
            keys: item ? Object.keys(item) : [],
          });

          // Validate item
          if (!item || !item.position) {
            logger.warn('Invalid item in chunk', { index: i, item });
            continue;
          }

        // Calculate correct chunk for this item based on its position
        // Don't rely on server's chunk assignment (may be from old buggy data)
        const itemChunkCoord = worldToChunk(item.position.x, item.position.z, chunkSize);

        // Skip if item belongs to different chunk than current one
        if (itemChunkCoord.cx !== chunkData.cx || itemChunkCoord.cz !== chunkData.cz) {
          logger.debug('Item belongs to different chunk, skipping', {
            position: item.position,
            itemId: item.id,
            itemChunk: itemChunkCoord,
            currentChunk: { cx: chunkData.cx, cz: chunkData.cz },
          });
          continue;
        }

        // STEP 1: Check if position is AIR
        const posKey = getBlockPositionKey(
          item.position.x,
          item.position.y,
          item.position.z
        );

        if (clientBlocksMap.has(posKey)) {
          logger.debug('Item skipped - position occupied by block', {
            position: item.position,
            itemId: item.id,
          });
          continue;
        }

        // STEP 2: Fill Item via ItemService (merges ItemType)
        const itemService = this.appContext.services.item;
        if (!itemService) {
          logger.warn('ItemService not available for item processing', {
            position: item.position,
            itemId: item.id,
          });
          continue;
        }

        // const filledItem = await itemService.fillItem(item);
        // if (!filledItem) {
        //   logger.warn('Failed to fill Item', {
        //     position: item.position,
        //     itemId: item.id,
        //   });
        //   continue;
        // }

        // STEP 3: Get BlockType 1 (ITEM type)
        const blockType = await blockTypeService.getBlockType('1');
        if (!blockType) {
          logger.warn('BlockType 1 (ITEM) not found');
          continue;
        }

        // STEP 4: Convert Item to Block for rendering
        // Items are converted to blocks only in ChunkService for rendering
        const block = itemToBlock(item);

        // STEP 5: Create ClientBlock with Item reference
        // Use correct chunk coords calculated from item position (not server's chunk)
        const clientBlock: ClientBlock = {
          block,
          chunk: itemChunkCoord, // Use calculated chunk coords, not chunkData coords
          blockType,
          currentModifier: block.modifiers?.[0] || blockType.modifiers[0],
          isVisible: true,
          isDirty: false,
          lastUpdate: Date.now(),
          itemBlockRef: item, // Store Item Block Ref
        };

        // Add to map with position key
        clientBlocksMap.set(posKey, clientBlock);

        // Load audio files for this block (non-blocking - loads in background)
        this.loadBlockAudio(clientBlock).catch(error => {
          logger.warn('Failed to load item block audio (non-blocking)', {
            position: clientBlock.block.position,
            error: (error as Error).message,
          });
        });

        logger.debug('Item added to chunk', {
          position: item.position,
          itemId: item.id,
        });
        }

        // Yield to main thread after each batch (except last batch)
        if (batchEnd < itemList.length) {
          await new Promise(resolve => setTimeout(resolve, 0));
        }
      }
    }

    // STEP 4: Fill in empty columns with default values (no blocks in column)
    for (let x = 0; x < chunkSize; x++) {
      for (let z = 0; z < chunkSize; z++) {
        const heightKey = `${x},${z}`;

        // Skip if already have data (from server or calculated)
        if (heightData.has(heightKey)) {
          continue;
        }

        // Empty column - use world bounds as defaults
        heightData.set(heightKey, [
          x,
          z,
          worldMaxY,  // No blocks, so max is world top
          worldMinY,  // No blocks, so min is world bottom
          worldMinY,  // No ground level (no blocks)
          undefined   // No water
        ]);
      }
    }

    // Summary log (only if water found)
    const waterColumns = Array.from(heightData.values()).filter(h => h[5] !== undefined);
    if (waterColumns.length > 0) {
      logger.debug('💧 Water found in chunk', {
        chunk: { cx: chunkData.cx, cz: chunkData.cz },
        waterColumns: waterColumns.length,
      });
    }

    // STEP 5: Process backdrop data - add default backdrop if not provided
    const backdrop = this.processBackdropData(chunkData);

    return {
      transfer: chunkData,
      data: clientBlocksMap,
      hightData: heightData,
      statusData: statusData,
      backdrop,
    };
  }

  /**
   * Process backdrop data - set default backdrop for sides that are not provided
   */
  private processBackdropData(chunkData: ChunkDataTransferObject): {
    n?: Array<Backdrop>;
    e?: Array<Backdrop>;
    s?: Array<Backdrop>;
    w?: Array<Backdrop>;
  } {
    const backdrop = chunkData.backdrop || {};

    // Set default backdrop for each side if not provided
    return {
      n: backdrop.n && backdrop.n.length > 0 ? backdrop.n : [DEFAULT_BACKDROP],
      e: backdrop.e && backdrop.e.length > 0 ? backdrop.e : [DEFAULT_BACKDROP],
      s: backdrop.s && backdrop.s.length > 0 ? backdrop.s : [DEFAULT_BACKDROP],
      w: backdrop.w && backdrop.w.length > 0 ? backdrop.w : [DEFAULT_BACKDROP],
    };
  }

  /**
   * Get chunk at world block coordinates
   *
   * @param x - Block X coordinate
   * @param z - Block Z coordinate
   * @returns Chunk or undefined if not loaded
   */
  getChunkForBlockPosition(pos : Vector3): ClientChunk | undefined {
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    const { cx, cz } = worldToChunk(pos.x, pos.z, chunkSize);
    return this.chunks.get(getChunkKey(cx, cz));
  }

  /**
   * Get chunk at world block coordinates
   *
   * @param x - Block X coordinate
   * @param z - Block Z coordinate
   * @returns Chunk or undefined if not loaded
   */
  getChunkForBlock(x: number, z: number): ClientChunk | undefined {
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    const { cx, cz } = worldToChunk(x, z, chunkSize);
    return this.chunks.get(getChunkKey(cx, cz));
  }

  /**
   * Get chunk at chunk coordinates
   *
   * @param cx - Chunk X coordinate
   * @param cz - Chunk Z coordinate
   * @returns Chunk or undefined if not loaded
   */
  getChunk(cx: number, cz: number): ClientChunk | undefined {
    return this.chunks.get(getChunkKey(cx, cz));
  }

  /**
   * Get block at world coordinates
   *
   * @param x - World X coordinate
   * @param y - World Y coordinate
   * @param z - World Z coordinate
   * @returns ClientBlock or undefined if not found
   */
  getBlockAt(x: number, y: number, z: number): ClientBlock | undefined {
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    const chunkCoord = worldToChunk(x, z, chunkSize);
    const chunk = this.getChunk(chunkCoord.cx, chunkCoord.cz);

    if (!chunk) {
      return undefined;
    }

    // Look up block in processed data map
    const posKey = getBlockPositionKey(x, y, z);
    return chunk.data.data.get(posKey);
  }

  /**
   * Handle block updates from server (called by BlockUpdateHandler)
   *
   * Updates individual blocks in loaded chunks. Blocks with blockTypeId: 0 are deleted.
   *
   * @param blocks - Array of block updates from server
   */
  async onBlockUpdate(blocks: Block[]): Promise<void> {
    try {
      logger.debug('ChunkService.onBlockUpdate called', {
        blockCount: blocks.length,
      });

      // Normalize blockTypeId for all blocks (convert legacy numbers to strings)
      for (const block of blocks) {
        block.blockTypeId = normalizeBlockTypeId(block.blockTypeId);
      }

      const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
      const blockTypeService = this.appContext.services.blockType;

      if (!blockTypeService) {
        logger.warn('BlockTypeService not available - cannot process block updates');
        return;
      }

      // Preload all BlockTypes needed for these updates
      const blockTypeIds = new Set(
        blocks
          .filter(block => block.blockTypeId !== '0') // Skip deletions
          .map(block => block.blockTypeId)
      );

      if (blockTypeIds.size > 0) {
        logger.debug('Preloading BlockTypes for block updates', {
          uniqueBlockTypes: blockTypeIds.size,
        });
        await blockTypeService.preloadBlockTypes(Array.from(blockTypeIds));
      }

      // Track affected chunks for re-rendering
      const affectedChunks = new Set<string>();

      for (const block of blocks) {
        // Calculate chunk coordinates from block position
        const chunkCoord = worldToChunk(block.position.x, block.position.z, chunkSize);
        const chunkKey = getChunkKey(chunkCoord.cx, chunkCoord.cz);

        // Get chunk
        const clientChunk = this.chunks.get(chunkKey);
        if (!clientChunk) {
          // Chunk not loaded on client - ignore update
          logger.debug('Block update for unloaded chunk, ignoring', {
            position: block.position,
            cx: chunkCoord.cx,
            cz: chunkCoord.cz,
          });
          continue;
        }

        // Get position key
        const posKey = getBlockPositionKey(
          block.position.x,
          block.position.y,
          block.position.z
        );

        // Handle deletion (blockTypeId: '0')
        if (block.blockTypeId === '0') {
          const wasDeleted = clientChunk.data.data.delete(posKey);
          if (wasDeleted) {
            logger.debug('Block deleted', { position: block.position });
            affectedChunks.add(chunkKey);
          }
          continue;
        }

        // Handle update/create
        const blockType = await blockTypeService.getBlockType(block.blockTypeId);
        if (!blockType) {
          logger.warn('BlockType not found for block update', {
            blockTypeId: block.blockTypeId,
            position: block.position,
            loadedGroups: blockTypeService.getLoadedGroups(),
            knownBlockTypeIds: blockTypeService.getAllBlockTypeIds(),
            knownBlockTypeCount: blockTypeService.getBlockTypeCount(),
          });
          continue;
        }

        // Merge block modifiers
        const currentModifier = mergeBlockModifier(this.appContext, block, blockType, clientChunk.data.statusData.get(posKey));
        if (currentModifier.visibility == undefined) {
          // fallback default visibility
          currentModifier.visibility = {
            shape: 0
          };
        }

        // Create/update ClientBlock
        const clientBlock: ClientBlock = {
          block,
          chunk: { cx: chunkCoord.cx, cz: chunkCoord.cz },
          blockType,
          currentModifier,
          isVisible: true,
          isDirty: true,
          lastUpdate: Date.now(),
        };

        // Update in chunk
        clientChunk.data.data.set(posKey, clientBlock);

        // Load audio files for this block (non-blocking - loads in background)
        this.loadBlockAudio(clientBlock).catch(error => {
          logger.warn('Failed to load block audio on update (non-blocking)', {
            position: clientBlock.block.position,
            error: (error as Error).message,
          });
        });

        // Update permanent audio for this block (non-blocking)
        this.updatePermanentAudioForBlock(clientChunk, clientBlock, posKey).catch(error => {
          logger.warn('Failed to update permanent audio on block update (non-blocking)', {
            position: clientBlock.block.position,
            error: (error as Error).message,
          });
        });

        affectedChunks.add(chunkKey);

        logger.debug('Block updated', {
          position: block.position,
          blockTypeId: block.blockTypeId,
        });
      }

      // Emit events for affected chunks (triggers re-rendering)
      for (const chunkKey of affectedChunks) {
        const chunk = this.chunks.get(chunkKey);
        if (chunk) {
          // Mark for re-rendering
          chunk.isRendered = false;
          this.emit('chunk:updated', chunk);
          logger.debug('🔵 Emitting chunk:updated event', { chunkKey });
        }
      }

      logger.debug('🔵 Block updates applied', {
        totalBlocks: blocks.length,
        affectedChunks: affectedChunks.size,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'ChunkService.onBlockUpdate', {
        count: blocks.length,
      });
    }
  }

  /**
   * Handle item updates from server (new method that receives Items, not Blocks)
   *
   * Receives Item[] from server, fills them with ItemType data, converts to Blocks,
   * and updates the chunks.
   *
   * @param items - Array of item updates from server
   */
  async onItemUpdate(items: ItemBlockRef[]): Promise<void> {
    try {
      logger.debug('🔵 ChunkService.onItemUpdate called', {
        itemCount: items.length,
      });

      const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
      const blockTypeService = this.appContext.services.blockType;
      const itemService = this.appContext.services.item;

      if (!blockTypeService) {
        logger.warn('BlockTypeService not available - cannot process item updates');
        return;
      }

      if (!itemService) {
        logger.warn('ItemService not available - cannot process item updates');
        return;
      }

      // Preload BlockType 1 (ITEM)
      await blockTypeService.preloadBlockTypes([1]);

      // Track affected chunks for re-rendering
      const affectedChunks = new Set<string>();

      for (const item of items) {
        // Check if this is a delete marker
        if (item.texture === '__deleted__') {
          // Handle deletion
          const chunkCoord = worldToChunk(item.position.x, item.position.z, chunkSize);
          const chunkKey = getChunkKey(chunkCoord.cx, chunkCoord.cz);
          const clientChunk = this.chunks.get(chunkKey);

          if (!clientChunk) {
            logger.debug('Item delete for unloaded chunk, ignoring', {
              position: item.position,
              cx: chunkCoord.cx,
              cz: chunkCoord.cz,
            });
            continue;
          }

          const posKey = getBlockPositionKey(item.position.x, item.position.y, item.position.z);
          const existingBlock = clientChunk.data.data.get(posKey);

          // Only delete if an item exists at this position
          if (existingBlock && existingBlock.block.blockTypeId === '1') {
            const wasDeleted = clientChunk.data.data.delete(posKey);
            if (wasDeleted) {
              logger.debug('Item deleted', {
                position: item.position,
                itemId: item.id,
              });
              affectedChunks.add(chunkKey);
            }
          }
          continue;
        }

        // Calculate chunk coordinates
        const chunkCoord = worldToChunk(item.position.x, item.position.z, chunkSize);
        const chunkKey = getChunkKey(chunkCoord.cx, chunkCoord.cz);

        // Get chunk
        const clientChunk = this.chunks.get(chunkKey);
        if (!clientChunk) {
          logger.debug('Item update for unloaded chunk, ignoring', {
            position: item.position,
            cx: chunkCoord.cx,
            cz: chunkCoord.cz,
          });
          continue;
        }

        // Get position key
        const posKey = getBlockPositionKey(
          item.position.x,
          item.position.y,
          item.position.z
        );

        // Get existing block at this position
        const existingBlock = clientChunk.data.data.get(posKey);

        // Check if position is AIR or already has an item
        const isAir = !existingBlock;
        const isItem = existingBlock && existingBlock.block.blockTypeId === '1';

        if (!isAir && !isItem) {
          logger.debug('Item add/update ignored - position occupied by non-item block', {
            position: item.position,
            existingBlockTypeId: existingBlock.block.blockTypeId,
            itemId: item.id,
          });
          continue;
        }

        // Get BlockType 1 (ITEM)
        const blockType = await blockTypeService.getBlockType('1');
        if (!blockType) {
          logger.warn('BlockType 1 (ITEM) not found');
          continue;
        }

        // Convert Item to Block
        const block = itemToBlock(item);

        // Create/update ClientBlock with Item reference
        const clientBlock: ClientBlock = {
          block,
          chunk: { cx: chunkCoord.cx, cz: chunkCoord.cz },
          blockType,
          currentModifier: block.modifiers?.[0] || blockType.modifiers[0],
          isVisible: true,
          isDirty: true,
          lastUpdate: Date.now(),
          itemBlockRef: item, // Store complete Item
        };

        // Update in chunk
        clientChunk.data.data.set(posKey, clientBlock);

        // Load audio files for this block (non-blocking)
        this.loadBlockAudio(clientBlock).catch(error => {
          logger.warn('Failed to load item update audio (non-blocking)', {
            position: clientBlock.block.position,
            error: (error as Error).message,
          });
        });

        affectedChunks.add(chunkKey);

        logger.debug('Item added/updated', {
          position: item.position,
          itemId: item.id,
          wasUpdate: isItem,
        });
      }

      // Emit events for affected chunks (triggers re-rendering)
      for (const chunkKey of affectedChunks) {
        const chunk = this.chunks.get(chunkKey);
        if (chunk) {
          // Mark for re-rendering
          chunk.isRendered = false;
          this.emit('chunk:updated', chunk);
          logger.debug('🔵 Emitting chunk:updated event for items', { chunkKey });
        }
      }

      logger.debug('🔵 Item updates applied', {
        totalItems: items.length,
        affectedChunks: affectedChunks.size,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'ChunkService.onItemUpdate', {
        count: items.length,
      });
    }
  }

  /**
   * Get current render distance
   */
  getRenderDistance(): number {
    return this.renderDistance;
  }

  /**
   * Get current unload distance
   */
  getUnloadDistance(): number {
    return this.unloadDistance;
  }

  /**
   * Get total number of loaded chunks
   */
  getLoadedChunkCount(): number {
    return this.chunks.size;
  }

  /**
   * Get all loaded chunks
   */
  getAllChunks(): ClientChunk[] {
    return Array.from(this.chunks.values());
  }

  /**
   * Redraw a specific chunk
   *
   * Marks the chunk as not rendered and emits update event to trigger re-rendering
   *
   * @param chunkX - Chunk X coordinate
   * @param chunkZ - Chunk Z coordinate
   * @returns True if chunk was found and marked for redraw, false otherwise
   */
  redrawChunk(chunkX: number, chunkZ: number): boolean {
    const chunkKey = getChunkKey(chunkX, chunkZ);
    const chunk = this.chunks.get(chunkKey);

    if (!chunk) {
      logger.warn('Cannot redraw chunk - chunk not loaded', { chunkX, chunkZ });
      return false;
    }

    // Mark for re-rendering
    chunk.isRendered = false;
    this.emit('chunk:updated', chunk);

    logger.debug('Chunk marked for redraw', { chunkX, chunkZ, chunkKey });
    return true;
  }

  /**
   * Redraw all loaded chunks
   *
   * Marks all loaded chunks as not rendered and emits update events to trigger re-rendering
   *
   * @returns Number of chunks marked for redraw
   */
  redrawAllChunks(): number {
    const chunks = Array.from(this.chunks.values());

    logger.debug('Redrawing all chunks', { count: chunks.length });

    for (const chunk of chunks) {
      chunk.isRendered = false;
      this.emit('chunk:updated', chunk);
    }

    logger.debug('All chunks marked for redraw', { count: chunks.length });
    return chunks.length;
  }

  /**
   * Recalculate all currentModifiers for all blocks in all loaded chunks
   *
   * This should be called when WorldInfo changes (status, seasonStatus, seasonProgress)
   * as these values affect modifier merging.
   *
   * @returns Number of blocks whose modifiers were recalculated
   */
  recalculateAllModifiers(): number {
    const chunks = Array.from(this.chunks.values());
    let blockCount = 0;

    logger.debug('Recalculating all block modifiers', { chunkCount: chunks.length });

    for (const chunk of chunks) {
      // Iterate over all blocks in chunk
      for (const clientBlock of chunk.data.data.values()) {
        const posKey = getBlockPositionKey(
          clientBlock.block.position.x,
          clientBlock.block.position.y,
          clientBlock.block.position.z
        );

        // Recalculate currentModifier using current WorldInfo
        const newModifier = mergeBlockModifier(
          this.appContext,
          clientBlock.block,
          clientBlock.blockType,
          chunk.data.statusData.get(posKey)
        );

        // Update currentModifier
        clientBlock.currentModifier = newModifier;
        blockCount++;
      }

      // Mark chunk for re-rendering
      chunk.isRendered = false;
    }

    logger.debug('All block modifiers recalculated', { blockCount, chunkCount: chunks.length });
    return blockCount;
  }

  /**
   * Recalculate modifiers and redraw all chunks
   *
   * Convenience method that recalculates all modifiers and then triggers re-rendering.
   * Should be called when WorldInfo status or season changes.
   *
   * @returns Object with blockCount and chunkCount
   */
  recalculateAndRedrawAll(): { blockCount: number; chunkCount: number } {
    logger.debug('Recalculating modifiers and redrawing all chunks');

    const blockCount = this.recalculateAllModifiers();
    const chunkCount = this.redrawAllChunks();

    logger.debug('Recalculation and redraw complete', { blockCount, chunkCount });

    return { blockCount, chunkCount };
  }

  /**
   * Add event listener
   */
  on(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event) || [];
    listeners.push(listener);
    this.eventListeners.set(event, listeners);
  }

  /**
   * Remove event listener
   */
  off(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index !== -1) {
        listeners.splice(index, 1);
      }
    }
  }

  /**
   * Preload audio files for a ClientBlock into the sound pool
   * Preloads step and collision audio so they're ready when needed
   * Audio is retrieved from currentModifier at playback time (not stored in ClientBlock)
   *
   * @param clientBlock - The client block to preload audio for
   */
  private async loadBlockAudio(clientBlock: ClientBlock): Promise<void> {
    const audioService = this.appContext.services.audio;
    if (!audioService) {
      return; // AudioService not available
    }

    const audioModifier = clientBlock.currentModifier.audio;
    if (!audioModifier || audioModifier.length === 0) {
      return; // No audio defined
    }

    // Filter for step and collision audio (enabled)
    const audioToPreload = audioModifier.filter(
      def => (def.type === AudioType.STEPS || def.type === AudioType.COLLISION) && def.enabled
    );

    if (audioToPreload.length === 0) {
      return; // No audio to preload
    }

    // Preload all audio files into the sound pool
    for (const audioDef of audioToPreload) {
      try {
        // Load into pool with initial pool size based on type
        const initialPoolSize = audioDef.type === AudioType.STEPS ? 3 : 1;
        await audioService.loadSoundIntoPool(audioDef.path, initialPoolSize);

        logger.debug('Audio preloaded into pool', {
          path: audioDef.path,
          type: audioDef.type,
          blockTypeId: clientBlock.blockType.id,
        });
      } catch (error) {
        logger.warn('Failed to preload block audio', {
          path: audioDef.path,
          type: audioDef.type,
          blockTypeId: clientBlock.blockType.id,
          error: (error as Error).message,
        });
      }
    }
  }

  /**
   * Load permanent audio for all blocks in a chunk
   * Creates spatial ambient sounds that play continuously while chunk is visible
   *
   * @param clientChunk - The client chunk to load permanent audio for
   */
  private async loadPermanentAudioForChunk(clientChunk: ClientChunk): Promise<void> {
    const audioService = this.appContext.services.audio;
    if (!audioService) {
      return; // AudioService not available
    }

    // Initialize permanent audio map if needed
    if (!clientChunk.data.permanentAudioSounds) {
      clientChunk.data.permanentAudioSounds = new Map();
    }

    // Iterate over all blocks in the chunk
    for (const clientBlock of clientChunk.data.data.values()) {
      const posKey = getBlockPositionKey(
        clientBlock.block.position.x,
        clientBlock.block.position.y,
        clientBlock.block.position.z
      );

      const audioModifier = clientBlock.currentModifier.audio;
      if (!audioModifier || audioModifier.length === 0) {
        continue; // No audio defined for this block
      }

      // Filter for permanent audio - only use first enabled one
      const permanentAudioDefs = audioModifier.filter(
        def => def.type === AudioType.PERMANENT && def.enabled
      );

      if (permanentAudioDefs.length === 0) {
        continue; // No permanent audio for this block
      }

      // Use only the first enabled permanent audio definition
      const audioDef = permanentAudioDefs[0];

      try {
        // Create permanent sound for this block
        const sound = await audioService.createPermanentSoundForBlock(clientBlock, audioDef);

        if (sound) {
          // Ensure resourcesToDispose exists
          if (!clientChunk.data.resourcesToDispose) {
            clientChunk.data.resourcesToDispose = new DisposableResources();
          }

          // Add sound to disposable resources (will be disposed when chunk unloads)
          clientChunk.data.resourcesToDispose.add(sound);

          // Track sound by block position for updates
          clientChunk.data.permanentAudioSounds.set(posKey, sound);

          // Start playing (deferred sound will wait for audio unlock if needed)
          sound.play();

          logger.debug('Permanent sound registered for block (will play when audio unlocks)', {
            path: audioDef.path,
            blockTypeId: clientBlock.blockType.id,
            position: clientBlock.block.position,
          });
        }
      } catch (error) {
        logger.warn('Failed to create permanent sound for block', {
          path: audioDef.path,
          blockTypeId: clientBlock.blockType.id,
          position: clientBlock.block.position,
          error: (error as Error).message,
        });
      }
    }
  }

  /**
   * Update permanent audio for a single block after block update
   * Stops old sound and starts new sound if audio definition changed
   *
   * @param clientChunk - The client chunk containing the block
   * @param clientBlock - The updated client block
   * @param posKey - Block position key
   */
  private async updatePermanentAudioForBlock(
    clientChunk: ClientChunk,
    clientBlock: ClientBlock,
    posKey: string
  ): Promise<void> {
    const audioService = this.appContext.services.audio;
    if (!audioService) {
      return;
    }

    // Initialize permanent audio map if needed
    if (!clientChunk.data.permanentAudioSounds) {
      clientChunk.data.permanentAudioSounds = new Map();
    }

    // Stop and dispose old sound if it exists
    const oldSound = clientChunk.data.permanentAudioSounds.get(posKey);
    if (oldSound) {
      try {
        oldSound.stop();
        oldSound.dispose();
        clientChunk.data.permanentAudioSounds.delete(posKey);
        logger.debug('Stopped old permanent sound for block update', {
          position: clientBlock.block.position,
        });
      } catch (error) {
        logger.warn('Failed to stop old permanent sound', {
          position: clientBlock.block.position,
          error: (error as Error).message,
        });
      }
    }

    // Check if block has permanent audio
    const audioModifier = clientBlock.currentModifier.audio;
    if (!audioModifier || audioModifier.length === 0) {
      return; // No audio defined
    }

    // Filter for permanent audio - only use first enabled one
    const permanentAudioDefs = audioModifier.filter(
      def => def.type === AudioType.PERMANENT && def.enabled
    );

    if (permanentAudioDefs.length === 0) {
      return; // No permanent audio
    }

    // Use only the first enabled permanent audio definition
    const audioDef = permanentAudioDefs[0];

    try {
      // Create new permanent sound for this block
      const sound = await audioService.createPermanentSoundForBlock(clientBlock, audioDef);

      if (sound) {
        // Ensure resourcesToDispose exists
        if (!clientChunk.data.resourcesToDispose) {
          clientChunk.data.resourcesToDispose = new DisposableResources();
        }

        // Add sound to disposable resources
        clientChunk.data.resourcesToDispose.add(sound);

        // Track sound by block position
        clientChunk.data.permanentAudioSounds.set(posKey, sound);

        // Start playing (deferred sound will wait for audio unlock if needed)
        sound.play();

        logger.debug('Permanent sound registered for block update (will play when audio unlocks)', {
          path: audioDef.path,
          blockTypeId: clientBlock.blockType.id,
          position: clientBlock.block.position,
        });
      }
    } catch (error) {
      logger.warn('Failed to create permanent sound for block update', {
        path: audioDef.path,
        blockTypeId: clientBlock.blockType.id,
        position: clientBlock.block.position,
        error: (error as Error).message,
      });
    }
  }

  /**
   * Emit event
   */
  private emit(event: string, ...args: any[]): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          ExceptionHandler.handle(error, 'ChunkService.emit', { event });
        }
      });
    }
  }

}
