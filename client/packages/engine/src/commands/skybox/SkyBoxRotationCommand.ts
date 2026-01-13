/**
 * SkyBoxRotationCommand - Set skybox rotation
 *
 * Usage: skyBoxRotation [degrees]
 * - Without parameters: Shows usage information
 * - With degrees: Sets skybox rotation around Y axis
 *   - Example: skyBoxRotation 0 (no rotation)
 *   - Example: skyBoxRotation 45 (45 degrees)
 *   - Example: skyBoxRotation 90 (90 degrees)
 *   - Example: skyBoxRotation 180 (180 degrees)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SkyBoxRotationCommand');

export class SkyBoxRotationCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxRotation';
  }

  description(): string {
    return 'Set skybox rotation in degrees (Y axis)';
  }

  async execute(parameters: any[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: skyBoxRotation [degrees]\nExample: skyBoxRotation 45';
    }

    // Parse rotation parameter
    const degrees = toNumber(parameters[0]);

    if (isNaN(degrees)) {
      return 'Invalid rotation. Provide a valid number in degrees.';
    }

    // Set skybox rotation
    skyBoxService.setRotation(degrees);

    logger.debug('SkyBox rotation set via command', { degrees });

    return `SkyBox rotation set to: ${degrees}°`;
  }
}
