/**
 * CameraLightEnableCommand - Enable or disable camera light (torch)
 *
 * Usage: cameraLightEnable [true|false]
 * - Without parameters: Shows current state
 * - With parameter: Enables or disables camera light
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toBoolean } from '@nimbus/shared';

const logger = getLogger('CameraLightEnableCommand');

export class CameraLightEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cameraLightEnable';
  }

  description(): string {
    return 'Enable or disable camera light (torch/flashlight)';
  }

  async execute(parameters: any[]): Promise<string> {
    const cameraService = this.appContext.services.camera;

    if (!cameraService) {
      return 'CameraService not available';
    }

    // Show current state if no parameters
    if (parameters.length === 0) {
      const enabled = cameraService.isCameraLightEnabled();
      return `Camera light is currently ${enabled ? 'enabled' : 'disabled'}`;
    }

    // Parse and validate parameter
    const enabled = toBoolean(parameters[0]);

    // Set camera light enabled/disabled
    cameraService.setCameraLightEnabled(enabled);

    logger.debug('Camera light toggled', { enabled });

    return `Camera light ${enabled ? 'enabled' : 'disabled'}`;
  }
}
