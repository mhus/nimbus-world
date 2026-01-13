/**
 * ShadowsDistanceCommand - Set shadow rendering distance
 *
 * Usage: shadowsDistance <blocks>
 * Sets the maximum distance shadows are rendered (reduces performance impact)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('ShadowsDistanceCommand');

export class ShadowsDistanceCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsDistance';
  }

  description(): string {
    return 'Set shadow rendering distance in blocks (default: 100)\n' +
        'Smaller distance = better performance, shorter shadows\n' +
        'Larger distance = worse performance, longer shadows\n' +
        '\n' +
        'Current: ${distance} blocks`;\n' +
        '  }\n';
  }

  async execute(parameters: any[]): Promise<string> {
    if (parameters.length === 0) {
      return 'Usage: shadowsDistance <blocks>\nExample: shadowsDistance 50';
    }

    const distance = toNumber(parameters[0]);

    if (isNaN(distance) || distance < 10 || distance > 15000) {
      return 'Invalid distance. Must be between 10 and 15000 blocks.';
    }

    const envService = this.appContext.services.environment;
    if (!envService) {
      return 'EnvironmentService not available';
    }

    if (envService.setShadowDistance(distance)) {
      return `Shadow distance set to ${distance} blocks).`;
    } else {
      return 'Shadow generator not initialized';
    }
  }
}
