/**
 * SunTextureCommand - Set sun texture
 *
 * Usage: sunTexture [path]
 * - Without parameters: Shows current sun texture path
 * - With parameter: Sets sun texture from asset path
 * - Use "null" or "reset" to reset to default circular disc
 *
 * Example: sunTexture textures/sun_star.png
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SunTextureCommand');

export class SunTextureCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunTexture';
  }

  description(): string {
    return 'Set sun texture from asset path (or "null" for default)';
  }

  async execute(parameters: string[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current texture path if no parameters
    if (parameters.length === 0) {
      const texturePath = this.appContext.worldInfo?.settings.sunTexture;
      if (texturePath) {
        return `Current sun texture: ${texturePath}`;
      } else {
        return 'Current sun texture: default circular disc';
      }
    }

    const texturePath = parameters[0];

    // Reset to default if null/reset
    if (texturePath === 'null' || texturePath === 'reset') {
      await sunService.setSunTexture(null);
      logger.debug('Sun texture reset to default');
      return 'Sun texture reset to default circular disc';
    }

    // Set custom texture
    await sunService.setSunTexture(texturePath);
    logger.debug('Sun texture set', { path: texturePath });

    return `Sun texture set to: ${texturePath}`;
  }
}
