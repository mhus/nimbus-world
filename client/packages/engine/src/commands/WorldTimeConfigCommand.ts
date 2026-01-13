/**
 * WorldTimeConfigCommand - Configure World Time settings
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WorldTimeConfigCommand');

/**
 * WorldTimeConfig command - Configure World Time settings
 * Usage: worldTimeConfig <minuteScaling> <minutesPerHour> <hoursPerDay> <daysPerMonth> <monthsPerYear>
 */
export class WorldTimeConfigCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'worldTimeConfig';
  }

  description(): string {
    return 'Configure World Time settings (minuteScaling, minutesPerHour, hoursPerDay, daysPerMonth, monthsPerYear)';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;
    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Parse parameters
    if (parameters.length !== 5) {
      logger.error(
        'Usage: worldTimeConfig <minuteScaling> <minutesPerHour> <hoursPerDay> <daysPerMonth> <monthsPerYear>'
      );
      return {
        error: 'Invalid parameters',
        usage:
          'worldTimeConfig <minuteScaling> <minutesPerHour> <hoursPerDay> <daysPerMonth> <monthsPerYear>',
      };
    }

    const minuteScaling = toNumber(parameters[0]);
    const minutesPerHour = toNumber(parameters[1]);
    const hoursPerDay = toNumber(parameters[2]);
    const daysPerMonth = toNumber(parameters[3]);
    const monthsPerYear = toNumber(parameters[4]);

    // Validate parameters
    if (
      isNaN(minuteScaling) ||
      isNaN(minutesPerHour) ||
      isNaN(hoursPerDay) ||
      isNaN(daysPerMonth) ||
      isNaN(monthsPerYear)
    ) {
      logger.error('All parameters must be valid numbers');
      return { error: 'All parameters must be valid numbers' };
    }

    // Set configuration
    environmentService.setWorldTimeConfig(
      minuteScaling,
      minutesPerHour,
      hoursPerDay,
      daysPerMonth,
      monthsPerYear
    );

    const config = environmentService.getWorldTimeConfig();

    logger.debug('=== World Time Config Updated ===');
    logger.debug(`  @Minute Scaling    : ${config.minuteScaling} (world minutes per real minute)`);
    logger.debug(`  @Minutes per Hour  : ${config.minutesPerHour}`);
    logger.debug(`  @Hours per Day     : ${config.hoursPerDay}`);
    logger.debug(`  @Days per Month    : ${config.daysPerMonth}`);
    logger.debug(`  @Months per Year   : ${config.monthsPerYear}`);
    logger.debug('=================================');

    return { success: true, config };
  }
}
