/**
 * CameraLightIntensityCommand - Set camera light intensity
 *
 * Usage: cameraLightIntensity [intensity]
 * - Without parameters: Shows current intensity
 * - With parameter: Sets intensity (0-10, typical 0.5-2.0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CameraLightIntensityCommand');

export class CameraLightIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cameraLightIntensity';
  }

  description(): string {
    return 'Set camera light intensity (0-10, typical 0.5-2.0)';
  }

  async execute(parameters: any[]): Promise<string> {
    const cameraService = this.appContext.services.camera;

    if (!cameraService) {
      return 'CameraService not available';
    }

    // Show current intensity if no parameters
    if (parameters.length === 0) {
      const intensity = cameraService.getCameraLightIntensity();
      return `Camera light intensity: ${intensity.toFixed(2)}`;
    }

    // Parse and validate parameter
    const intensity = toNumber(parameters[0]);

    if (isNaN(intensity)) {
      return 'Invalid intensity value (must be a number)';
    }

    // Set intensity
    cameraService.setCameraLightIntensity(intensity);

    logger.debug('Camera light intensity set', { intensity });

    return `Camera light intensity set to ${intensity.toFixed(2)}`;
  }
}
