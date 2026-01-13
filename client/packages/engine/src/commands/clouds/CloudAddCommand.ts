/**
 * CloudAddCommand - Add a cloud to the scene
 *
 * Usage: cloudAdd [id] [x] [z] [y] [width] [height] [texture] [speed] [direction] [level]
 * - id: Unique cloud identifier (string)
 * - x, z, y: Position (start X, start Z, height Y)
 * - width, height: Cloud dimensions
 * - texture: Texture path (e.g., "textures/clouds/cloud1.png")
 * - speed: Movement speed (blocks per second, 0 = static)
 * - direction: Direction in degrees (0=North, 90=East, 180=South, 270=West)
 * - level: Cloud level/priority (0-9, optional, default: 0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudAddCommand');

export class CloudAddCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudAdd';
  }

  description(): string {
    return 'Add a cloud to the scene';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length < 9) {
      return 'Usage: cloudAdd [id] [x] [z] [y] [width] [height] [texture] [speed] [direction] [level?]\nExample: cloudAdd "cloud1" 0 0 200 80 50 "textures/clouds/cloud1.png" 5 90 0';
    }

    // Parse parameters
    const id = parameters[0];
    const startX = toNumber(parameters[1]);
    const startZ = toNumber(parameters[2]);
    const y = toNumber(parameters[3]);
    const width = toNumber(parameters[4]);
    const height = toNumber(parameters[5]);
    const texture = parameters[6];
    const speed = toNumber(parameters[7]);
    const direction = toNumber(parameters[8]);
    const level = parameters.length >= 10 ? toNumber(parameters[9]) : 0;

    // Validate parameters
    if (isNaN(startX) || isNaN(startZ) || isNaN(y) || isNaN(width) || isNaN(height) || isNaN(speed) || isNaN(direction)) {
      return 'Invalid parameters. All numeric values must be valid numbers.';
    }

    if (isNaN(level) || level < 0 || level > 9) {
      return 'Invalid level. Must be between 0 and 9.';
    }

    try {
      // Add cloud
      await cloudsService.addCloud({
        id,
        level,
        startX,
        startZ,
        y,
        width,
        height,
        texture,
        speed,
        direction,
      });

      logger.debug('Cloud added', { id, startX, startZ, y, width, height, texture, speed, direction, level });

      return `Cloud "${id}" added at position (${startX}, ${startZ}, ${y}) with size ${width}x${height}, speed ${speed}, direction ${direction}°`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to add cloud', { error: errorMessage });
      return `Failed to add cloud: ${errorMessage}`;
    }
  }
}
