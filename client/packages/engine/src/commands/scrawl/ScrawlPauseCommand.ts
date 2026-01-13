/**
 * ScrawlPauseCommand - Pause a running scrawl script
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlPauseCommand');

export class ScrawlPauseCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlPause';
  }

  description(): string {
    return 'Pause a running scrawl script by executor ID (scrawlPause <executorId>)';
  }

  async execute(args: string[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlPause <executorId>');
      logger.debug('Example: scrawlPause executor_0');
      return;
    }

    const scrawlService = appContext.services.scrawl;
    const executorId = args[0];

    const success = scrawlService.pauseExecutor(executorId);
    if (success) {
      logger.debug(`Executor ${executorId} paused`);
    } else {
      logger.error(`Executor ${executorId} not found`);
    }
  }
}
