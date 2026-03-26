/**
 * EnvironmentScriptParamCommand - Set/remove persistent script parameters
 *
 * Usage:
 *   environmentScriptParam <key> <value>   - Set parameter
 *   environmentScriptParam <key>           - Remove parameter
 *   environmentScriptParam                 - List all parameters
 *
 * Examples:
 *   environmentScriptParam("intensity", 0.7)
 *   environmentScriptParam("windStrength", 0.3)
 *   environmentScriptParam("intensity")        - removes intensity
 *   environmentScriptParam()                   - lists all
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('EnvironmentScriptParamCommand');

export class EnvironmentScriptParamCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'environmentScriptParam';
  }

  description(): string {
    return 'Set/remove/list persistent environment script parameters';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // No parameters: list all
    if (parameters.length === 0) {
      const params = environmentService.getScriptParameters();
      return {
        parameters: params,
        count: Object.keys(params).length,
      };
    }

    const key = String(parameters[0]);

    // One parameter: remove
    if (parameters.length === 1) {
      environmentService.removeScriptParameter(key);
      return {
        key,
        removed: true,
        message: `Parameter removed: ${key}`,
      };
    }

    // Two parameters: set
    const value = parameters[1];
    environmentService.setScriptParameter(key, value);
    return {
      key,
      value,
      message: `Parameter set: ${key} = ${value}`,
    };
  }
}
