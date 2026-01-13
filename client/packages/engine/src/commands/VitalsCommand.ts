/**
 * VitalsCommand - Manage player vitals
 *
 * Usage:
 * - /vitals add <type> <current> <max> <color> <name> <order> [regenRate] [degenRate] - Add vital
 * - /vitals update <type> <current> - Update vital value
 * - /vitals extend <type> <amount> [duration] - Add temporary extension
 * - /vitals remove <type> - Remove vital
 * - /vitals clear - Remove all vitals
 * - /vitals list - List all vitals
 * - /vitals show - Toggle vitals display
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import type { VitalsData } from '@nimbus/shared';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('VitalsCommand');

export class VitalsCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'vitals';
  }

  description(): string {
    return 'Manage player vitals (health, hunger, etc.)';
  }

  async execute(parameters: any[]): Promise<any> {
    if (parameters.length === 0) {
      return this.getUsageHelp();
    }

    const subcommand = String(parameters[0]).toLowerCase();

    switch (subcommand) {
      case 'add':
        return this.handleAdd(parameters.slice(1));
      case 'update':
        return this.handleUpdate(parameters.slice(1));
      case 'extend':
        return this.handleExtend(parameters.slice(1));
      case 'remove':
        return this.handleRemove(parameters.slice(1));
      case 'clear':
        return this.handleClear();
      case 'list':
        return this.handleList();
      case 'show':
        return this.handleShow();
      default:
        return `Unknown subcommand: ${subcommand}\nUse /vitals for help`;
    }
  }

  /**
   * Handle /vitals add
   */
  private handleAdd(args: any[]): string {
    if (args.length < 6) {
      return 'Usage: /vitals add <type> <current> <max> <color> <name> <order> [regenRate] [degenRate]\n\nExample: /vitals add health 100 100 #ff0000 Health 0 0.5 0';
    }

    const type = String(args[0]);
    const current = parseFloat(String(args[1]));
    const max = parseFloat(String(args[2]));
    const color = String(args[3]);
    const name = String(args[4]);
    const order = parseInt(String(args[5]), 10);
    const regenRate = args[6] ? parseFloat(String(args[6])) : 0;
    const degenRate = args[7] ? parseFloat(String(args[7])) : 0;

    if (isNaN(current) || isNaN(max) || isNaN(order) || isNaN(regenRate) || isNaN(degenRate)) {
      return 'Error: current, max, order, regenRate, and degenRate must be numbers';
    }

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const vital: VitalsData = {
      type,
      current,
      max,
      extended: 0,
      regenRate,
      degenRate,
      color,
      name,
      order,
    };

    playerService.setVital(vital);
    return `Vital added: ${type}\n${JSON.stringify(vital, null, 2)}`;
  }

  /**
   * Handle /vitals update <type> <current>
   */
  private handleUpdate(args: any[]): string {
    if (args.length < 2) {
      return 'Usage: /vitals update <type> <current>\n\nExample: /vitals update health 50';
    }

    const type = String(args[0]);
    const current = parseFloat(String(args[1]));

    if (isNaN(current)) {
      return 'Error: current must be a number';
    }

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const vital = playerService.getVital(type);
    if (!vital) {
      return `Error: Vital not found: ${type}`;
    }

    playerService.updateVitalValue(type, current);
    return `Vital updated: ${type} = ${Math.floor(current)}`;
  }

  /**
   * Handle /vitals extend <type> <amount> [duration]
   */
  private handleExtend(args: any[]): string {
    if (args.length < 2) {
      return 'Usage: /vitals extend <type> <amount> [duration]\n\nExample: /vitals extend health 20 10000';
    }

    const type = String(args[0]);
    const amount = parseFloat(String(args[1]));
    const duration = args[2] ? parseInt(String(args[2]), 10) : undefined;

    if (isNaN(amount)) {
      return 'Error: amount must be a number';
    }

    if (duration !== undefined && isNaN(duration)) {
      return 'Error: duration must be a number (milliseconds)';
    }

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const vital = playerService.getVital(type);
    if (!vital) {
      return `Error: Vital not found: ${type}`;
    }

    vital.extended = amount;
    vital.extendExpiry = duration ? Date.now() + duration : undefined;
    playerService.setVital(vital);

    let message = `Vital extended: ${type} +${amount}`;
    if (duration) {
      message += ` for ${duration}ms (${(duration / 1000).toFixed(1)}s)`;
    } else {
      message += ` (permanent)`;
    }
    return message;
  }

  /**
   * Handle /vitals remove <type>
   */
  private handleRemove(args: any[]): string {
    if (args.length === 0) {
      return 'Usage: /vitals remove <type>';
    }

    const type = String(args[0]);

    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const removed = playerService.removeVital(type);
    if (removed) {
      return `Vital removed: ${type}`;
    } else {
      return `Vital not found: ${type}`;
    }
  }

  /**
   * Handle /vitals clear
   */
  private handleClear(): string {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    playerService.clearAllVitals();
    return 'All vitals cleared';
  }

  /**
   * Handle /vitals list
   */
  private handleList(): string {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const vitals = playerService.getVitals();

    if (vitals.length === 0) {
      return 'No vitals configured';
    }

    // Sort by order
    vitals.sort((a, b) => a.order - b.order);

    let output = 'Player vitals:\n';
    output += '─'.repeat(60) + '\n';

    for (const vital of vitals) {
      const maxValue = vital.max + (vital.extended || 0);
      const percent = maxValue > 0 ? ((vital.current / maxValue) * 100).toFixed(1) : '0';

      output += `\n${vital.name} (${vital.type})`;
      output += `\n  Value: ${Math.floor(vital.current)}/${vital.max}`;
      if (vital.extended) output += ` (+${vital.extended})`;
      output += ` [${percent}%]`;
      output += `\n  Color: ${vital.color}`;
      if (vital.regenRate > 0) output += `  Regen: +${vital.regenRate}/s`;
      if (vital.degenRate > 0) output += `  Degen: -${vital.degenRate}/s`;
      if (vital.extendExpiry) {
        const remaining = Math.max(0, vital.extendExpiry - Date.now());
        output += `\n  Extension expires in: ${(remaining / 1000).toFixed(1)}s`;
      }
    }

    output += '\n' + '─'.repeat(60);
    return output;
  }

  /**
   * Handle /vitals show (toggle vitals display)
   */
  private handleShow(): string {
    const notificationService = this.appContext.services.notification;
    if (!notificationService) {
      return 'Error: NotificationService not available';
    }

    notificationService.toggleShowVitals();
    return 'Vitals display toggled';
  }

  /**
   * Get usage help
   */
  private getUsageHelp(): string {
    return `Vitals Command - Manage player vitals

Usage:
  /vitals add <type> <current> <max> <color> <name> <order> [regen] [degen]
  /vitals update <type> <current>
  /vitals extend <type> <amount> [duration]
  /vitals remove <type>
  /vitals clear
  /vitals list
  /vitals show

Examples:
  /vitals add health 100 100 #ff0000 Health 0 0.5 0
  /vitals add hunger 100 100 #ff8800 Hunger 1 0 0.1
  /vitals update health 50
  /vitals extend health 20 10000
  /vitals show`;
  }
}
