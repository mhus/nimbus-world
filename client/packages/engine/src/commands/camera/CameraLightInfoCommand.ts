/**
 * CameraLightInfoCommand - Show camera light information
 *
 * Usage: cameraLightInfo
 * Shows current camera light settings
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CameraLightInfoCommand');

export class CameraLightInfoCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cameraLightInfo';
  }

  description(): string {
    return 'Show camera light (torch) information';
  }

  async execute(parameters: any[]): Promise<string> {
    const cameraService = this.appContext.services.camera;

    if (!cameraService) {
      return 'CameraService not available';
    }

    const info = cameraService.getCameraLightInfo();

    return `Camera Light Info:
  Enabled: ${info.enabled}
  Intensity: ${info.intensity.toFixed(2)}
  Range: ${info.range.toFixed(1)} blocks
  Color: rgb(${(info.color.r * 255).toFixed(0)}, ${(info.color.g * 255).toFixed(0)}, ${(info.color.b * 255).toFixed(0)})`;
  }
}
