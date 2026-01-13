/**
 * FogCommand - Set fog camera mode intensity
 */

import { CommandHandler } from './CommandHandler';
import {  toNumber , getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('FogCommand');

/**
 * Fog command - Set fog camera effect intensity
 *
 * Usage:
 *   fog <intensity>  - Set fog intensity (0 = off, 0.1-1.0 = intensity)
 *   fog              - Show current fog intensity
 *
 * Examples:
 *   fog 0     - Disable fog
 *   fog 0.3   - Light fog
 *   fog 0.8   - Heavy fog (DEAD mode default)
 *   fog 1.0   - Maximum fog
 */
export class FogCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'fog';
  }

  description(): string {
    return 'Set fog camera mode intensity (0 = off, 0.1-1.0 = intensity)';
  }

  execute(parameters: any[]): any {
    const playerService = this.appContext.services.player;

    if (!playerService) {
      logger.error('PlayerService not available');
      return { error: 'PlayerService not available' };
    }

    // Get current intensity from modifier stack
    const fogStack = this.appContext.services.modifier?.getModifierStack<number>(
      this.appContext.services.modifier?.constructor.name === 'ModifierService'
        ? 'fogViewMode' as any
        : 'fogViewMode' as any
    );

    // Show current value if no parameters
    if (parameters.length === 0) {
      const currentIntensity = fogStack?.getValue() ?? 0;
      const message = `Current fog intensity: ${currentIntensity.toFixed(2)} (0 = off, 1.0 = max)`;
      logger.debug(message);
      return {
        intensity: currentIntensity,
        message,
      };
    }

    // Parse intensity parameter
    const intensity = toNumber(parameters[0]);

    if (isNaN(intensity)) {
      logger.error(`Invalid intensity: ${parameters[0]}. Use numeric value (0-1.0).`);
      return { error: 'Invalid intensity. Use numeric value (0-1.0).' };
    }

    if (intensity < 0 || intensity > 1.0) {
      logger.error(`Intensity out of range: ${intensity}. Must be 0-1.0.`);
      return { error: 'Intensity must be between 0 and 1.0.' };
    }

    // Apply fog intensity via PlayerService
    playerService.setFogViewMode(intensity);

    const message = intensity === 0
      ? 'Fog disabled'
      : `Fog intensity set to ${intensity.toFixed(2)}`;
    logger.debug(message);

    return {
      intensity,
      message,
    };
  }
}
