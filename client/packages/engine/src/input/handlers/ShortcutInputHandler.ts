/**
 * ShortcutInputHandler - Handles shortcut key input from keyboard or gamepad
 *
 * Processes shortcut actions (number keys 1-9, 0 or gamepad buttons).
 * Shortcuts can trigger actions on selected entities/blocks or globally.
 *
 * This handler:
 * - Gets current movement status from PlayerService
 * - Reads shortcut configuration from PlayerInfo.shortcuts['key0'-'key9']
 * - Determines selected entity or block via SelectService
 * - Sends interaction to server with full context including shortcut data
 *
 * Shortcut mapping:
 * - shortcutNr 1-9 → PlayerInfo.shortcuts['key1'-'key9']
 * - shortcutNr 10 (key '0') → PlayerInfo.shortcuts['key0']
 *
 * Usage in InputController:
 * ```typescript
 * // Keyboard number key
 * onKeyDown = (event: KeyboardEvent) => {
 *   if (event.key >= '0' && event.key <= '9') {
 *     const shortcutNr = event.key === '0' ? 10 : parseInt(event.key, 10);
 *     this.shortcutHandler.activate(shortcutNr);
 *   }
 * }
 *
 * // GamePad button
 * onButtonPress = (buttonIndex: number) => {
 *   this.shortcutHandler.activate(buttonIndex + 1); // Map to 1-10
 * }
 * ```
 */

import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';
import type { AppContext } from '../../AppContext';
import { getLogger, ExceptionHandler } from '@nimbus/shared';

const logger = getLogger('ShortcutInputHandler');

export class ShortcutInputHandler extends InputHandler {
  private activeShortcutNr?: number;
  private activeShortcutKey?: string;

  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  /**
   * Handle shortcut activation
   *
   * @param shortcutNr Shortcut number (1-10, where 10 = key '0')
   */
  protected onActivate(shortcutNr: number): void {
    try {
      // Ensure AppContext is available
      if (!this.appContext) {
        logger.warn('AppContext not available for shortcut');
        return;
      }

      const shortcutService = this.appContext.services.shortcut;
      const notificationService = this.appContext.services.notification;

      if (!shortcutService) {
        logger.warn('ShortcutService not available');
        return;
      }

      // Determine shortcut key to use
      let shortcutKey: string;
      if (notificationService) {
        const mappedKey = notificationService.mapKeyboardNumberToShortcut(shortcutNr);
        if (mappedKey) {
          shortcutKey = mappedKey;
        } else {
          shortcutKey = shortcutNr === 10 ? 'key0' : `key${shortcutNr}`;
        }
      } else {
        shortcutKey = shortcutNr === 10 ? 'key0' : `key${shortcutNr}`;
      }

      // Calculate actual shortcut number
      let actualShortcutNr: number;
      if (shortcutKey.startsWith('key')) {
        actualShortcutNr = parseInt(shortcutKey.replace('key', ''), 10);
      } else if (shortcutKey.startsWith('click')) {
        actualShortcutNr = parseInt(shortcutKey.replace('click', ''), 10);
      } else if (shortcutKey.startsWith('slot')) {
        actualShortcutNr = parseInt(shortcutKey.replace('slot', ''), 10);
      } else {
        actualShortcutNr = shortcutNr;
      }

      // Fire shortcut through ShortcutService (centralized)
      shortcutService.fireShortcut(actualShortcutNr, shortcutKey);

      // Trigger highlight animation in UI
      this.playerService.highlightShortcut(shortcutKey);

      // Track active shortcut for continuous updates
      this.activeShortcutNr = shortcutNr;
      this.activeShortcutKey = shortcutKey;
    } catch (error) {
      ExceptionHandler.handle(error, 'ShortcutInputHandler.onActivate', { shortcutNr });
    }
  }

  /**
   * Handle shortcut deactivation.
   * Called when key is released.
   */
  protected onDeactivate(): void {
    if (!this.activeShortcutNr || !this.activeShortcutKey) {
      return;
    }

    try {
      const shortcutService = this.appContext?.services.shortcut;
      if (!shortcutService) {
        return;
      }

      // Get shortcut data before ending
      const shortcut = shortcutService.endShortcut(this.activeShortcutNr);

      // Emit ended event
      if (shortcut) {
        const duration = (Date.now() - shortcut.startTime) / 1000;

        this.playerService.emitShortcutEnded({
          shortcutNr: this.activeShortcutNr,
          shortcutKey: this.activeShortcutKey,
          executorId: shortcut.executorId,
          duration,
        });

        logger.debug('Shortcut ended', {
          shortcutNr: this.activeShortcutNr,
          shortcutKey: this.activeShortcutKey,
          duration,
        });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ShortcutInputHandler.onDeactivate', {
        shortcutNr: this.activeShortcutNr,
      });
    } finally {
      this.activeShortcutNr = undefined;
      this.activeShortcutKey = undefined;
    }
  }

  /**
   * Update handler state.
   * Called each frame while shortcut is active.
   *
   * Note: Position updates are now handled automatically by ShortcutService's
   * 100ms timer loop which uses TargetingService to resolve current targets.
   * This method is kept for potential future per-frame logic.
   */
  protected onUpdate(deltaTime: number, value: number): void {
    // No-op: ShortcutService.sendActiveShortcutUpdatesToServer() handles updates
    // automatically every 100ms using TargetingService for dynamic target resolution
  }

  /**
   * Gets the currently active shortcut number.
   * Used for debugging and status queries.
   *
   * @returns Active shortcut number or undefined
   */
  getActiveShortcutNr(): number | undefined {
    return this.activeShortcutNr;
  }
}
