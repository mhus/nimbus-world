/**
 * CloudsAnimationStopCommand - Stop automated cloud animation
 *
 * Usage: cloudsAnimationStop [jobName]
 *
 * If no jobName is provided, all animations are stopped.
 *
 * Example: cloudsAnimationStop myJob
 * Example: cloudsAnimationStop (stops all animations)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CloudsAnimationStopCommand');

export class CloudsAnimationStopCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudsAnimationStop';
  }

  description(): string {
    return 'Stop automated cloud animation (optionally specify jobName, otherwise stops all)';
  }

  async execute(parameters: string[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    try {
      const jobName = parameters.length > 0 ? parameters[0] : undefined;

      if (jobName) {
        // Stop specific job
        const activeJobs = cloudsService.getActiveAnimationJobs();
        if (!activeJobs.includes(jobName)) {
          return `Animation job '${jobName}' not found. Active jobs: ${activeJobs.join(', ') || 'none'}`;
        }

        cloudsService.stopCloudsAnimation(jobName);
        logger.debug('Cloud animation stopped via command', { jobName });
        return `Cloud animation '${jobName}' stopped`;
      } else {
        // Stop all jobs
        const activeJobs = cloudsService.getActiveAnimationJobs();
        if (activeJobs.length === 0) {
          return 'No active cloud animations to stop';
        }

        cloudsService.stopCloudsAnimation();
        logger.debug('All cloud animations stopped via command', { count: activeJobs.length });
        return `All cloud animations stopped (${activeJobs.length} jobs)`;
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to stop cloud animation', { error: errorMessage });
      return `Failed to stop cloud animation: ${errorMessage}`;
    }
  }
}
