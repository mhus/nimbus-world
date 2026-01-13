/**
 * SkyBoxEnableCommand - Enable/disable skybox
 *
 * Usage: skyBoxEnable [true|false]
 * - Without parameters: Shows usage information
 * - With "true": Enables skybox visibility
 * - With "false": Disables skybox visibility
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SkyBoxEnableCommand');

export class SkyBoxEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'skyBoxEnable';
  }

  description(): string {
    return 'Enable/disable skybox (true|false)';
  }

  async execute(parameters: string[]): Promise<string> {
    const skyBoxService = this.appContext.services.skyBox;

    if (!skyBoxService) {
      return 'SkyBoxService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: skyBoxEnable [true|false]\nExample: skyBoxEnable true';
    }

    const param = parameters[0].toLowerCase();

    if (param === 'true') {
      skyBoxService.setEnabled(true);
      logger.debug('SkyBox enabled via command');
      return 'SkyBox enabled';
    } else if (param === 'false') {
      skyBoxService.setEnabled(false);
      logger.debug('SkyBox disabled via command');
      return 'SkyBox disabled';
    } else {
      return 'Invalid parameter. Use "true" or "false".';
    }
  }
}
