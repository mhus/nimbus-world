/**
 * PlaySoundCommand - Play sound directly (non-spatial)
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import {  toBoolean, toNumber , getLogger } from '@nimbus/shared';

const logger = getLogger('PlaySoundCommand');

/**
 * Play sound command
 *
 * Usage:
 *   /playSound <soundPath> [stream] [volume]
 *
 * Examples:
 *   /playSound audio/ui/click.ogg false 1.0
 *   /playSound audio/effects/notification.mp3 true 0.8
 *   /playSound audio/ui/beep.wav
 */
export class PlaySoundCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'playSound';
  }

  description(): string {
    return 'Play sound directly (non-spatial) (/playSound <path> [stream] [volume])';
  }

  async execute(parameters: any[]): Promise<any> {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    // Require at least soundPath parameter
    if (parameters.length === 0) {
      logger.error('Usage: /playSound <soundPath> [stream] [volume]');
      logger.debug('Example: /playSound audio/ui/click.ogg false 1.0');
      return { error: 'Missing soundPath parameter' };
    }

    // Parse parameters
    const soundPath = String(parameters[0]);
    const stream = parameters.length > 1 ? toBoolean(parameters[1]) : false;
    const volume = parameters.length > 2 ? toNumber(parameters[2]) : 1.0;

    // Validate volume
    if (isNaN(volume) || volume < 0 || volume > 1) {
      logger.error('Invalid volume. Must be between 0.0 and 1.0');
      return { error: 'Invalid volume parameter' };
    }

    // Play sound
    try {
      await audioService.playSound(soundPath, stream, volume);

      logger.debug(`✓ Playing sound: ${soundPath}`);
      logger.debug(`  Stream: ${stream}, Volume: ${volume}`);

      return { status: 'playing', soundPath, stream, volume };
    } catch (error) {
      logger.error('Failed to play sound', error);
      return { error: 'Failed to play sound' };
    }
  }
}
