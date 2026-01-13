/**
 * PlayAmbientAudioCommand - Play ambient background music
 */

import { getLogger } from '@nimbus/shared';
import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { StackName } from '../services/ModifierService';
import type { Modifier } from '../services/ModifierService';

const logger = getLogger('PlayAmbientAudioCommand');

/**
 * Play ambient audio command
 *
 * Usage:
 *   /playAmbientAudio <soundPath> [stream] [volume]  - Play ambient music
 *   /playAmbientAudio ""                              - Stop ambient music
 *
 * Examples:
 *   /playAmbientAudio audio/music/ambient1.ogg true 0.8
 *   /playAmbientAudio audio/music/forest.mp3 false 1.0
 *   /playAmbientAudio ""
 *
 * Note: Uses StackModifier with priority 100 (command override)
 */
export class PlayAmbientAudioCommand extends CommandHandler {
  private appContext: AppContext;
  private commandModifier?: Modifier<string>; // Priority 100

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'playAmbientAudio';
  }

  description(): string {
    return 'Play ambient background music (/playAmbientAudio <path> [stream] [volume])';
  }

  async execute(parameters: any[]): Promise<any> {
    const modifierService = this.appContext.services.modifier;

    if (!modifierService) {
      logger.error('ModifierService not available');
      return { error: 'ModifierService not available' };
    }

    // Require at least soundPath parameter
    if (parameters.length === 0) {
      logger.error('Usage: /playAmbientAudio <soundPath>');
      logger.debug('Example: /playAmbientAudio audio/music/ambient1.ogg');
      logger.debug('Stop ambient: /playAmbientAudio ""');
      return { error: 'Missing soundPath parameter' };
    }

    // Parse soundPath parameter (stream and volume are always true/1.0 per spec)
    const soundPath = String(parameters[0]);

    try {
      // Get ambient audio stack
      const stack = modifierService.getModifierStack<string>(StackName.AMBIENT_AUDIO);
      if (!stack) {
        logger.error('Ambient audio stack not available');
        return { error: 'Ambient audio stack not available' };
      }

      // Create or update command modifier (priority 100)
      if (!this.commandModifier) {
        this.commandModifier = stack.addModifier(soundPath, 100);
        logger.debug('Command modifier created', { soundPath, prio: 100 });
      } else {
        this.commandModifier.setValue(soundPath);
        logger.debug('Command modifier updated', { soundPath });
      }

      // Enable/disable based on path
      this.commandModifier.setEnabled(soundPath.trim() !== '');

      if (soundPath.trim() === '') {
        logger.debug('✓ Ambient music command cleared (reverts to lower priority)');
        return { status: 'cleared' };
      } else {
        logger.debug(`✓ Ambient music command set: ${soundPath}`);
        logger.debug(`  Priority: 100 (command override)`);
        return { status: 'set', soundPath };
      }
    } catch (error) {
      logger.error('Failed to set ambient music', error);
      return { error: 'Failed to set ambient music' };
    }
  }
}
