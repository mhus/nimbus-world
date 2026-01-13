/**
 * HorizonGradientHeightCommand - Set horizon gradient box height
 *
 * Usage: horizonGradientHeight <height>
 * - height: Height of the vertical sides (positive number)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('HorizonGradientHeightCommand');

export class HorizonGradientHeightCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientHeight';
  }

  description(): string {
    return 'Set horizon gradient height (height)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length === 0) {
      return 'Usage: horizonGradientHeight <height>\nExample: horizonGradientHeight 150';
    }

    const height = toNumber(parameters[0]);

    if (isNaN(height) || height <= 0) {
      return 'Invalid height. Must be a positive number.';
    }

    service.setHeight(height);
    logger.debug('Horizon gradient height set via command', { height });
    return `Horizon gradient height set to ${height}`;
  }
}
