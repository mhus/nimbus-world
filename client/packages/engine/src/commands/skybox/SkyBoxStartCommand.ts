/**
 * SkyBoxStartCommand - Configure and enable skybox with one command
 *
 * Usage: skyBoxStart <color> <rotation> <size> <texture>
 *
 * Parameters:
 * - color: RGB color as object {r,g,b} or JSON string
 * - rotation: Rotation in degrees (0-360)
 * - size: Size multiplier (typically 1000-2000)
 * - texture: Texture path (e.g., "textures/skybox/default.png")
 *
 * Examples:
 *   skyBoxStart("{\"r\":0.2,\"g\":0.5,\"b\":1.0}" 0 1500 "textures/skybox/sky.png")
 *   skyBoxStart("{r:0.5,g:0.7,b:1}" 45 1200 "textures/skybox/sunset.png")
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber, toObject, toString } from '@nimbus/shared';
import { Color3 } from '@babylonjs/core/Maths/math.color';

const logger = getLogger('SkyBoxStartCommand');

export class SkyBoxStartCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxStart';
  }

  description(): string {
    return 'Configure and enable skybox: skyBoxStart <color> <rotation> <size> <texture>';
  }

  async execute(parameters: any[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if insufficient parameters
    if (parameters.length < 4) {
      return (
        'Usage: skyBoxStart <color> <rotation> <size> <texture>\n' +
        'Example: skyBoxStart "{\\"r\\":0.2,\\"g\\":0.5,\\"b\\":1.0}" 0 1500 "textures/skybox/sky.png"'
      );
    }

    // Parse parameters
    const colorObj = toObject(parameters[0]);
    const rotation = toNumber(parameters[1]);
    const size = toNumber(parameters[2]);
    const texture = toString(parameters[3]);

    // Validate color object exists and has r, g, b properties
    if (!colorObj || typeof colorObj !== 'object' || !('r' in colorObj) || !('g' in colorObj) || !('b' in colorObj)) {
      return 'Invalid color parameter. Must be an object with r, g, b properties (0-1).\nExample: "{\\"r\\":0.2,\\"g\\":0.5,\\"b\\":1.0}"';
    }

    // Convert color values to numbers
    const r = toNumber(colorObj.r);
    const g = toNumber(colorObj.g);
    const b = toNumber(colorObj.b);

    // Validate numeric values
    if (isNaN(r) || isNaN(g) || isNaN(b) || isNaN(rotation) || isNaN(size)) {
      return 'Invalid numeric parameters. Color RGB, rotation and size must be numbers.';
    }

    // Validate color values (0-1)
    if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1) {
      return 'Invalid color values. RGB values must be between 0 and 1.';
    }

    // Validate texture path
    if (!texture || texture.length === 0) {
      return 'Invalid texture parameter. Must be a non-empty string.';
    }

    try {
      // Set size first (before applying material)
      skyBoxService.setSize(size);
      logger.debug('SkyBox size set', { size });

      // Set rotation
      skyBoxService.setRotation(rotation);
      logger.debug('SkyBox rotation set', { rotation });

      // Set color mode
      const color = new Color3(r, g, b);
      skyBoxService.setColorMode(color);
      logger.debug('SkyBox color mode set', { r, g, b });

      // Set texture mode (async)
      await skyBoxService.setTextureMode(texture);
      logger.debug('SkyBox texture mode set', { texture });

      // Enable skybox
      skyBoxService.setEnabled(true);
      logger.debug('SkyBox enabled via skyBoxStart command');

      return (
        `SkyBox configured and enabled:\n` +
        `  Color: RGB(${r}, ${g}, ${b})\n` +
        `  Rotation: ${rotation}°\n` +
        `  Size: ${size}\n` +
        `  Texture: ${texture}`
      );
    } catch (error) {
      logger.error('Failed to configure skybox', { error: (error as Error).message });
      return `Failed to configure skybox: ${(error as Error).message}`;
    }
  }
}
