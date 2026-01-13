/**
 * CloudPositionCommand - Set cloud position
 *
 * Usage: cloudPosition [id] [x] [z] [y]
 * - id: Cloud identifier
 * - x, z, y: New position (X, Z, height Y)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudPositionCommand');

export class CloudPositionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudPosition';
  }

  description(): string {
    return 'Set cloud position';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length < 4) {
      return 'Usage: cloudPosition [id] [x] [z] [y]\nExample: cloudPosition "cloud1" 100 100 200';
    }

    const id = parameters[0];
    const x = toNumber(parameters[1]);
    const z = toNumber(parameters[2]);
    const y = toNumber(parameters[3]);

    if (isNaN(x) || isNaN(z) || isNaN(y)) {
      return 'Invalid position values. All values must be numbers.';
    }

    try {
      cloudsService.setCloudPosition(id, x, z, y);
      logger.debug('Cloud position changed', { id, x, z, y });
      return `Cloud "${id}" position set to (${x}, ${z}, ${y})`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change cloud position', { error: errorMessage, id, x, z, y });
      return `Failed to change cloud position: ${errorMessage}`;
    }
  }
}
