/**
 * SetShortcutCommand - Set shortcut bindings for player actions
 *
 * Usage:
 * - setShortcut list                          // List all shortcuts
 * - setShortcut clear <key>                   // Clear a specific shortcut
 * - setShortcut clearAll                      // Clear all shortcuts
 * - setShortcut <key> <type> [options]        // Set a shortcut
 *
 * Keys:
 * - key0...key9: Number keys (key '0' through '9')
 * - click0, click1, click2: Mouse buttons (left, middle, right)
 * - slot0...slotN: Inventory slots
 *
 * Types:
 * - none: No action (default)
 * - block: Place/use a block
 * - attack: Attack action
 * - use: Use item action
 *
 * Options (JSON object):
 * - itemId: string - Item ID to use
 * - pose: string - Pose to activate (e.g., 'attack', 'use')
 * - wait: number - Wait time before activation (ms)
 * - duration: number - Duration of action (ms)
 *
 * Examples:
 * - setShortcut key1 attack {"itemId": "sword", "pose": "attack", "duration": 500}
 * - setShortcut click0 use {"itemId": "tool", "wait": 100}
 * - setShortcut key5 block {"itemId": "wall"}
 * - setShortcut click2 none
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, ExceptionHandler, ShortcutDefinition, ShortcutActionType } from '@nimbus/shared';

const logger = getLogger('SetShortcutCommand');

export class SetShortcutCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'setShortcut';
  }

  description(): string {
    return 'Set shortcut bindings for player actions';
  }

  async execute(parameters: any[]): Promise<string> {
    try {
      // Get PlayerService
      const playerService = this.appContext.services.player;
      if (!playerService) {
        return 'PlayerService not available';
      }

      const playerEntity = playerService.getPlayerEntity();
      const playerInfo = playerEntity.playerInfo;

      // Initialize shortcuts map if not exists
      if (!playerInfo.shortcuts) {
        playerInfo.shortcuts = {};
      }

      // No parameters: show help
      if (parameters.length === 0) {
        return this.getUsageHelp();
      }

      const command = parameters[0];

      // List all shortcuts
      if (command === 'list') {
        return this.listShortcuts(playerInfo.shortcuts);
      }

      // Clear all shortcuts
      if (command === 'clearAll') {
        playerInfo.shortcuts = {};
        playerService.updatePlayerInfo({ shortcuts: {} });
        logger.debug('All shortcuts cleared');
        return 'All shortcuts cleared';
      }

      // Clear specific shortcut: setShortcut clear <key>
      if (command === 'clear') {
        if (parameters.length < 2) {
          return 'Error: Missing key parameter\nUsage: setShortcut clear <key>';
        }
        const key = parameters[1];
        if (playerInfo.shortcuts[key]) {
          delete playerInfo.shortcuts[key];
          playerService.updatePlayerInfo({ shortcuts: playerInfo.shortcuts });
          logger.debug('Shortcut cleared', { key });
          return `Shortcut cleared: ${key}`;
        } else {
          return `No shortcut found for key: ${key}`;
        }
      }

      // Set shortcut: setShortcut <key> <type> [options]
      if (parameters.length < 2) {
        return 'Error: Missing key and type parameters\nUsage: setShortcut <key> <type> [options]';
      }

      const key = parameters[0];
      const type = parameters[1] as ShortcutActionType;

      // Validate key format
      if (!this.isValidKey(key)) {
        return `Error: Invalid key format: ${key}\nValid formats: key0-key9, click0-click2, slot0-slotN`;
      }

      // Parse options (optional JSON parameter)
      let options: Partial<ShortcutDefinition> = {};
      if (parameters.length >= 3) {
        try {
          if (typeof parameters[2] === 'string') {
            options = JSON.parse(parameters[2]);
          } else if (typeof parameters[2] === 'object') {
            options = parameters[2];
          }
        } catch (parseError) {
          if (parseError instanceof SyntaxError) {
            return `Invalid JSON options: ${parseError.message}\nUsage: setShortcut <key> <type> {"itemId": "...", ...}`;
          }
          throw parseError;
        }
      }

      // Create shortcut definition
      // Note: pose, duration are now stored in ItemData, not in ShortcutDefinition
      // wait is still required in ShortcutDefinition
      const shortcut: ShortcutDefinition = {
        type,
        itemId: options.itemId,
        name: options.name,
        description: options.description,
        wait: options.wait ?? 0, // Default: no wait time
        command: options.command,
        commandArgs: options.commandArgs,
        iconPath: options.iconPath,
      };

      // Set shortcut
      playerInfo.shortcuts[key] = shortcut;
      playerService.updatePlayerInfo({ shortcuts: playerInfo.shortcuts });

      logger.debug('Shortcut set', { key, shortcut });

      return `Shortcut set: ${key}\n${JSON.stringify(shortcut, null, 2)}`;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      ExceptionHandler.handle(error, 'SetShortcutCommand.execute', { parameters });
      return `Error setting shortcut: ${errorMessage}`;
    }
  }

  /**
   * Validate shortcut key format
   */
  private isValidKey(key: string): boolean {
    // key0-key9
    if (/^key[0-9]$/.test(key)) return true;
    // click0-click2 (or higher for additional mouse buttons)
    if (/^click[0-9]+$/.test(key)) return true;
    // slot0-slotN
    if (/^slot[0-9]+$/.test(key)) return true;
    return false;
  }

  /**
   * List all shortcuts
   */
  private listShortcuts(shortcuts: Record<string, ShortcutDefinition>): string {
    const keys = Object.keys(shortcuts);
    if (keys.length === 0) {
      return 'No shortcuts configured';
    }

    let output = 'Configured shortcuts:\n';
    output += '─'.repeat(60) + '\n';

    // Sort keys
    keys.sort();

    for (const key of keys) {
      const shortcut = shortcuts[key];
      output += `\n${key}: ${shortcut.type}`;
      if (shortcut.itemId) output += ` (item: ${shortcut.itemId})`;
      if (shortcut.name) output += ` [${shortcut.name}]`;
      if (shortcut.description) output += ` - ${shortcut.description}`;
      if (shortcut.command) output += ` - ${shortcut.command}`;
      if (shortcut.commandArgs) output += ` - ${JSON.stringify(shortcut.commandArgs)}`;
      if (shortcut.wait > 0) output += `, wait: ${shortcut.wait}ms`;
      if (shortcut.iconPath) output += `, icon: ${shortcut.iconPath}`;
    }

    output += '\n' + '─'.repeat(60);
    return output;
  }

  /**
   * Get usage help text
   */
  private getUsageHelp(): string {
    return `SetShortcut Command - Manage shortcut bindings

Usage:
  setShortcut list                          List all shortcuts
  setShortcut clear <key>                   Clear a specific shortcut
  setShortcut clearAll                      Clear all shortcuts
  setShortcut <key> <type> [options]        Set a shortcut

Keys:
  key0...key9       Number keys (key '0' through '9')
  click0, click1    Mouse buttons (0=left, 1=middle, 2=right)
  slot0...slotN     Inventory slots

Types:
  none              No action (default)
  block             Place/use a block
  attack            Attack action
  use               Use item action

Options (JSON object):
  itemId            Item ID to use (string)
  pose              Pose to activate (string)
  wait              Wait time before activation (ms)
  duration          Duration of action (ms)

Examples:
  setShortcut key1 attack {"itemId": "sword", "pose": "attack", "duration": 500}
  setShortcut click0 use {"itemId": "tool", "wait": 100}
  setShortcut key5 block {"itemId": "wall"}
  setShortcut click2 none
  setShortcut list
  setShortcut clear key1`;
  }
}
