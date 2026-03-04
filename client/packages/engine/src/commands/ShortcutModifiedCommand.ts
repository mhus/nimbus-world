/**
 * ShortcutModifiedCommand - Reload shortcuts from server after external modification.
 *
 * Called by world-player when shortcuts have been modified externally (e.g. via the shortcut panel).
 * Reloads the config from the server and updates playerInfo shortcuts in a single atomic update
 * to avoid race conditions with async UI updates.
 *
 * Hand-type shortcuts (left_hand_1, right_hand_1, left_hand_2, right_hand_2) are resolved
 * by looking up the corresponding wearing slot and replacing itemId/iconPath from the equipped item.
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';
import type { ShortcutDefinition, PlayerBackpack } from '@nimbus/shared';

const logger = getLogger('ShortcutModifiedCommand');

declare const __EDITOR__: boolean;

/** Maps hand shortcut types to their WEARABLE_SLOT keys */
const HAND_TYPE_TO_SLOT: Record<string, string> = {
  left_hand_1: 'LEFT_HAND_1',
  right_hand_1: 'RIGHT_HAND_1',
  left_hand_2: 'LEFT_HAND_2',
  right_hand_2: 'RIGHT_HAND_2',
};

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

    // Resolve hand-type shortcuts from wearing items
    await this.resolveHandShortcuts(newShortcuts, config.playerBackpack);

    const count = Object.keys(newShortcuts).length;
    logger.info('Applying reloaded shortcuts', { count });

    // Single atomic update: replace all shortcuts at once and fire ONE playerInfo:updated event.
    // This avoids the race condition where clearAll + individual setShortcut commands each trigger
    // async updateShortcutDisplay() calls that interleave and cause slot duplication.
    playerService.updatePlayerInfo({ shortcuts: newShortcuts });

    logger.info('Shortcuts reloaded successfully', { count });
    return `Shortcuts reloaded: ${count} shortcuts applied`;
  }

  /**
   * Resolve hand-type shortcuts by looking up the equipped item in the corresponding wearing slot.
   * Updates itemId and iconPath from the wearing item.
   */
  private async resolveHandShortcuts(
    shortcuts: Record<string, ShortcutDefinition>,
    backpack: PlayerBackpack | null | undefined
  ): Promise<void> {
    const wearingItemIds = backpack?.wearingItemIds;
    const itemService = this.appContext.services.item;

    for (const [key, shortcut] of Object.entries(shortcuts)) {
      if (!shortcut || !shortcut.type) continue;

      const slotKey = HAND_TYPE_TO_SLOT[shortcut.type];
      if (!slotKey) continue;

      // Look up equipped item in the wearing slot
      const equippedItemId = wearingItemIds?.[slotKey as any];
      if (!equippedItemId) {
        logger.debug('No item equipped in wearing slot for hand shortcut', { key, type: shortcut.type, slotKey });
        continue;
      }

      // Set the itemId from the wearing slot
      shortcut.itemId = equippedItemId;

      // Try to load item to get the iconPath (texture)
      if (itemService) {
        try {
          const item = await itemService.getItem(equippedItemId);
          if (item?.modifier?.texture) {
            shortcut.iconPath = item.modifier.texture;
          }
          if (item?.name) {
            shortcut.name = item.name;
          }
        } catch (err) {
          logger.warn('Failed to load item for hand shortcut', { key, equippedItemId, error: (err as Error).message });
        }
      }

      logger.debug('Resolved hand shortcut', { key, type: shortcut.type, slotKey, equippedItemId, iconPath: shortcut.iconPath });
    }
  }
}
