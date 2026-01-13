/**
 * CloudListCommand - List all clouds in the scene
 *
 * Usage: cloudList
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CloudListCommand');

export class CloudListCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudList';
  }

  description(): string {
    return 'List all clouds in the scene';
  }

  async execute(parameters: string[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    const clouds = cloudsService.getAllClouds();

    if (clouds.length === 0) {
      return 'No clouds in the scene';
    }

    const cloudList = clouds.map(cloud => {
      return [
        `Cloud: ${cloud.id}`,
        `  Level: ${cloud.level}`,
        `  Position: (${cloud.currentX.toFixed(1)}, ${cloud.currentZ.toFixed(1)}, ${cloud.y.toFixed(1)})`,
        `  Size: ${cloud.width}x${cloud.height}`,
        `  Speed: ${cloud.speed} blocks/s`,
        `  Direction: ${cloud.direction}°`,
        `  Enabled: ${cloud.enabled}`,
        `  Fade: ${(cloud.fadeAlpha * 100).toFixed(0)}%`,
      ].join('\n');
    }).join('\n\n');

    logger.debug('Cloud list requested', { count: clouds.length });

    return `Clouds (${clouds.length}):\n\n${cloudList}`;
  }
}
