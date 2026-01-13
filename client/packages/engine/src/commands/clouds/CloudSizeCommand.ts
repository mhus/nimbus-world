/**
 * CloudSizeCommand - Set cloud size
 *
 * Usage: cloudSize [id] [width] [height]
 * - id: Cloud identifier
 * - width, height: Cloud dimensions
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudSizeCommand');

export class CloudSizeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudSize';
  }

  description(): string {
    return 'Set cloud size';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length < 3) {
      return 'Usage: cloudSize [id] [width] [height]\nExample: cloudSize "cloud1" 100 60';
    }

    const id = parameters[0];
    const width = toNumber(parameters[1]);
    const height = toNumber(parameters[2]);

    if (isNaN(width) || isNaN(height)) {
      return 'Invalid size values. Both width and height must be numbers.';
    }

    if (width <= 0 || height <= 0) {
      return 'Size values must be positive.';
    }

    try {
      cloudsService.setCloudSize(id, width, height);
      logger.debug('Cloud size changed', { id, width, height });
      return `Cloud "${id}" size set to ${width}x${height}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change cloud size', { error: errorMessage, id, width, height });
      return `Failed to change cloud size: ${errorMessage}`;
    }
  }
}
