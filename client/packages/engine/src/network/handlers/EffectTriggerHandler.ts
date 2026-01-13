/**
 * EffectTriggerHandler - Handles effect trigger messages from server
 *
 * Receives effect triggers from server (synchronized from other clients)
 * and executes them locally.
 *
 * Effects received from server are marked as remote (won't be re-sent to server).
 */

import {
  BaseMessage,
  MessageType,
  type EffectTriggerData,
  getLogger,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { ScrawlService } from '../../scrawl/ScrawlService';

const logger = getLogger('EffectTriggerHandler');

/**
 * Handles EFFECT_TRIGGER messages from server (s.t)
 */
export class EffectTriggerHandler extends MessageHandler<EffectTriggerData> {
  readonly messageType = MessageType.EFFECT_TRIGGER;

  constructor(private scrawlService: ScrawlService) {
    super();
  }

  async handle(message: BaseMessage<EffectTriggerData>): Promise<void> {
    const data = message.d;

    if (!data) {
      logger.warn('Effect trigger message without data');
      return;
    }

    logger.debug('Effect trigger received from server', {
      effectId: data.effectId,
      entityId: data.entityId,
      chunkCount: data.chunks?.length || 0,
      hasEffect: !!data.effect,
    });

    if (!data.effect || !data.effectId) {
      logger.warn('Effect trigger without effect definition or effectId');
      return;
    }

    // Check if this effect was sent by us (would have been already executed locally)
    if (this.scrawlService.wasEffectSentByUs(data.effectId)) {
      logger.debug('Effect was sent by us, skipping (already executed locally)', {
        effectId: data.effectId,
      });
      return;
    }

    // Check if this effect was already received (prevent duplicate execution)
    if (!this.scrawlService.markEffectAsReceived(data.effectId)) {
      logger.debug('Effect already received, skipping (duplicate message)', {
        effectId: data.effectId,
      });
      return;
    }

    try {
      // Mark this effect as remote (came from server, don't send back)
      // IMPORTANT: Remove any isLocal from the effect itself (came from sender)
      const { isLocal: _removeIsLocal, ...cleanEffect } = data.effect as any;

      const effectWithFlag = {
        ...cleanEffect,
        sendToServer: false, // Prevent re-broadcasting
      };

      // Execute the effect locally with isLocal: false
      // The effect already contains source, target, and all parameters
      // IMPORTANT: Force isLocal to false regardless of what server sends
      const executorId = await this.scrawlService.executeAction(
        effectWithFlag,
        {
          isLocal: false, // Force remote execution - don't activate player direction
          // Override any vars that might come from server
          vars: {
            ...(data.effect.parameters || {}),
          },
        }
      );

      // Register remote effect mapping for parameter updates (s.u)
      this.scrawlService.registerRemoteEffectMapping(data.effectId, executorId);

      logger.debug('Remote effect executed and registered', {
        executorId,
        effectId: data.effectId,
        entityId: data.entityId,
        isLocal: false,
      });
    } catch (error) {
      logger.error('Failed to execute remote effect', {
        effectId: data.effectId,
        entityId: data.entityId,
        error: (error as Error).message,
      });
    }
  }
}
