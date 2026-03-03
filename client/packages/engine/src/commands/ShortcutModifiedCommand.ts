/**
 * ShortcutModifiedCommand - Reload shortcuts from server after external modification.
 *
 * Called by world-player when shortcuts have been modified externally (e.g. via the shortcut panel).
 * Reloads the config from the server and updates playerInfo shortcuts in a single atomic update
 * to avoid race conditions with async UI updates.
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
    const playerService = this.appContext.services.player;

    if (!configService || !playerService) {
      return 'ConfigService or PlayerService not available';
    }

    logger.info('Shortcuts modified externally, reloading from server...');

    // Reload config from server (updates appContext.playerInfo with fresh data)
    const clientType = __EDITOR__ ? 'editor' : 'viewer';
    const config = await configService.reloadConfig(clientType);

    // Get shortcuts from reloaded config
    const playerInfo = config.playerInfo;
    const shortcuts = __EDITOR__
      ? (playerInfo?.editorShortcuts || playerInfo?.shortcuts)
      : playerInfo?.shortcuts;

    const newShortcuts = shortcuts || {};
    const count = Object.keys(newShortcuts).length;

    logger.info('Applying reloaded shortcuts', { count });

    // Single atomic update: replace all shortcuts at once and fire ONE playerInfo:updated event.
    // This avoids the race condition where clearAll + individual setShortcut commands each trigger
    // async updateShortcutDisplay() calls that interleave and cause slot duplication.
    playerService.updatePlayerInfo({ shortcuts: newShortcuts });

    logger.info('Shortcuts reloaded successfully', { count });
    return `Shortcuts reloaded: ${count} shortcuts applied`;
  }
}
