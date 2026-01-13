/**
 * CameraLightRangeCommand - Set camera light range
 *
 * Usage: cameraLightRange [range]
 * - Without parameters: Shows current range
 * - With parameter: Sets range in blocks (typical 5-30)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CameraLightRangeCommand');

export class CameraLightRangeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cameraLightRange';
  }

  description(): string {
    return 'Set camera light range in blocks (typical 5-30)';
  }

  async execute(parameters: any[]): Promise<string> {
    const cameraService = this.appContext.services.camera;

    if (!cameraService) {
      return 'CameraService not available';
    }

    // Show current range if no parameters
    if (parameters.length === 0) {
      const range = cameraService.getCameraLightRange();
      return `Camera light range: ${range.toFixed(1)} blocks`;
    }

    // Parse and validate parameter
    const range = toNumber(parameters[0]);

    if (isNaN(range) || range < 1) {
      return 'Invalid range value (must be a number >= 1)';
    }

    // Set range
    cameraService.setCameraLightRange(range);

    logger.debug('Camera light range set', { range });

    return `Camera light range set to ${range.toFixed(1)} blocks`;
  }
}
