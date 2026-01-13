/**
 * ScrawlResumeCommand - Resume a paused scrawl script
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlResumeCommand');

export class ScrawlResumeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlResume';
  }

  description(): string {
    return 'Resume a paused scrawl script by executor ID (scrawlResume <executorId>)';
  }

  async execute(args: string[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlResume <executorId>');
      logger.debug('Example: scrawlResume executor_0');
      return;
    }

    const scrawlService = appContext.services.scrawl;
    const executorId = args[0];

    const success = scrawlService.resumeExecutor(executorId);
    if (success) {
      logger.debug(`Executor ${executorId} resumed`);
    } else {
      logger.error(`Executor ${executorId} not found`);
    }
  }
}
