/**
 * ScrawlStopCommand - Stop a running scrawl script
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlStopCommand');

export class ScrawlStopCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlStop';
  }

  description(): string {
    return 'Stop a running scrawl script by executor ID (scrawlStop <executorId|all>)';
  }

  async execute(args: string[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlStop <executorId|all>');
      logger.debug('Examples:');
      logger.debug('  scrawlStop executor_0');
      logger.debug('  scrawlStop all');
      return;
    }

    const scrawlService = appContext.services.scrawl;
    const executorId = args[0];

    if (executorId === 'all') {
      // Stop all running executors
      const runningIds = scrawlService.getRunningExecutorIds();
      logger.debug(`Stopping ${runningIds.length} executor(s)...`);
      scrawlService.cancelAllExecutors();
      logger.debug('All executors stopped');
    } else {
      // Stop specific executor
      const success = scrawlService.cancelExecutor(executorId);
      if (success) {
        logger.debug(`Executor ${executorId} stopped`);
      } else {
        logger.error(`Executor ${executorId} not found`);
      }
    }
  }
}
