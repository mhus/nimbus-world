/**
 * GetStackModifierCurrentValueCommand - Get the current value of a stack
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('GetStackModifierCurrentValueCommand');
import type { AppContext } from '../../AppContext';

/**
 * GetStackModifierCurrentValue command - Get the current effective value of a stack
 *
 * Usage:
 *   getStackModifierCurrentValue <stackName>
 *
 * Parameters:
 *   stackName - Name of the stack (e.g., 'fogViewMode', 'playerViewMode')
 *
 * Examples:
 *   getStackModifierCurrentValue fogViewMode
 *   getStackModifierCurrentValue playerViewMode
 *   getStackModifierCurrentValue ambientAudio
 */
export class GetStackModifierCurrentValueCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'getStackModifierCurrentValue';
  }

  description(): string {
    return 'Get the current effective value of a modifier stack';
  }

  execute(parameters: any[]): any {
    const modifierService = this.appContext.services.modifier;

    if (!modifierService) {
      logger.error('ModifierService not available');
      return { error: 'ModifierService not available' };
    }

    if (parameters.length < 1) {
      logger.error('Usage: getStackModifierCurrentValue <stackName>');
      return { error: 'Invalid parameters. Need stackName.' };
    }

    const stackName = parameters[0] as string;

    // Get the stack
    const stack = modifierService.getModifierStack(stackName);
    if (!stack) {
      logger.error(`Stack '${stackName}' does not exist`);
      return { error: `Stack '${stackName}' does not exist` };
    }

    const currentValue = stack.getValue();
    const modifierCount = stack.modifiers.length;

    const message = `Stack '${stackName}' current value: ${JSON.stringify(currentValue)} (${modifierCount} active modifiers)`;
    logger.debug(message);

    return {
      stackName,
      currentValue,
      modifierCount,
      message,
    };
  }
}
