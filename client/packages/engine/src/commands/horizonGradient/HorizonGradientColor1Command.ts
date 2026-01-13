/**
 * HorizonGradientColor1Command - Set horizon gradient top color
 *
 * Usage: horizonGradientColor1 <r> <g> <b>
 * - r, g, b: RGB color values (0.0 to 1.0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';
import { Color3 } from '@babylonjs/core';

const logger = getLogger('HorizonGradientColor1Command');

export class HorizonGradientColor1Command extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientColor1';
  }

  description(): string {
    return 'Set horizon gradient top color (r g b)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length < 3) {
      return 'Usage: horizonGradientColor1 <r> <g> <b>\nExample: horizonGradientColor1 0.3 0.5 0.8';
    }

    const r = toNumber(parameters[0]);
    const g = toNumber(parameters[1]);
    const b = toNumber(parameters[2]);

    if (isNaN(r) || isNaN(g) || isNaN(b)) {
      return 'Invalid color values. Must be numbers between 0.0 and 1.0.';
    }

    if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1) {
      return 'Color values must be between 0.0 and 1.0.';
    }

    const color = new Color3(r, g, b);
    service.setColor1(color);
    logger.debug('Horizon gradient color1 set via command', { r, g, b });
    return `Horizon gradient top color set to RGB(${r}, ${g}, ${b})`;
  }
}
