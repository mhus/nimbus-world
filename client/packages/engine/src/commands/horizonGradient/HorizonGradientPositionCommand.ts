/**
 * HorizonGradientPositionCommand - Set horizon gradient box Y position
 *
 * Usage: horizonGradientPosition <y>
 * - y: Y position of bottom edge
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('HorizonGradientPositionCommand');

export class HorizonGradientPositionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientPosition';
  }

  description(): string {
    return 'Set horizon gradient Y position (y)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length === 0) {
      return 'Usage: horizonGradientPosition <y>\nExample: horizonGradientPosition -50';
    }

    const y = toNumber(parameters[0]);

    if (isNaN(y)) {
      return 'Invalid Y position. Must be a number.';
    }

    service.setYPosition(y);
    logger.debug('Horizon gradient Y position set via command', { y });
    return `Horizon gradient Y position set to ${y}`;
  }
}
