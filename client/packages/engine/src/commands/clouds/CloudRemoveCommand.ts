/**
 * CloudRemoveCommand - Remove a cloud from the scene
 *
 * Usage: cloudRemove [id]
 * - id: Cloud identifier to remove
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CloudRemoveCommand');

export class CloudRemoveCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudRemove';
  }

  description(): string {
    return 'Remove a cloud from the scene';
  }

  async execute(parameters: string[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length === 0) {
      return 'Usage: cloudRemove [id]\nExample: cloudRemove "cloud1"';
    }

    const id = parameters[0];

    try {
      cloudsService.removeCloud(id);
      logger.debug('Cloud removed', { id });
      return `Cloud "${id}" removed`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to remove cloud', { error: errorMessage, id });
      return `Failed to remove cloud: ${errorMessage}`;
    }
  }
}
