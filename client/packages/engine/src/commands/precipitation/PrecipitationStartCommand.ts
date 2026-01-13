/**
 * PrecipitationStartCommand - Start precipitation with full control
 *
 * Usage: precipitationStart [intensity] [r] [g] [b] [size] [speed] [gravity]
 * - intensity: 0-100
 * - r, g, b: Color (0.0-1.0)
 * - size: Particle size
 * - speed: Emit power (fall speed)
 * - gravity: Gravity strength
 *
 * Example: precipitationStart 50 0.4 0.4 0.6 0.3 25 15
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { Color4 } from '@babylonjs/core';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('PrecipitationStartCommand');

export class PrecipitationStartCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'precipitationStart';
  }

  description(): string {
    return 'Start precipitation with custom parameters (intensity r g b size speed gravity)';
  }

  async execute(parameters: any[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    // Parse parameters
    if (parameters.length < 7) {
      return 'Usage: precipitationStart [intensity] [r] [g] [b] [size] [speed] [gravity]\nExample: precipitationStart 50 0.4 0.4 0.6 0.3 25 15';
    }

    const intensity = toNumber(parameters[0]);
    const r = toNumber(parameters[1]);
    const g = toNumber(parameters[2]);
    const b = toNumber(parameters[3]);
    const size = toNumber(parameters[4]);
    const speed = toNumber(parameters[5]);
    const gravity = toNumber(parameters[6]);

    // Validate
    if (isNaN(intensity) || intensity < 0) {
      return 'Intensity must be non-negative';
    }
    if (isNaN(r) || isNaN(g) || isNaN(b) || r < 0 || g < 0 || b < 0 || r > 1 || g > 1 || b > 1) {
      return 'RGB values must be 0.0-1.0';
    }
    if (isNaN(size) || size <= 0) {
      return 'Size must be positive';
    }
    if (isNaN(speed) || speed < 0) {
      return 'Speed must be non-negative';
    }
    if (isNaN(gravity)) {
      return 'Gravity must be a number';
    }

    try {
      // Set all parameters first (while system might be running)
      precipitationService.setIntensity(intensity);
      precipitationService.setParticleSize(size);
      precipitationService.setParticleColor(new Color4(r, g, b, 1.0));
      precipitationService.setParticleSpeed(speed);
      precipitationService.setParticleGravity(gravity);

      // If not enabled, enable it (creates system with new parameters)
      if (!precipitationService.isEnabled()) {
        precipitationService.setEnabled(true);
      } else {
        // Already enabled - need to recreate with new parameters
        precipitationService.setEnabled(false);
        precipitationService.setEnabled(true);
      }

      logger.debug('Precipitation started', { intensity, color: { r, g, b }, size, speed, gravity });
      return `Precipitation started: intensity=${intensity}, color=(${r},${g},${b}), size=${size}, speed=${speed}, gravity=${gravity}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to start precipitation', { error: errorMessage });
      return `Failed to start precipitation: ${errorMessage}`;
    }
  }
}
