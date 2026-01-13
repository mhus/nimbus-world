/**
 * SunLensFlareColorCommand - Set sun lens flare color
 *
 * Usage: sunLensFlareColor [r] [g] [b]
 * - Without parameters: Shows usage information
 * - With 3 parameters: Sets lens flare color (RGB values 0-1)
 *   - Example: sunLensFlareColor 1 0.9 0.7
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunLensFlareColorCommand');

export class SunLensFlareColorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLensFlareColor';
  }

  description(): string {
    return 'Set sun lens flare color (r g b, values 0-1)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: sunLensFlareColor [r] [g] [b] (values 0-1)';
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

    // Set lens flare color
    sunService.setSunLensFlareColor(r, g, b);

    logger.debug('Lens flare color set', { r, g, b });

    return `Lens flare color set to RGB(${r.toFixed(2)}, ${g.toFixed(2)}, ${b.toFixed(2)})`;
  }
}
