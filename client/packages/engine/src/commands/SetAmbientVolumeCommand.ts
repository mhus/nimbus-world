/**
 * SetAmbientVolumeCommand - Set ambient music volume multiplier
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SetAmbientVolumeCommand');
import type { AppContext } from '../AppContext';

/**
 * Set ambient volume command
 *
 * Usage:
 *   /setAmbientVolume <volume>   - Set ambient music volume (0.0 - 1.0)
 *   /setAmbientVolume            - Show current ambient volume
 *
 * Examples:
 *   /setAmbientVolume 0.5
 *   /setAmbientVolume 0.8
 *   /setAmbientVolume 0    - Mute ambient music
 */
export class SetAmbientVolumeCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'setAmbientVolume';
  }

  description(): string {
    return 'Set ambient music volume multiplier (/setAmbientVolume <0.0-1.0>)';
  }

  execute(parameters: any[]): any {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    // No parameters - show current volume
    if (parameters.length === 0) {
      const currentVolume = audioService.getAmbientVolume();
      logger.debug(`Current ambient volume: ${currentVolume.toFixed(2)}`);
      return { ambientVolume: currentVolume };
    }

    // Parse volume parameter
    const volume = parseFloat(String(parameters[0]));

    if (isNaN(volume)) {
      logger.error('Invalid volume parameter. Must be a number between 0.0 and 1.0');
      return { error: 'Invalid volume parameter' };
    }

    // Set volume (will be clamped in setAmbientVolume)
    audioService.setAmbientVolume(volume);

    const actualVolume = audioService.getAmbientVolume();
    logger.debug(`✓ Ambient volume set to ${actualVolume.toFixed(2)}`);

    if (actualVolume <= 0) {
      logger.debug('  Ambient music will be stopped/muted');
    }

    return { ambientVolume: actualVolume };
  }
}
