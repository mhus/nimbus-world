/**
 * AudioCommand - Toggle audio playback on/off
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('AudioCommand');
import type { AppContext } from '../AppContext';

/**
 * Audio command - Enable/disable audio playback
 *
 * Usage:
 *   /audio on     - Enable audio
 *   /audio off    - Disable audio
 *   /audio        - Show current status
 */
export class AudioCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'audio';
  }

  description(): string {
    return 'Enable or disable audio playback (/audio on|off)';
  }

  execute(parameters: any[]): any {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    // No parameters - show status
    if (parameters.length === 0) {
      const enabled = audioService.isAudioEnabled();
      logger.debug(`Audio playback is currently: ${enabled ? 'ENABLED' : 'DISABLED'}`);
      return { enabled };
    }

    // Parse parameter
    const param = String(parameters[0]).toLowerCase();

    if (param === 'on' || param === 'enable' || param === '1' || param === 'true') {
      audioService.setAudioEnabled(true);
      logger.debug('✓ Audio playback ENABLED');
      return { enabled: true };
    } else if (param === 'off' || param === 'disable' || param === '0' || param === 'false') {
      audioService.setAudioEnabled(false);
      logger.debug('✓ Audio playback DISABLED');
      return { enabled: false };
    } else {
      logger.error(`Invalid parameter: ${param}. Use 'on' or 'off'`);
      return { error: 'Invalid parameter' };
    }
  }
}
