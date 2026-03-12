/**
 * SettingsModifiedCommand - Reload settings from server after external modification.
 *
 * Called by world-player when user settings have been modified externally (e.g. via the settings panel).
 * Reloads settings from the server which triggers the settings:changed event,
 * causing all subscribed services (AudioService, EngineService, etc.) to update.
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SettingsModifiedCommand');

declare const __EDITOR__: boolean;

export class SettingsModifiedCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'SettingsModified';
  }

  description(): string {
    return 'Reload settings from server after external modification';
  }

  async execute(_parameters: any[]): Promise<string> {
    const configService = this.appContext.services.config;

    if (!configService) {
      return 'ConfigService not available';
    }

    logger.info('Settings modified externally, reloading from server...');

    const clientType = __EDITOR__ ? 'editor' : 'viewer';
    await configService.loadSettings(clientType);

    logger.info('Settings reloaded successfully');
    return 'Settings reloaded and applied';
  }
}
