/**
 * SkyBoxSizeCommand - Set skybox size
 *
 * Usage: skyBoxSize [size]
 * - Without parameters: Shows usage information
 * - With size: Sets skybox mesh size
 *   - Example: skyBoxSize 2000 (default size)
 *   - Example: skyBoxSize 3000 (larger skybox)
 *   - Example: skyBoxSize 1000 (smaller skybox)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SkyBoxSizeCommand');

export class SkyBoxSizeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxSize';
  }

  description(): string {
    return 'Set skybox size (number)';
  }

  async execute(parameters: any[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: skyBoxSize [size]\nExample: skyBoxSize 2000';
    }

    // Parse size parameter
    const size = toNumber(parameters[0]);

    if (isNaN(size)) {
      return 'Invalid size. Provide a valid number.';
    }

    if (size <= 0) {
      return 'Size must be greater than 0.';
    }

    // Set skybox size
    skyBoxService.setSize(size);

    logger.debug('SkyBox size set via command', { size });

    return `SkyBox size set to: ${size}`;
  }
}
