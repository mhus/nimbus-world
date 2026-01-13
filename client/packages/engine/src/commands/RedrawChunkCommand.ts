/**
 * RedrawChunkCommand - Redraw chunks
 *
 * Redraws a specific chunk or all loaded chunks by marking them as not rendered
 * and emitting update events.
 *
 * Usage:
 * - /redrawChunk - Redraw all loaded chunks
 * - /redrawChunk <chunkX> <chunkZ> - Redraw specific chunk
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('RedrawChunkCommand');

export class RedrawChunkCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'redrawChunk';
  }

  description(): string {
    return 'Redraw chunks (all or specific chunk)';
  }

  async execute(args: string[]): Promise<void> {
    const chunkService = this.appContext.services.chunk;

    if (!chunkService) {
      logger.error('ChunkService not available');
      return;
    }

    // Check if chunk coordinates are provided
    if (args.length >= 2) {
      // Redraw specific chunk
      const chunkX = parseInt(args[0], 10);
      const chunkZ = parseInt(args[1], 10);

      if (isNaN(chunkX) || isNaN(chunkZ)) {
        logger.error('Invalid chunk coordinates. Must be integers.');
        logger.debug('Usage: /redrawChunk [chunkX] [chunkZ]');
        return;
      }

      logger.debug('Redrawing specific chunk', { chunkX, chunkZ });
      const success = chunkService.redrawChunk(chunkX, chunkZ);

      if (success) {
        logger.debug(`Chunk (${chunkX}, ${chunkZ}) marked for redraw`);
      } else {
        logger.warn(`Chunk (${chunkX}, ${chunkZ}) not loaded - cannot redraw`);
      }
    } else {
      // Redraw all chunks
      logger.debug('Redrawing all loaded chunks');
      const count = chunkService.redrawAllChunks();
      logger.debug(`${count} chunk(s) marked for redraw`);
    }
  }
}
