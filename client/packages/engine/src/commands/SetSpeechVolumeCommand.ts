/**
 * SetSpeechVolumeCommand - Set speech/narration volume multiplier
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SetSpeechVolumeCommand');

/**
 * Set speech volume command
 *
 * Usage:
 *   /setSpeechVolume <volume>   - Set speech volume (0.0 - 1.0)
 *   /setSpeechVolume            - Show current speech volume
 *
 * Examples:
 *   /setSpeechVolume 1.0
 *   /setSpeechVolume 0.8
 *   /setSpeechVolume 0    - Mute speech
 */
export class SetSpeechVolumeCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'setSpeechVolume';
  }

  description(): string {
    return 'Set speech volume multiplier (/setSpeechVolume <0.0-1.0>)';
  }

  execute(parameters: any[]): any {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    // No parameters - show current volume
    if (parameters.length === 0) {
      const currentVolume = audioService.getSpeechVolume();
      logger.debug(`Current speech volume: ${currentVolume.toFixed(2)}`);
      return { speechVolume: currentVolume };
    }

    // Parse volume parameter
    const volume = parseFloat(String(parameters[0]));

    if (isNaN(volume)) {
      logger.error('Invalid volume parameter. Must be a number between 0.0 and 1.0');
      return { error: 'Invalid volume parameter' };
    }

    // Set volume (will be clamped in setSpeechVolume)
    audioService.setSpeechVolume(volume);

    const actualVolume = audioService.getSpeechVolume();
    logger.debug(`✓ Speech volume set to ${actualVolume.toFixed(2)}`);

    if (actualVolume <= 0) {
      logger.debug('  Speech will be muted');
    }

    return { speechVolume: actualVolume };
  }
}
