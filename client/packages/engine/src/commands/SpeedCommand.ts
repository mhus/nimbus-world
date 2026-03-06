/**
 * SpeedCommand - Override player movement speed
 *
 * Usage:
 * - /speed <value> - Set speed override (0 = use default from player config)
 * - /speed - Show current speed override and effective speed
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';

export class SpeedCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'speed';
  }

  description(): string {
    return 'Override player movement speed (0 = use default)';
  }

  async execute(parameters: any[]): Promise<any> {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const entity = playerService.getPlayerEntity();
    if (!entity) {
      return 'Error: No player entity';
    }

    if (parameters.length === 0) {
      const override = entity.speedOverride ?? 0;
      return `Speed override: ${override} (effective: ${entity.effectiveSpeed}, override active: ${override > 0})`;
    }

    const value = parseFloat(String(parameters[0]));
    if (isNaN(value) || value < 0) {
      return 'Error: speed must be a number >= 0';
    }

    entity.speedOverride = value;

    if (value > 0) {
      return `Speed override set to ${value} (overrides default ${entity.effectiveSpeed})`;
    } else {
      return `Speed override disabled (using default ${entity.effectiveSpeed})`;
    }
  }
}
