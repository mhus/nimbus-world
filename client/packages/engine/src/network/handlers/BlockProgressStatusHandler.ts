/**
 * BlockProgressStatusHandler - Handles block progress status update messages (b.ps)
 *
 * Receives block status changes from server (e.g., door open/closed)
 * and forwards to ChunkService for updating block modifiers and re-rendering.
 */

import {
  BaseMessage,
  MessageType,
  type BlockProgressStatusData,
  getLogger,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { ChunkService } from '../../services/ChunkService';

const logger = getLogger('BlockProgressStatusHandler');

/**
 * Handles BLOCK_PROGRESS_STATUS_UPDATE messages from server (b.ps)
 */
export class BlockProgressStatusHandler extends MessageHandler<BlockProgressStatusData> {
  readonly messageType = MessageType.BLOCK_PROGRESS_STATUS_UPDATE;

  constructor(private chunkService: ChunkService) {
    super();
  }

  async handle(message: BaseMessage<BlockProgressStatusData>): Promise<void> {
    const data = message.d;
    if (!data || !data.s) {
      logger.warn('Received empty block progress status update');
      return;
    }

    logger.debug('Block progress status update received', {
      cx: data.cx,
      cz: data.cz,
      entries: Object.keys(data.s).length,
    });

    await this.chunkService.onBlockProgressStatusUpdate(data);
  }
}
