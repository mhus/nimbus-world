/**
 * SunLightDirectionCommand - Set sun light direction
 *
 * Usage: sunLightDirection <x> <y> <z>
 * - x, y, z: Direction vector (will be normalized)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunLightDirectionCommand');

export class SunLightDirectionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLightDirection';
  }

  description(): string {
    return 'Set sun light direction (x y z, will be normalized)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current direction if no parameters
    if (parameters.length === 0) {
      const direction = environmentService.getSunLightDirection();
      return `Current sun light direction: (${direction.x.toFixed(2)}, ${direction.y.toFixed(2)}, ${direction.z.toFixed(2)})`;
    }

    // Require 3 parameters
    if (parameters.length !== 3) {
      return 'Usage: sunLightDirection <x> <y> <z>';
    }

    // Parse parameters
    const x = toNumber(parameters[0]);
    const y = toNumber(parameters[1]);
    const z = toNumber(parameters[2]);

    if (isNaN(x) || isNaN(y) || isNaN(z)) {
      return 'Invalid parameters. All values must be numbers.';
    }

    // Check if zero vector
    if (x === 0 && y === 0 && z === 0) {
      return 'Invalid direction. Cannot use zero vector.';
    }

    // Set sun light direction
    environmentService.setSunLightDirection(x, y, z);

    const direction = environmentService.getSunLightDirection();
    logger.debug('Sun light direction set', { x: direction.x, y: direction.y, z: direction.z });

    return `Sun light direction set to (${direction.x.toFixed(2)}, ${direction.y.toFixed(2)}, ${direction.z.toFixed(2)})`;
  }
}
