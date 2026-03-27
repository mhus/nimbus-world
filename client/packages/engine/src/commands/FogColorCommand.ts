/**
 * FogColorCommand - Set fog color
 *
 * Usage:
 *   fogColor <r> <g> <b>  - Set fog color (0-1 per channel)
 *   fogColor               - Reset to default gray
 *
 * Examples:
 *   fogColor 0.05 0.05 0.1  - Dark blue (night)
 *   fogColor 0.5 0.5 0.5    - Default gray (day)
 *   fogColor 0.4 0.35 0.3   - Warm tone (evening)
 *   fogColor                 - Reset to default
 */

import { CommandHandler } from './CommandHandler';
import { toNumber, getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('FogColorCommand');

export class FogColorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'fogColor';
  }

  description(): string {
    return 'Set fog color (r g b, 0-1 per channel) or reset to default';
  }

  execute(parameters: any[]): any {
    const cameraService = this.appContext.services.camera;

    if (!cameraService) {
      return { error: 'CameraService not available' };
    }

    // Reset if no parameters
    if (parameters.length === 0) {
      cameraService.setFogColor();
      return { message: 'Fog color reset to default' };
    }

    if (parameters.length < 3) {
      return { error: 'Usage: fogColor <r> <g> <b> (0-1 per channel)' };
    }

    const r = toNumber(parameters[0]);
    const g = toNumber(parameters[1]);
    const b = toNumber(parameters[2]);

    if (isNaN(r) || isNaN(g) || isNaN(b)) {
      return { error: 'Invalid color values. Use numeric values (0-1).' };
    }

    cameraService.setFogColor(r, g, b);

    logger.debug('Fog color set', { r, g, b });
    return { r, g, b, message: `Fog color set to RGB(${r}, ${g}, ${b})` };
  }
}
