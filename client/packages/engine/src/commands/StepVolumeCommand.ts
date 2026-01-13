/**
 * StepVolumeCommand - Set step sound volume multiplier
 *
 * Usage: /stepvolume <volume>
 * Example: /stepvolume 0.5
 */

import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('StepVolumeCommand');

export class StepVolumeCommand {
  constructor(private appContext: AppContext) {}

  name(): string {
    return 'stepvolume';
  }

  description(): string {
    return 'Set step sound volume multiplier (0.0-1.0)';
  }

  execute(args: string[]): void {
    if (args.length === 0) {
      // Show current volume
      const audioService = this.appContext.services.audio;
      if (!audioService) {
        logger.error('AudioService not available');
        return;
      }

      const currentVolume = audioService.getStepVolume();
      logger.debug(`Current step volume: ${currentVolume}`);
      return;
    }

    const volumeStr = args[0];
    const volume = parseFloat(volumeStr);

    if (isNaN(volume)) {
      logger.error(`Invalid volume: ${volumeStr}. Must be a number between 0.0 and 1.0`);
      return;
    }

    if (volume < 0 || volume > 1) {
      logger.error(`Volume out of range: ${volume}. Must be between 0.0 and 1.0`);
      return;
    }

    const audioService = this.appContext.services.audio;
    if (!audioService) {
      logger.error('AudioService not available');
      return;
    }

    audioService.setStepVolume(volume);
    logger.debug(`Step volume set to ${volume}`);
  }
}
