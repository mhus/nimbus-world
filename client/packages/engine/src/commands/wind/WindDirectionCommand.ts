/**
 * WindDirectionCommand - Set global wind direction
 *
 * Usage: windDirection [x] [z]
 * - Without parameters: Shows current wind direction
 * - With parameters: Sets wind direction (x, z) as a 2D vector
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WindDirectionCommand');

export class WindDirectionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'windDirection';
  }

  description(): string {
    return 'Set wind direction (x, z)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current wind direction if no parameters
    if (parameters.length === 0) {
      const dir = environmentService.getWindDirection();
      return `Current wind direction: (${dir.x.toFixed(2)}, ${dir.z.toFixed(2)})`;
    }

    // Validate parameters
    if (parameters.length < 2) {
      return 'Missing parameters. Usage: windDirection <x> <z>';
    }

    const x = toNumber(parameters[0]);
    const z = toNumber(parameters[1]);

    if (isNaN(x) || isNaN(z)) {
      return 'Invalid parameters. Both x and z must be numbers.';
    }

    // Set wind direction
    environmentService.setWindDirection(x, z);

    const dir = environmentService.getWindDirection();
    logger.debug('Wind direction set', { x: dir.x, z: dir.z });

    return `Wind direction set to (${dir.x.toFixed(2)}, ${dir.z.toFixed(2)})`;
  }
}
