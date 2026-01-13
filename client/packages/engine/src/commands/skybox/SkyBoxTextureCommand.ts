/**
 * SkyBoxTextureCommand - Set skybox texture
 *
 * Usage: skyBoxTexture [basePath]
 * - Without parameters: Shows usage information
 * - With basePath: Sets skybox to texture mode with the specified cube texture
 *   - Example: skyBoxTexture textures/skybox/stars
 *   - Note: Expects 6 files: basePath_px.png, _nx.png, _py.png, _ny.png, _pz.png, _nz.png
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SkyBoxTextureCommand');

export class SkyBoxTextureCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxTexture';
  }

  description(): string {
    return 'Set skybox texture (basePath)';
  }

  async execute(parameters: string[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: skyBoxTexture [basePath]\nExample: skyBoxTexture textures/skybox/stars';
    }

    const basePath = parameters[0];

    if (!basePath || basePath.trim().length === 0) {
      return 'Invalid texture path. Provide a valid base path.';
    }

    try {
      // Set skybox to texture mode
      await skyBoxService.setTextureMode(basePath);

      logger.debug('SkyBox texture set via command', { basePath });

      return `SkyBox texture set to: ${basePath}`;
    } catch (error) {
      logger.error('Failed to set skybox texture', { basePath, error });
      return `Failed to set skybox texture: ${error}`;
    }
  }
}
