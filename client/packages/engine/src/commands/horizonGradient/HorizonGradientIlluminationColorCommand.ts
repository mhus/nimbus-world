/**
 * HorizonGradientIlluminationColorCommand - Set horizon gradient illumination color
 *
 * Usage: horizonGradientIlluminationColor <r> <g> <b>
 * - r, g, b: RGB color values (0.0 to 1.0)
 * - Use "off" or "none" to disable illumination
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';
import { Color3 } from '@babylonjs/core';

const logger = getLogger('HorizonGradientIlluminationColorCommand');

export class HorizonGradientIlluminationColorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientIlluminationColor';
  }

  description(): string {
    return 'Set horizon gradient illumination color (r g b) or "off" to disable';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length === 0) {
      const color = service.getIlluminationColor();
      if (!color) {
        return 'Horizon gradient illumination is currently disabled';
      }
      return `Horizon gradient illumination color: RGB(${color.r.toFixed(2)}, ${color.g.toFixed(2)}, ${color.b.toFixed(2)})`;
    }

    // Check for "off" or "none" to disable illumination
    const firstParam = String(parameters[0]).toLowerCase();
    if (firstParam === 'off' || firstParam === 'none') {
      service.setIlluminationColor(undefined);
      logger.info('Horizon gradient illumination disabled via command');
      return 'Horizon gradient illumination disabled';
    }

    if (parameters.length < 3) {
      return 'Usage: horizonGradientIlluminationColor <r> <g> <b>\nExample: horizonGradientIlluminationColor 1.0 0.8 0.6\nOr: horizonGradientIlluminationColor off';
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
    service.setIlluminationColor(color);
    logger.info('Horizon gradient illumination color set via command', { r, g, b });
    return `Horizon gradient illumination color set to RGB(${r}, ${g}, ${b})`;
  }
}
