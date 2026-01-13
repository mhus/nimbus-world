/**
 * SetSelectedEditBlockCommand - Sets the selected edit block (green highlight)
 *
 * Usage: setSelectedEditBlock [x] [y] [z]
 * Example: setSelectedEditBlock 10 64 5   (selects block at position)
 * Example: setSelectedEditBlock           (clears selection)
 */

import { CommandHandler } from './CommandHandler';
import { toNumber, getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('SetSelectedEditBlockCommand');

/**
 * SetSelectedEditBlock command - Sets or clears the selected edit block
 */
export class SetSelectedEditBlockCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'setSelectedEditBlock';
  }

  description(): string {
    return 'Sets or clears the selected edit block (setSelectedEditBlock [x] [y] [z])';
  }

  execute(parameters: any[]): any {
    const selectService = this.appContext.services.select;

    if (!selectService) {
      logger.error('SelectService not available');
      return { error: 'SelectService not available' };
    }

    // No parameters = clear selection
    if (parameters.length === 0) {
      selectService.setSelectedEditBlock();
      logger.debug('✓ Edit block selection cleared');
      return { success: true, cleared: true };
    }

    // Need exactly 3 parameters for position
    if (parameters.length !== 3) {
      logger.error('Usage: setSelectedEditBlock [x] [y] [z]');
      logger.error('');
      logger.error('Parameters:');
      logger.error('  No parameters: Clears the selection');
      logger.error('  x, y, z: World coordinates of the block to select');
      logger.error('');
      logger.error('Examples:');
      logger.error('  setSelectedEditBlock           (clear selection)');
      logger.error('  setSelectedEditBlock 10 64 5   (select block at x=10, y=64, z=5)');
      return { error: 'Invalid arguments' };
    }

    // Parse coordinates
    const x = toNumber(parameters[0]);
    const y = toNumber(parameters[1]);
    const z = toNumber(parameters[2]);

    if (isNaN(x) || isNaN(y) || isNaN(z)) {
      logger.error('✗ Coordinates must be valid numbers');
      return { error: 'Invalid coordinates' };
    }

    // Set selection
    try {
      selectService.setSelectedEditBlock(x, y, z);

      const result = {
        success: true,
        position: { x, y, z },
      };

      logger.debug('✓ Edit block selected with green highlight:');
      logger.debug(`  Position: (${x}, ${y}, ${z})`);

      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error(`✗ Failed to set selected edit block: ${errorMessage}`);
      return { error: errorMessage };
    }
  }
}
