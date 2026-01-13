/**
 * HorizonGradientAlphaCommand - Set horizon gradient transparency
 *
 * Usage: horizonGradientAlpha <alpha>
 * - alpha: Transparency value (0.0 = fully transparent, 1.0 = opaque)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('HorizonGradientAlphaCommand');

export class HorizonGradientAlphaCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientAlpha';
  }

  description(): string {
    return 'Set horizon gradient transparency (alpha)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length === 0) {
      return 'Usage: horizonGradientAlpha <alpha>\nExample: horizonGradientAlpha 0.7';
    }

    const alpha = toNumber(parameters[0]);

    if (isNaN(alpha) || alpha < 0 || alpha > 1) {
      return 'Invalid alpha. Must be a number between 0.0 and 1.0.';
    }

    service.setAlpha(alpha);
    logger.debug('Horizon gradient alpha set via command', { alpha });
    return `Horizon gradient transparency set to ${alpha}`;
  }
}
