/**
 * GetSelectedEditBlockCommand - Gets the currently selected edit block position
 *
 * Usage: getSelectedEditBlock
 * Returns: The position {x, y, z} or null if no block is selected
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('GetSelectedEditBlockCommand');
import type { AppContext } from '../AppContext';

/**
 * GetSelectedEditBlock command - Gets the selected edit block position
 */
export class GetSelectedEditBlockCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'getSelectedEditBlock';
  }

  description(): string {
    return 'Gets the currently selected edit block position';
  }

  execute(parameters: any[]): any {
    const selectService = this.appContext.services.select;

    if (!selectService) {
      logger.error('SelectService not available');
      return { error: 'SelectService not available' };
    }

    // Get selected block position
    const position = selectService.getSelectedEditBlock();

    if (!position) {
      logger.debug('No edit block currently selected');
      return { selected: false, position: null };
    }

    const result = {
      selected: true,
      position,
    };

    logger.debug('✓ Selected edit block:');
    logger.debug(`  Position: (${position.x}, ${position.y}, ${position.z})`);

    return result;
  }
}
