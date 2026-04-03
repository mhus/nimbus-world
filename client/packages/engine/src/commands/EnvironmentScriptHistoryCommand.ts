/**
 * EnvironmentScriptHistoryCommand - Show the last environment script executions
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('EnvironmentScriptHistoryCommand');

/**
 * Show environment script execution history
 *
 * Usage:
 *   environmentScriptHistory
 *
 * Returns: Last 10 script executions with timestamps and parameters
 */
export class EnvironmentScriptHistoryCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'environmentScriptHistory';
  }

  description(): string {
    return 'Show the last 10 environment script executions';
  }

  execute(_parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    const history = environmentService.getScriptHistory();

    if (history.length === 0) {
      return { message: 'No environment scripts have been executed yet', history: [] };
    }

    const entries = history.map((entry) => {
      const date = new Date(entry.timestamp);
      const time = date.toLocaleTimeString();
      const paramStr = entry.parameters
        ? Object.entries(entry.parameters)
            .map(([k, v]) => `${k}=${v}`)
            .join(', ')
        : '';
      return {
        name: entry.name,
        scriptId: entry.scriptId,
        time,
        timestamp: entry.timestamp,
        parameters: paramStr || undefined,
      };
    });

    return {
      message: `Last ${entries.length} environment script executions`,
      history: entries,
    };
  }
}
