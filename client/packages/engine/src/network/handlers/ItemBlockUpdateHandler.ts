/**
 * ItemBlockUpdateHandler - Handles item update messages (b.iu)
 *
 * Receives item updates from server (as Item objects), converts them to Blocks,
 * and forwards to ChunkService for updating individual items in loaded chunks.
 *
 * Item updates can be:
 * - New items (converted to blockTypeId: 1)
 * - Modified items (converted to blockTypeId: 1, replaces existing item)
 * - Deleted items (blockTypeId: 0, only if item exists)
 *
 * Items can only exist at AIR positions or replace existing items.
 */

import {
  BaseMessage,
  MessageType,
  type Item,
  getLogger, ItemBlockRef,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { ChunkService } from '../../services/ChunkService';

const logger = getLogger('ItemBlockUpdateHandler');

/**
 * Handles ITEM_BLOCK_UPDATE messages from server (b.iu)
 */
export class ItemBlockUpdateHandler extends MessageHandler<ItemBlockRef[]> {
  readonly messageType = MessageType.ITEM_BLOCK_UPDATE;

  constructor(private chunkService: ChunkService) {
    super();
  }

  async handle(message: BaseMessage<ItemBlockRef[]>): Promise<void> {
    const items = message.d;

    logger.debug('🔵 ITEM UPDATE MESSAGE RECEIVED (b.iu)', {
      messageType: message.t,
      itemCount: items?.length || 0,
      rawMessage: message,
    });

    if (!items || items.length === 0) {
      logger.warn('Received empty item update');
      return;
    }

    logger.debug('Processing item updates', {
      count: items.length,
      items: items.map(item => ({
        position: item.position,
        itemId: item.id,
        isDelete: item.texture === '__deleted__',
      })),
    });

    // Forward Items directly to ChunkService
    // ChunkService will handle filling and conversion
    await this.chunkService.onItemUpdate(items);

    logger.debug('Item updates forwarded to ChunkService');
  }
}
