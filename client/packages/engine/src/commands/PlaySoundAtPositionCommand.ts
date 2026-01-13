/**
 * PlaySoundAtPositionCommand - Play sound at specific world position
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('PlaySoundAtPositionCommand');
import type { AppContext } from '../AppContext';

/**
 * Play sound at position command
 *
 * Usage:
 *   /playSoundAtPosition <soundPath> <x> <y> <z> [volume]
 *
 * Examples:
 *   /playSoundAtPosition audio/effects/explosion.ogg 10 64 5 1.0
 *   /playSoundAtPosition audio/effects/teleport.ogg 0 70 0 0.8
 *   /playSoundAtPosition audio/effects/bell.ogg 5 65 10
 */
export class PlaySoundAtPositionCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'playSoundAtPosition';
  }

  description(): string {
    return 'Play sound at world position (/playSoundAtPosition <path> <x> <y> <z> [volume])';
  }

  async execute(parameters: any[]): Promise<any> {
    const audioService = this.appContext.services.audio;

    if (!audioService) {
      logger.error('AudioService not available');
      return { error: 'AudioService not available' };
    }

    // Require at least soundPath and x, y, z parameters
    if (parameters.length < 4) {
      logger.error('Usage: /playSoundAtPosition <soundPath> <x> <y> <z> [volume]');
      logger.debug('Example: /playSoundAtPosition audio/effects/explosion.ogg 10 64 5 1.0');
      return { error: 'Missing parameters (need at least: soundPath, x, y, z)' };
    }

    // Parse parameters
    const soundPath = String(parameters[0]);
    const x = parseFloat(String(parameters[1]));
    const y = parseFloat(String(parameters[2]));
    const z = parseFloat(String(parameters[3]));
    const volume = parameters.length > 4 ? parseFloat(String(parameters[4])) : 1.0;

    // Validate coordinates
    if (isNaN(x) || isNaN(y) || isNaN(z)) {
      logger.error('Invalid coordinates. x, y, z must be numbers');
      return { error: 'Invalid coordinates' };
    }

    // Validate volume
    if (isNaN(volume) || volume < 0 || volume > 1) {
      logger.error('Invalid volume. Must be between 0.0 and 1.0');
      return { error: 'Invalid volume parameter' };
    }

    // Play sound at position
    try {
      await audioService.playSoundAtPosition(soundPath, x, y, z, volume);

      logger.debug(`✓ Playing sound at position: ${soundPath}`);
      logger.debug(`  Position: (${x}, ${y}, ${z})`);
      logger.debug(`  Volume: ${volume}`);

      return { status: 'playing', soundPath, position: { x, y, z }, volume };
    } catch (error) {
      logger.error('Failed to play sound at position', error);
      return { error: 'Failed to play sound' };
    }
  }
}
