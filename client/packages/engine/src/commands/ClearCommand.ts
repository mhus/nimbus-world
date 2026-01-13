/**
 * ClearCommand - Clears the browser console
 */

import { CommandHandler } from './CommandHandler';

/**
 * Clear command - Clears the browser console
 */
export class ClearCommand extends CommandHandler {
  name(): string {
    return 'clear';
  }

  description(): string {
    return 'Clears the browser console';
  }

  execute(parameters: any[]): any {
    console.clear();
    return 'Console cleared';
  }
}
