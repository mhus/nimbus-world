/**
 * ReloadWorldConfigCommand - Reload WorldInfo from server
 *
 * This command is typically triggered by the server when WorldInfo changes
 * (status, seasonStatus, seasonProgress). It reloads WorldInfo and automatically
 * recalculates all block modifiers if relevant properties changed.
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ReloadWorldConfigCommand');

export class ReloadWorldConfigCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'reloadWorldConfig';
  }

  description(): string {
    return 'Reload WorldInfo from server (auto-recalculates modifiers if status/season changed)';
  }

  async execute(_args: string[]): Promise<void> {
    const configService = this.appContext.services.config;

    if (!configService) {
      logger.error('ConfigService not available');
      return;
    }

    try {
      logger.debug('Reloading WorldInfo from server...');

      // Load WorldInfo (will automatically recalculate modifiers if status/season changed)
      const worldInfo = await configService.loadWorldInfo();

      logger.debug('WorldInfo reloaded successfully', {
        worldId: worldInfo.worldId,
        worldName: worldInfo.name,
        status: worldInfo.status,
        seasonStatus: worldInfo.seasonStatus,
        seasonProgress: worldInfo.seasonProgress,
      });

      // Note: ConfigService.loadWorldInfo() automatically calls
      // ChunkService.recalculateAndRedrawAll() if status/season changed
      // For environment settings changes (sun, horizon, etc.), a client restart is needed
    } catch (error) {
      logger.error('Failed to reload WorldInfo', undefined, error as Error);
    }
  }
}
