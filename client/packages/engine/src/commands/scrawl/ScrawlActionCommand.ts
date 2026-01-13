/**
 * ScrawlActionCommand - Execute a complete ScriptActionDefinition
 *
 * Executes a full ScriptActionDefinition with all parameters provided by the caller.
 * Does NOT auto-fill source/target - caller must provide everything.
 * Sends to server for multiplayer synchronization (unless sendToServer: false).
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { ScriptActionDefinition } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlActionCommand');

export class ScrawlActionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlAction';
  }

  description(): string {
    return 'Execute a complete ScriptActionDefinition (synchronized to server)';
  }

  async execute(args: any[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlAction <json|object>');
      logger.debug('Examples:');
      logger.debug('  scrawlAction({script:{id:"test",root:{kind:"Play",effectId:"log"}}})');
      logger.debug('  scrawlAction({script:{...},parameters:{source:{...},target:{...}}})');
      logger.debug('  scrawlAction({sendToServer:false,script:{...}}) // Local only');
      return;
    }

    const scrawlService = appContext.services.scrawl;

    try {
      let action: ScriptActionDefinition;

      // Check if first argument is already an object
      if (typeof args[0] === 'object' && args[0] !== null) {
        action = args[0] as ScriptActionDefinition;
      } else {
        // Parse JSON string
        const input = args.join(' ');
        action = JSON.parse(input);
      }

      logger.debug('Executing scrawl action', {
        hasScript: !!action.script,
        scriptId: action.scriptId || action.script?.id,
        hasParameters: !!action.parameters,
        parameterKeys: action.parameters ? Object.keys(action.parameters) : [],
        sendToServer: action.sendToServer !== false,
      });

      // Execute action as-is (caller provides all data including source/target)
      // If action.parameters contains source/target, they will be used
      // If not, the script must not rely on them
      const executorId = await scrawlService.executeAction(action);

      logger.debug(`Scrawl action started with executor ID: ${executorId}`);
    } catch (error: any) {
      logger.error('Failed to execute scrawl action', { error: error.message });
    }
  }
}
