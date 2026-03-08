/**
 * RenderScaleCommand - Adjust hardware scaling level at runtime
 *
 * Usage:
 * - /renderScale <value> - Set hardware scaling level (1 = native, 2 = half resolution, 0.5 = double resolution)
 * - /renderScale - Show current hardware scaling level
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('RenderScaleCommand');

export class RenderScaleCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'renderScale';
  }

  description(): string {
    return 'Set hardware scaling level (1 = native, 2 = half res, 0.5 = double res)';
  }

  execute(parameters: any[]): any {
    const engine = this.appContext.services.engine?.getEngine();
    if (!engine) {
      return 'Error: Engine not available';
    }

    if (parameters.length === 0) {
      const current = engine.getHardwareScalingLevel();
      return `Hardware scaling level: ${current}`;
    }

    const value = parseFloat(String(parameters[0]));
    if (isNaN(value) || value <= 0) {
      return 'Error: value must be a number > 0';
    }

    engine.setHardwareScalingLevel(value);
    logger.info(`Hardware scaling level set to ${value}`);
    return `Hardware scaling level set to ${value}`;
  }
}
