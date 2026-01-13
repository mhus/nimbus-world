/**
 * StatusEffectCommand - Manage player status effects
 *
 * Usage:
 * - /effect add <itemId> [duration] - Add status effect
 * - /effect remove <effectId> - Remove status effect by ID
 * - /effect clear - Remove all status effects
 * - /effect list - List all active status effects
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('StatusEffectCommand');

export class StatusEffectCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'effect';
  }

  description(): string {
    return 'Manage player status effects';
  }

  async execute(parameters: any[]): Promise<any> {
    if (parameters.length === 0) {
      return this.getUsageHelp();
    }

    const subcommand = String(parameters[0]).toLowerCase();

    switch (subcommand) {
      case 'add':
        return this.handleAdd(parameters.slice(1));
      case 'remove':
        return this.handleRemove(parameters.slice(1));
      case 'clear':
        return this.handleClear();
      case 'list':
        return this.handleList();
      default:
        return `Unknown subcommand: ${subcommand}\nUse /effect for help`;
    }
  }

  /**
   * Handle /effect add <itemId> [duration]
   */
  private async handleAdd(args: any[]): Promise<string> {
    if (args.length === 0) {
      return 'Usage: /effect add <itemId> [duration]\n\nExample: /effect add potion_heal 5000';
    }

    const itemId = String(args[0]);
    const duration = args[1] ? parseInt(String(args[1]), 10) : undefined;

    if (duration !== undefined && (isNaN(duration) || duration <= 0)) {
      return 'Error: duration must be a positive number (milliseconds)';
    }

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    try {
      const effectId = playerService.addStatusEffect(itemId, duration);

      let message = `Status effect added: ${effectId}\n`;
      message += `Item ID: ${itemId}\n`;
      if (duration) {
        message += `Duration: ${duration}ms (${(duration / 1000).toFixed(1)}s)`;
      } else {
        message += `Duration: Permanent (until manually removed)`;
      }

      return message;
    } catch (error) {
      logger.error('Failed to add status effect', { itemId, duration }, error as Error);
      return `Error: ${(error as Error).message}`;
    }
  }

  /**
   * Handle /effect remove <effectId>
   */
  private handleRemove(args: any[]): string {
    if (args.length === 0) {
      return 'Usage: /effect remove <effectId>\n\nUse /effect list to see effect IDs';
    }

    const effectId = String(args[0]);

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const removed = playerService.removeStatusEffect(effectId);
    if (removed) {
      return `Status effect removed: ${effectId}`;
    } else {
      return `Status effect not found: ${effectId}`;
    }
  }

  /**
   * Handle /effect clear
   */
  private handleClear(): string {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    playerService.clearAllStatusEffects();
    return 'All status effects cleared';
  }

  /**
   * Handle /effect list
   */
  private handleList(): string {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const effects = playerService.getStatusEffects();

    if (effects.length === 0) {
      return 'No active status effects';
    }

    let output = 'Active status effects:\n';
    output += '─'.repeat(60) + '\n';

    for (const effect of effects) {
      output += `\n${effect.id}`;
      output += `\n  Item: ${effect.itemId}`;
      output += `\n  Applied: ${new Date(effect.appliedAt).toLocaleTimeString()}`;

      if (effect.duration && effect.expiresAt) {
        const remaining = Math.max(0, effect.expiresAt - Date.now());
        output += `\n  Duration: ${effect.duration}ms (${(remaining / 1000).toFixed(1)}s remaining)`;
      } else {
        output += `\n  Duration: Permanent`;
      }
    }

    output += '\n' + '─'.repeat(60);
    return output;
  }

  /**
   * Get usage help
   */
  private getUsageHelp(): string {
    return `StatusEffect Command - Manage player status effects

Usage:
  /effect add <itemId> [duration]  - Add status effect
  /effect remove <effectId>        - Remove status effect
  /effect clear                    - Remove all status effects
  /effect list                     - List active status effects

Examples:
  /effect add potion_heal 5000     - Add heal effect for 5 seconds
  /effect add buff_strength         - Add permanent strength buff
  /effect remove effect_123...      - Remove specific effect
  /effect list                      - Show all active effects`;
  }
}
