/**
 * SkyBoxColorCommand - Set skybox color
 *
 * Usage: skyBoxColor [r] [g] [b]
 * - Without parameters: Shows usage information
 * - With 3 parameters: Sets skybox color (RGB values 0-1)
 *   - Example: skyBoxColor 0.2 0.5 1.0 (sky blue)
 *   - Example: skyBoxColor 1 1 1 (white)
 *   - Example: skyBoxColor 0.1 0.1 0.2 (dark blue)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';
import { Color3 } from '@babylonjs/core';

const logger = getLogger('SkyBoxColorCommand');

export class SkyBoxColorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxColor';
  }

  description(): string {
    return 'Set skybox color (r g b, values 0-1)';
  }

  async execute(parameters: any[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: skyBoxColor [r] [g] [b] (values 0-1)\nExample: skyBoxColor 0.2 0.5 1.0';
    }

    // Require exactly 3 parameters
    if (parameters.length !== 3) {
      return 'Invalid parameters. Provide exactly 3 values (r, g, b).';
    }

    // Parse and validate parameters
    const r = toNumber(parameters[0]);
    const g = toNumber(parameters[1]);
    const b = toNumber(parameters[2]);

    if (isNaN(r) || isNaN(g) || isNaN(b)) {
      return 'Invalid parameters. All values must be numbers (0-1).';
    }

    if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1) {
      return 'Values out of bounds. RGB values must be between 0 and 1.';
    }

    // Set skybox color
    skyBoxService.setColorMode(new Color3(r, g, b));

    logger.debug('SkyBox color set via command', { r, g, b });

    return `SkyBox color set to RGB(${r.toFixed(2)}, ${g.toFixed(2)}, ${b.toFixed(2)})`;
  }
}
