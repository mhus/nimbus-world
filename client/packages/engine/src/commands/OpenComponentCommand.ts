/**
 * OpenComponentCommand - Opens a predefined component modal
 *
 * Usage: openComponent <component> <attributes...>
 * Example: openComponent block_editor 10 64 5
 *
 * Available components:
 * - block_editor: Opens block editor at position (x, y, z)
 *   Usage: openComponent block_editor <x> <y> <z>
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('OpenComponentCommand');
import type { AppContext } from '../AppContext';

/**
 * OpenComponent command - Opens predefined component modals
 */
export class OpenComponentCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'openComponent';
  }

  description(): string {
    return 'Opens a predefined component modal (openComponent <component> <attributes...>)';
  }

  execute(parameters: any[]): any {
    const modalService = this.appContext.services.modal;

    if (!modalService) {
      logger.error('ModalService not available');
      return { error: 'ModalService not available' };
    }

    if (parameters.length < 1) {
      logger.error('Usage: openComponent <component> <attributes...>');
      logger.error('');
      logger.error('Available components:');
      logger.error('');
      logger.error('  block_editor - Opens block editor at a specific position');
      logger.error('    Usage: openComponent block_editor <x> <y> <z>');
      logger.error('    Example: openComponent block_editor 10 64 5');
      logger.error('');
      logger.error('  panel - Opens panel navigation');
      logger.error('    Usage: openComponent panel');
      logger.error('    Example: openComponent panel');
      logger.error('');
      logger.error('Future components:');
      logger.error('  settings, inventory, map, etc.');
      return { error: 'Invalid arguments' };
    }

    // Parse component name
    const component = parameters[0].toString();

    // Parse attributes (remaining parameters)
    const attributes = parameters.slice(1).map((p) => p.toString());

    // Open component modal
    try {
      const modalRef = modalService.openComponent(component, attributes);

      const result = {
        success: true,
        modalId: modalRef.id,
        component,
        attributes,
      };

      logger.debug(`✓ Modal opened successfully:`);
      logger.debug(`  ID: ${modalRef.id}`);
      logger.debug(`  Component: ${component}`);
      logger.debug(`  Attributes: ${attributes.join(', ')}`);

      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error(`✗ Failed to open modal: ${errorMessage}`);
      return { error: errorMessage };
    }
  }
}
