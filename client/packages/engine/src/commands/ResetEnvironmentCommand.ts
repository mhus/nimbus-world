/**
 * ResetEnvironmentCommand - Reset environment to default state
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('ResetEnvironmentCommand');

/**
 * Reset environment command
 *
 * Usage:
 *   resetEnvironment
 *
 * Resets environment to default state by:
 * - Clearing all clouds
 * - (Future: resetting other environment effects)
 *
 * Examples:
 *   resetEnvironment
 */
export class ResetEnvironmentCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'resetEnvironment';
  }

  description(): string {
    return 'Reset environment to default state (clears clouds, etc.)';
  }

  async execute(_parameters: any[]): Promise<any> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    try {
      // Reset the environment
      environmentService.resetEnvironment();

      const message = 'Environment reset to default state';
      logger.debug(message);
      logger.debug(`✓ ${message}`);

      return {
        success: true,
        message,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      logger.error('Failed to reset environment', undefined, error as Error);
      return {
        error: 'Failed to reset environment',
        details: errorMessage,
      };
    }
  }
}
