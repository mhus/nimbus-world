/**
 * SunLightSpecularCommand - Set sun light specular color
 *
 * Usage: sunLightSpecular <r> <g> <b>
 * - r, g, b: RGB color values (0-1)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';
import { Color3 } from '@babylonjs/core';

const logger = getLogger('SunLightSpecularCommand');

export class SunLightSpecularCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLightSpecular';
  }

  description(): string {
    return 'Set sun light specular color (r g b, values 0-1)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Require 3 parameters
    if (parameters.length !== 3) {
      return 'Usage: sunLightSpecular <r> <g> <b> (values 0-1)';
    }

    // Parse parameters
    const r = toNumber(parameters[0]);
    const g = toNumber(parameters[1]);
    const b = toNumber(parameters[2]);

    if (isNaN(r) || isNaN(g) || isNaN(b)) {
      return 'Invalid parameters. All values must be numbers (0-1).';
    }

    if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1) {
      return 'Values out of bounds. All values must be between 0 and 1.';
    }

    // Set sun light specular color
    const color = new Color3(r, g, b);
    environmentService.setSunLightSpecularColor(color);

    logger.debug('Sun light specular color set', { r, g, b });

    return `Sun light specular color set to (${r.toFixed(2)}, ${g.toFixed(2)}, ${b.toFixed(2)})`;
  }
}
