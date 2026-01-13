/**
 * WireframeCommand - Toggle wireframe rendering mode
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('WireframeCommand');
import type { AppContext } from '../AppContext';

/**
 * Wireframe command - Toggle wireframe mode to see the mesh structure
 *
 * Usage:
 *   wireframe true   - Enable wireframe mode
 *   wireframe false  - Disable wireframe mode
 *   wireframe        - Toggle wireframe mode
 */
export class WireframeCommand extends CommandHandler {
  private appContext: AppContext;
  private wireframeEnabled: boolean = false;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'wireframe';
  }

  description(): string {
    return 'Toggle wireframe rendering mode (true/false or no parameter to toggle)';
  }

  execute(parameters: any[]): any {
    const engineService = this.appContext.services.engine;

    if (!engineService) {
      logger.error('EngineService not available');
      return { error: 'EngineService not available' };
    }

    // Parse parameter
    let enabled: boolean;

    if (parameters.length === 0) {
      // No parameter: toggle current state
      enabled = !this.wireframeEnabled;
    } else {
      // Parse first parameter as boolean
      const param = parameters[0];

      if (typeof param === 'boolean') {
        enabled = param;
      } else if (typeof param === 'string') {
        const lower = param.toLowerCase();
        if (lower === 'true' || lower === '1' || lower === 'on' || lower === 'yes') {
          enabled = true;
        } else if (lower === 'false' || lower === '0' || lower === 'off' || lower === 'no') {
          enabled = false;
        } else {
          logger.error(`Invalid parameter: ${param}. Use true/false.`);
          return { error: 'Invalid parameter. Use true/false.' };
        }
      } else {
        logger.error('Invalid parameter type. Use true/false.');
        return { error: 'Invalid parameter type. Use true/false.' };
      }
    }

    // Update state
    this.wireframeEnabled = enabled;

    // Apply wireframe mode
    engineService.setWireframeMode(enabled);

    const message = `Wireframe mode ${enabled ? 'enabled' : 'disabled'}`;
    logger.debug(message);

    return {
      wireframeEnabled: enabled,
      message,
    };
  }
}
