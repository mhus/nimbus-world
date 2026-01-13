/**
 * SetStackModifierCommand - Set or update a modifier in a stack
 */

import { CommandHandler } from '../CommandHandler';
import {  toNumber , getLogger } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';
import { Modifier, AnimationModifier, AnimationStack } from '../../services/ModifierService';

const logger = getLogger('SetStackModifierCommand');

/**
 * Global modifier registry to track named modifiers across commands
 * Maps: stackName -> modifierName -> Modifier
 */
export const MODIFIER_REGISTRY = new Map<string, Map<string, Modifier<any> | AnimationModifier<any>>>();

/**
 * SetStackModifier command - Set or update a modifier value in a stack
 *
 * Usage:
 *   setStackModifier <stackName> <modifierName> <value> [prio] [waitTime]
 *
 * Parameters:
 *   stackName    - Name of the stack (e.g., 'fogViewMode', 'playerViewMode')
 *   modifierName - Name/ID for the modifier (to update existing or create new)
 *                  Use empty string '' to set the default value directly
 *   value        - The value to set (type depends on stack)
 *   prio         - Optional priority (default: 50, lower = higher priority)
 *                  Ignored when modifierName is empty
 *   waitTime     - Optional wait time in milliseconds for AnimationStack (default: 100ms)
 *
 * Examples:
 *   setStackModifier fogViewMode weather 0.5 10
 *   setStackModifier fogViewMode weather 0.8 10 200
 *   setStackModifier playerViewMode cutscene false 5
 *   setStackModifier ambientLightIntensity '' 1.0        # Set default value
 *   setStackModifier sunPosition '' 90 0 500             # Set default with waitTime
 */
export class SetStackModifierCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'setStackModifier';
  }

  description(): string {
    return 'Set or update a modifier value in a stack (supports AnimationStack with waitTime)';
  }

  execute(parameters: any[]): any {
    const modifierService = this.appContext.services.modifier;

    if (!modifierService) {
      logger.error('ModifierService not available');
      return { error: 'ModifierService not available' };
    }

    if (parameters.length < 3) {
      logger.error('Usage: setStackModifier <stackName> <modifierName> <value> [prio] [waitTime]');
      return { error: 'Invalid parameters. Need at least stackName, modifierName, and value.' };
    }

    const stackName = parameters[0] as string;
    const modifierName = parameters[1] as string;
    const valueStr = parameters[2];
    const prio = parameters.length >= 4 ? toNumber(parameters[3]) : 50;
    const waitTime = parameters.length >= 5 ? toNumber(parameters[4]) : undefined;

    // Validate priority
    if (isNaN(prio)) {
      logger.error(`Invalid priority: ${parameters[3]}`);
      return { error: 'Invalid priority. Must be a number.' };
    }

    // Validate waitTime if provided
    if (waitTime !== undefined && isNaN(waitTime)) {
      logger.error(`Invalid waitTime: ${parameters[4]}`);
      return { error: 'Invalid waitTime. Must be a number in milliseconds.' };
    }

    // Get the stack
    const stack = modifierService.getModifierStack(stackName);
    if (!stack) {
      logger.error(`Stack '${stackName}' does not exist`);
      return { error: `Stack '${stackName}' does not exist` };
    }

    // Parse value based on stack's current value type
    const currentValue = stack.getValue();
    let parsedValue: any;

    try {
      if (typeof currentValue === 'boolean') {
        parsedValue = valueStr === 'true' || valueStr === '1';
      } else if (typeof currentValue === 'number') {
        parsedValue = parseFloat(valueStr);
        if (isNaN(parsedValue)) {
          throw new Error('Invalid number value');
        }
      } else if (typeof currentValue === 'string') {
        parsedValue = valueStr;
      } else {
        // Try JSON parse for complex types
        parsedValue = JSON.parse(valueStr);
      }
    } catch (error) {
      logger.error(`Failed to parse value '${valueStr}' for stack '${stackName}':`, error);
      return { error: `Failed to parse value. Expected type: ${typeof currentValue}` };
    }

    // Special case: Empty modifierName sets the default value
    if (modifierName === '' || modifierName === '""' || modifierName === "''") {
      const defaultModifier = stack.getDefaultModifier();
      const oldDefaultValue = defaultModifier.getValue();

      // Set the new default value
      if (stack instanceof AnimationStack && waitTime !== undefined) {
        // For AnimationStack with waitTime
        if ('setWaitTime' in defaultModifier && typeof (defaultModifier as any).setWaitTime === 'function') {
          (defaultModifier as any).setWaitTime(waitTime);
        }
      }

      defaultModifier.setValue(parsedValue);

      const message = `Default value of stack '${stackName}' set to ${parsedValue}${waitTime !== undefined ? ` (waitTime: ${waitTime}ms)` : ''}`;
      logger.debug(message);

      return {
        action: 'default_value_set',
        stackName,
        modifierName: '(default)',
        oldValue: oldDefaultValue,
        newValue: parsedValue,
        currentValue: stack.getValue(),
        waitTime,
        message,
      };
    }

    // Get or create modifier registry for this stack
    let stackModifiers = MODIFIER_REGISTRY.get(stackName);
    if (!stackModifiers) {
      stackModifiers = new Map();
      MODIFIER_REGISTRY.set(stackName, stackModifiers);
    }

    // Check if modifier already exists
    const existingModifier = stackModifiers.get(modifierName);

    if (existingModifier) {
      // Update existing modifier
      if (existingModifier instanceof AnimationModifier && waitTime !== undefined) {
        existingModifier.setValue(parsedValue, waitTime);
      } else {
        existingModifier.setValue(parsedValue);
      }

      const message = `Updated modifier '${modifierName}' in stack '${stackName}' to value: ${parsedValue}${waitTime !== undefined ? ` (waitTime: ${waitTime}ms)` : ''}`;
      logger.debug(message);

      return {
        action: 'updated',
        stackName,
        modifierName,
        value: parsedValue,
        prio,
        waitTime,
        message,
      };
    } else {
      // Create new modifier
      const modifier = modifierService.addModifier(stackName, {
        value: parsedValue,
        prio,
        waitTime,
      });

      stackModifiers.set(modifierName, modifier);

      const message = `Created modifier '${modifierName}' in stack '${stackName}' with value: ${parsedValue}, prio: ${prio}${waitTime !== undefined ? `, waitTime: ${waitTime}ms` : ''}`;
      logger.debug(message);

      return {
        action: 'created',
        stackName,
        modifierName,
        value: parsedValue,
        prio,
        waitTime,
        message,
      };
    }
  }

  /**
   * Remove a named modifier
   * @param stackName The stack name
   * @param modifierName The modifier name
   */
  removeModifier(stackName: string, modifierName: string): boolean {
    const stackModifiers = MODIFIER_REGISTRY.get(stackName);
    if (!stackModifiers) {
      return false;
    }

    const modifier = stackModifiers.get(modifierName);
    if (!modifier) {
      return false;
    }

    modifier.close();
    stackModifiers.delete(modifierName);
    return true;
  }

  /**
   * Get a named modifier
   * @param stackName The stack name
   * @param modifierName The modifier name
   */
  getModifier(stackName: string, modifierName: string): Modifier<any> | AnimationModifier<any> | undefined {
    return MODIFIER_REGISTRY.get(stackName)?.get(modifierName);
  }
}

/**
 * Get the name of a modifier if it exists in the registry
 * @param stackName The stack name
 * @param modifier The modifier instance
 * @returns The modifier name or undefined
 */
export function getModifierName(stackName: string, modifier: Modifier<any> | AnimationModifier<any>): string | undefined {
  const stackModifiers = MODIFIER_REGISTRY.get(stackName);
  if (!stackModifiers) return undefined;

  for (const [name, mod] of stackModifiers.entries()) {
    if (mod === modifier) {
      return name;
    }
  }
  return undefined;
}
