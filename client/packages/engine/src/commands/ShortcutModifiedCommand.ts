/**
 * ShortcutModifiedCommand - Reload shortcuts from server after external modification.
 *
 * Called by world-player when shortcuts have been modified externally (e.g. via the shortcut panel).
 * Reloads the config from the server, clears all current shortcuts, and re-applies them.
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShortcutModifiedCommand');

declare const __EDITOR__: boolean;

export class ShortcutModifiedCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'ShortcutModified';
  }

  description(): string {
    return 'Reload shortcuts from server after external modification';
  }

  async execute(_parameters: any[]): Promise<string> {
    const configService = this.appContext.services.config;
    const commandService = this.appContext.services.command;

    if (!configService || !commandService) {
      return 'ConfigService or CommandService not available';
    }

    logger.info('Shortcuts modified externally, reloading from server...');

    // Reload config from server
    const clientType = __EDITOR__ ? 'editor' : 'viewer';
    const config = await configService.reloadConfig(clientType);

    // Clear all current shortcuts
    await commandService.executeCommand('setShortcut', ['clearAll']);

    // Re-apply shortcuts from reloaded config
    const playerInfo = config.playerInfo;
    const shortcuts = __EDITOR__
      ? (playerInfo?.editorShortcuts || playerInfo?.shortcuts)
      : playerInfo?.shortcuts;

    if (!shortcuts) {
      logger.info('No shortcuts found after reload');
      return 'Shortcuts reloaded (none defined)';
    }

    const entries = Object.entries(shortcuts);
    logger.info('Re-applying shortcuts', { count: entries.length });

    await Promise.all(
      entries.map(async ([key, shortcut]) => {
        if (!shortcut) return;
        try {
          await commandService.executeCommand('setShortcut', [
            key,
            shortcut.type,
            {
              itemId: shortcut.itemId,
              wait: shortcut.wait,
              name: shortcut.name,
              description: shortcut.description,
              command: shortcut.command,
              commandArgs: shortcut.commandArgs,
              iconPath: shortcut.iconPath,
            },
          ]);
        } catch (error) {
          logger.error('Failed to re-apply shortcut', { key }, error as Error);
        }
      })
    );

    logger.info('Shortcuts reloaded successfully', { count: entries.length });
    return `Shortcuts reloaded: ${entries.length} shortcuts applied`;
  }
}
