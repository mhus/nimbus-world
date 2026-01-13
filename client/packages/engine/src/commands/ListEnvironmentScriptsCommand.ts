/**
 * ListEnvironmentScriptsCommand - List all registered environment scripts
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('ListEnvironmentScriptsCommand');

/**
 * List environment scripts command
 *
 * Usage:
 *   listEnvironmentScripts
 *
 * Returns: List of all registered environment scripts with their names and running status
 *
 * Examples:
 *   listEnvironmentScripts
 */
export class ListEnvironmentScriptsCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'listEnvironmentScripts';
  }

  description(): string {
    return 'List all registered environment scripts';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Get all scripts
    const allScripts = environmentService.getAllEnvironmentScripts();
    const runningScripts = environmentService.getRunningEnvironmentScripts();

    // Create a set of running script names
    const runningNames = new Set(runningScripts.map((r) => r.name));

    // Build result list
    const scriptList = allScripts.map((script) => {
      const isRunning = runningNames.has(script.name);
      return {
        name: script.name,
        scriptName: script.script,
        running: isRunning,
      };
    });

    // Sort by name
    scriptList.sort((a, b) => a.name.localeCompare(b.name));

    // Log summary
    logger.debug(`Total environment scripts: ${scriptList.length}`);
    logger.debug(`Running scripts: ${runningScripts.length}`);

    // Log each script
    if (scriptList.length === 0) {
      logger.debug('No environment scripts registered');
    } else {
      logger.debug('Environment scripts:');
      for (const script of scriptList) {
        const status = script.running ? '[RUNNING]' : '[STOPPED]';
        logger.debug(`  ${status} ${script.name} -> ${script.scriptName}`);
      }
    }

    return {
      total: scriptList.length,
      running: runningScripts.length,
      scripts: scriptList,
    };
  }
}
