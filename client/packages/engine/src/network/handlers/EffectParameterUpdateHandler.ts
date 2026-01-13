/**
 * EffectParameterUpdateHandler - Handles effect parameter update messages from server
 *
 * Receives parameter updates for running effects from server (synchronized from other clients)
 * and updates the local effect parameters.
 */

import {
  BaseMessage,
  MessageType,
  type EffectParameterUpdateData,
  getLogger,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { ScrawlService } from '../../scrawl/ScrawlService';

const logger = getLogger('EffectParameterUpdateHandler');

/**
 * Handles EFFECT_PARAMETER_UPDATE messages from server (s.u)
 */
export class EffectParameterUpdateHandler extends MessageHandler<EffectParameterUpdateData> {
  readonly messageType = MessageType.EFFECT_PARAMETER_UPDATE;

  constructor(private scrawlService: ScrawlService) {
    super();
  }

  async handle(message: BaseMessage<EffectParameterUpdateData>): Promise<void> {
    const data = message.d;

    if (!data) {
      logger.warn('Effect parameter update message without data');
      return;
    }

    logger.debug('Effect parameter update received from server', {
      effectId: data.effectId,
      paramName: data.paramName,
      value: data.value
    });

    if (!data.effectId || !data.paramName) {
      logger.warn('Invalid effect parameter update data');
      return;
    }

    try {
      const updated = this.scrawlService.updateExecutorParameter(
        data.effectId,
        data.paramName,
        data.value,
        data.targeting
      );

      if (updated) {
        logger.debug('Remote parameter update applied successfully', {
          effectId: data.effectId,
          paramName: data.paramName,
        });
      } else {
        logger.warn('Remote parameter update failed: executor not found', {
          effectId: data.effectId,
          paramName: data.paramName,
        });
      }
    } catch (error) {
      logger.warn('Failed to apply remote parameter update', {
        effectId: data.effectId,
        paramName: data.paramName,
        error: (error as Error).message,
      });
    }
  }
}
