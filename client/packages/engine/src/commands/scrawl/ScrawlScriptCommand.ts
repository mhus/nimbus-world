/**
 * ScrawlScriptCommand - Execute a script by name with parameters
 *
 * Usage: scrawlScript('scriptName', {key: value, ...})
 *
 * Loads and executes a script from the script library with the provided parameters.
 * The parameters are merged into the execution context's vars.
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlScriptCommand');

export class ScrawlScriptCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlScript';
  }

  description(): string {
    return 'Execute a script by name with parameters: scrawlScript("scriptName", {key: value, ...})';
  }

  async execute(args: any[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlScript("scriptName", {parameters})');
      logger.debug('Examples:');
      logger.debug('  scrawlScript("fireball", {damage: 50, radius: 10})');
      logger.debug('  scrawlScript("heal", {amount: 100})');
      logger.debug('  scrawlScript("test_script")');
      return;
    }

    const scrawlService = appContext.services.scrawl;

    try {
      // First argument is the script name
      const scriptName = args[0];

      if (typeof scriptName !== 'string') {
        logger.error('First argument must be a string (script name)');
        return;
      }

      // Second argument (optional) is the parameters object
      let parameters: Record<string, any> = {};

      if (args.length > 1) {
        if (typeof args[1] === 'object' && args[1] !== null) {
          parameters = args[1];
        } else {
          logger.error('Second argument must be an object (parameters)');
          return;
        }
      }

      logger.debug('Executing script', {
        scriptName,
        parameters: Object.keys(parameters),
      });

      // Execute the script with parameters
      // Parameters are passed via the context.vars
      const executorId = await scrawlService.executeScript(scriptName, {
        vars: parameters,
      });

      logger.debug(`Script execution started with executor ID: ${executorId}`, {
        scriptName,
      });
    } catch (error: any) {
      logger.error('Failed to execute script', { error: error.message });
    }
  }
}
