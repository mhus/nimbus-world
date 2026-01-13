/**
 * WorldTimeInfoCommand - Show World Time information
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('WorldTimeInfoCommand');

/**
 * WorldTimeInfo command - Show World Time information
 * Usage: worldTimeInfo
 */
export class WorldTimeInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'worldTimeInfo';
  }

  description(): string {
    return 'Show World Time information and status';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;
    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    const isRunning = environmentService.isWorldTimeRunning();
    const config = environmentService.getWorldTimeConfig();

    logger.debug('=== World Time Info ===');
    logger.debug(`  Status         : ${isRunning ? 'Running' : 'Stopped'}`);

    if (isRunning) {
      const currentTime = environmentService.getWorldTimeCurrentAsString();
      const currentMinute = environmentService.getWorldTimeCurrent();
      const daySection = environmentService.getWorldDayTimeSection();

      logger.debug(`  Current Time   : ${currentTime}`);
      logger.debug(`  World Minute   : ${currentMinute.toFixed(2)}`);
      logger.debug(`  Day Section    : ${daySection}`);
    }

    logger.debug('');
    logger.debug('=== Configuration ===');
    logger.debug(`  Minute Scaling : ${config.minuteScaling} (world minutes per real minute)`);
    logger.debug(`  Minutes/Hour   : ${config.minutesPerHour}`);
    logger.debug(`  Hours/Day      : ${config.hoursPerDay}`);
    logger.debug(`  Days/Month     : ${config.daysPerMonth}`);
    logger.debug(`  Months/Year    : ${config.monthsPerYear}`);
    logger.debug('=====================');

    return {
      success: true,
      running: isRunning,
      currentTime: isRunning ? environmentService.getWorldTimeCurrentAsString() : null,
      currentMinute: isRunning ? environmentService.getWorldTimeCurrent() : null,
      daySection: isRunning ? environmentService.getWorldDayTimeSection() : null,
      config,
    };
  }
}
