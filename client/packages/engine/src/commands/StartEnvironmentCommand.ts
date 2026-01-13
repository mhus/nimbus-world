/**
 * StartEnvironmentCommand - Start the environment system
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('StartEnvironmentCommand');

/**
 * Start environment command
 *
 * Usage:
 *   startEnvironment
 *
 * Examples:
 *   startEnvironment()
 *
 * Note: Initializes the environment system including world time and season calculation.
 */
export class StartEnvironmentCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'startEnvironment';
  }

  description(): string {
    return 'Start the environment system';
  }

  async execute(parameters: any[]): Promise<any> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Start the environment
    environmentService.startEnvironment();

    const message = 'Environment system started';
    logger.info(message);
    return {
      started: true,
      message,
    };
  }
}
