/**
 * CloudsAnimationStartCommand - Start automated cloud animation
 *
 * Usage: cloudsAnimationStart <jobName> <emitCountPerMinute> <emitProbability> <minX> <maxX> <minZ> <maxZ> <minY> <maxY> <minWidth> <maxWidth> <minHeight> <maxHeight> <speed> <direction> <texture1,texture2,...>
 *
 * Example: cloudsAnimationStart myJob 10 0.5 -100 100 -100 100 80 120 20 40 10 20 2.0 90 textures/cloud1.png,textures/cloud2.png
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import type { Area } from '../../services/CloudsService';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudsAnimationStartCommand');

export class CloudsAnimationStartCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudsAnimationStart';
  }

  description(): string {
    return 'Start automated cloud animation that creates clouds over time';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    if (parameters.length < 16) {
      return 'Usage: cloudsAnimationStart <jobName> <emitCountPerMinute> <emitProbability> <minX> <maxX> <minZ> <maxZ> <minY> <maxY> <minWidth> <maxWidth> <minHeight> <maxHeight> <speed> <direction> <texture1,texture2,...>';
    }

    try {
      const jobName = parameters[0];
      const emitCountPerMinute = toNumber(parameters[1]);
      const emitProbability = toNumber(parameters[2]);

      // Parse area
      const area: Area = {
        minX: toNumber(parameters[3]),
        maxX: toNumber(parameters[4]),
        minZ: toNumber(parameters[5]),
        maxZ: toNumber(parameters[6]),
        minY: toNumber(parameters[7]),
        maxY: toNumber(parameters[8]),
        minWidth: toNumber(parameters[9]),
        maxWidth: toNumber(parameters[10]),
        minHeight: toNumber(parameters[11]),
        maxHeight: toNumber(parameters[12]),
      };

      const speed = toNumber(parameters[13]);
      const direction = toNumber(parameters[14]);
      const textures = parameters[15].split(',').map((t: string) => t.trim());

      // Validate numeric values
      if (isNaN(emitCountPerMinute) || isNaN(emitProbability) || isNaN(speed) || isNaN(direction)) {
        return 'Invalid numeric parameters';
      }

      if (Object.values(area).some(isNaN)) {
        return 'Invalid area parameters';
      }

      cloudsService.startCloudsAnimation(
        jobName,
        emitCountPerMinute,
        emitProbability,
        area,
        speed,
        direction,
        textures
      );

      logger.debug('Cloud animation started via command', { jobName, emitCountPerMinute, emitProbability, speed, direction });
      return `Cloud animation '${jobName}' started (${emitCountPerMinute} emits/min, direction: ${direction}°, ${textures.length} textures)`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to start cloud animation', { error: errorMessage });
      return `Failed to start cloud animation: ${errorMessage}`;
    }
  }
}
