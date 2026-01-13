/**
 * PlayEntityAudioCommand - Play audio for an entity
 */

import { getLogger } from '@nimbus/shared';
import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';

const logger = getLogger('PlayEntityAudioCommand');

/**
 * Play entity audio command
 *
 * Usage:
 *   /playEntityAudio <entityId> <type>  - Play audio for entity
 *
 * Examples:
 *   /playEntityAudio wizard1 attack
 *   /playEntityAudio @player_avatar hurt
 *   /playEntityAudio npc_guard idle
 */
export class PlayEntityAudioCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'playEntityAudio';
  }

  description(): string {
    return 'Play audio for an entity (/playEntityAudio <entityId> <type>)';
  }

  async execute(parameters: any[]): Promise<any> {
    const audioService = this.appContext.services.audio;
    const entityService = this.appContext.services.entity;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    if (!entityService) {
      logger.error('EntityService not available');
      return { error: 'EntityService not available' };
    }

    // Require entityId and type parameters
    if (parameters.length < 2) {
      logger.error('Usage: /playEntityAudio <entityId> <type>');
      logger.debug('Example: /playEntityAudio wizard1 attack');
      return { error: 'Missing parameters' };
    }

    const entityId = String(parameters[0]);
    const audioType = String(parameters[1]);

    try {
      // Get entity from EntityService
      const entity = await entityService.getEntity(entityId);
      if (!entity) {
        logger.error(`Entity not found: ${entityId}`);
        return { error: `Entity not found: ${entityId}` };
      }

      // Play entity audio
      await audioService.playEntityAudio(entity, audioType);

      logger.debug(`✓ Playing entity audio: ${entityId} / ${audioType}`);
      return { status: 'playing', entityId, audioType };
    } catch (error) {
      logger.error('Failed to play entity audio', error);
      return { error: 'Failed to play entity audio' };
    }
  }
}
