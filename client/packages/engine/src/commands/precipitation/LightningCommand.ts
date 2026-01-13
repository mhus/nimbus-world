/**
 * LightningCommand - Trigger realistic lightning storm
 *
 * Usage: lightning [groupCount] [toGround]
 * - groupCount: Number of lightning strikes (each at different location, 5-30 flashes per strike)
 * - toGround: true = reaches ground, false = stays in sky
 *
 * Example: lightning 10 true (10 strikes at different locations, each with 5-30 rapid flashes)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('LightningCommand');

export class LightningCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'lightning';
  }

  description(): string {
    return 'Trigger lightning storm (groupCount toGround)';
  }

  async execute(parameters: any[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    // Parse parameters
    if (parameters.length < 2) {
      return 'Usage: lightning [groupCount] [toGround]\nExample: lightning 10 true';
    }

    const groupCount = toNumber(parameters[0]);
    const toGroundStr = parameters[1].toLowerCase();

    // Validate
    if (isNaN(groupCount) || groupCount < 1) {
      return 'Group count must be at least 1';
    }
    if (toGroundStr !== 'true' && toGroundStr !== 'false') {
      return 'toGround must be "true" or "false"';
    }

    const toGround = toGroundStr === 'true';

    try {
      precipitationService.triggerLightning(groupCount, toGround);
      logger.debug('Lightning storm triggered via command', { groupCount, toGround });
      return `Lightning storm triggered: ${groupCount} strikes (each with 5-30 flashes), ${toGround ? 'to ground' : 'in sky'}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to trigger lightning', { error: errorMessage });
      return `Failed to trigger lightning: ${errorMessage}`;
    }
  }
}
