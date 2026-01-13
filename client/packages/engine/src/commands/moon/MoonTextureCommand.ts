/**
 * MoonTextureCommand - Set moon texture
 *
 * Usage: moonTexture [moonIndex] [texturePath]
 * - moonIndex: 0, 1, or 2
 * - texturePath: Path to texture (e.g., 'textures/moon/moon1.png') or 'none' to remove
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonTextureCommand');

export class MoonTextureCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonTexture';
  }

  description(): string {
    return 'Set moon texture (moonTexture [0-2] [path] or "none")';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonTexture [moonIndex] [texturePath]\nExample: moonTexture 0 textures/moon/moon1.png\nOr: moonTexture 0 none (to remove texture)';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const texturePath = parameters[1];

    if (texturePath.toLowerCase() === 'none') {
      await moonService.setMoonTexture(moonIndex, null);
      logger.debug(`Moon ${moonIndex} texture removed`);
      return `Moon ${moonIndex} texture removed (using geometric rendering)`;
    } else {
      await moonService.setMoonTexture(moonIndex, texturePath);
      logger.debug(`Moon ${moonIndex} texture set to ${texturePath}`);
      return `Moon ${moonIndex} texture set to ${texturePath}`;
    }
  }
}
