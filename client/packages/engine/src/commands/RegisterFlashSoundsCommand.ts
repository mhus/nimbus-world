/**
 * RegisterFlashSoundsCommand - Register thunder sounds for lightning flashes
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('RegisterFlashSoundsCommand');
import type { AppContext } from '../AppContext';

/**
 * Register flash sounds command
 *
 * Usage:
 *   /registerFlashSounds <soundPath1> [soundPath2] [soundPath3] ...
 *   /registerFlashSounds (empty list to clear)
 *
 * Examples:
 *   /registerFlashSounds audio/weather/thunder1.ogg audio/weather/thunder2.ogg
 *   /registerFlashSounds audio/weather/thunder1.ogg
 *   /registerFlashSounds (clears the list)
 */
export class RegisterFlashSoundsCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'registerFlashSounds';
  }

  description(): string {
    return 'Register thunder sounds for lightning flashes (/registerFlashSounds [path1] [path2] ...)';
  }

  async execute(parameters: any[]): Promise<any> {
    const precipitationService = this.appContext.services.precipitation;

    if (!precipitationService) {
      logger.error('PrecipitationService not available');
      return { error: 'PrecipitationService not available' };
    }

    // Convert parameters to string array
    const soundPaths: string[] = parameters.map(p => String(p));

    // Register sounds (empty array clears the list)
    precipitationService.registerFlashSounds(soundPaths);

    if (soundPaths.length === 0) {
      logger.debug('✓ Thunder sounds cleared');
      return { status: 'cleared', count: 0 };
    }

    logger.debug(`✓ Registered ${soundPaths.length} thunder sound(s):`);
    soundPaths.forEach((path, index) => {
      logger.debug(`  ${index + 1}. ${path}`);
    });

    return { status: 'registered', count: soundPaths.length, paths: soundPaths };
  }
}
