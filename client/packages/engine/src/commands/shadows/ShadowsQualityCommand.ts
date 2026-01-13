/**
 * ShadowsQualityCommand - Set shadow quality preset
 *
 * Usage: shadowsQuality [quality]
 * - Without parameters: Shows current quality
 * - With parameter: Sets quality (low, medium, high)
 *
 * Quality presets:
 * - low: 512px map, hard shadows, fastest
 * - medium: 1024px map, ESM filtering, balanced
 * - high: 2048px map, PCF filtering, best quality
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toString } from '@nimbus/shared';

const logger = getLogger('ShadowsQualityCommand');

type ShadowQuality = 'low' | 'medium' | 'high';

export class ShadowsQualityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsQuality';
  }

  description(): string {
    return 'Set shadow quality preset (low, medium, high)';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    // Show current quality if no parameters
    if (parameters.length === 0) {
      const info = envService.getShadowInfo();
      return `Current shadow quality: ${info.quality} (map size: ${info.mapSize}px)`;
    }

    // Parse and validate parameter
    const quality = toString(parameters[0]).toLowerCase() as ShadowQuality;

    if (!['low', 'medium', 'high'].includes(quality)) {
      return 'Invalid quality. Must be: low, medium, or high';
    }

    // Set shadow quality
    envService.setShadowQuality(quality);

    logger.debug('Shadow quality set', { quality });

    return `Shadow quality set to ${quality}`;
  }
}
