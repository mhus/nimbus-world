/**
 * ListStacksCommand - List all available modifier stacks
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ListStacksCommand');
import type { AppContext } from '../../AppContext';
import { AnimationStack } from '../../services/ModifierService';
import { getModifierName } from './SetStackModifierCommand';

/**
 * ListStacks command - List all available modifier stacks with their current values
 *
 * Usage:
 *   listStacks [verbose]
 *
 * Parameters:
 *   verbose - Optional: Show detailed information including all modifiers (default: false)
 *
 * Examples:
 *   listStacks           - Show basic stack information
 *   listStacks verbose   - Show detailed information with all modifiers
 */
export class ListStacksCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'listStacks';
  }

  description(): string {
    return 'List all available modifier stacks with their current values';
  }

  execute(parameters: any[]): any {
    const modifierService = this.appContext.services.modifier;

    if (!modifierService) {
      logger.error('ModifierService not available');
      return { error: 'ModifierService not available' };
    }

    const verbose = parameters.length > 0 &&
      (parameters[0] === 'verbose' || parameters[0] === 'v' || parameters[0] === 'true');

    const stackNames = modifierService.stackNames;

    if (stackNames.length === 0) {
      logger.debug('No modifier stacks available');
      return { stacks: [] };
    }

    logger.debug(`\n${'='.repeat(80)}`);
    logger.debug(`Available Modifier Stacks (${stackNames.length} total)`);
    logger.debug(`${'='.repeat(80)}\n`);

    const stacksInfo: any[] = [];

    for (const stackName of stackNames) {
      const stack = modifierService.getModifierStack(stackName);
      if (!stack) continue;

      const currentValue = stack.getValue();
      const modifierCount = stack.modifiers.length;
      const isAnimationStack = stack instanceof AnimationStack;
      const defaultValue = stack.getDefaultModifier().getValue();

      const stackInfo: any = {
        name: stackName,
        type: isAnimationStack ? 'AnimationStack' : 'ModifierStack',
        currentValue,
        defaultValue,
        modifierCount,
      };

      // Basic output
      logger.debug(`Stack: ${stackName}`);
      logger.debug(`  Type: ${isAnimationStack ? 'AnimationStack ⏱' : 'ModifierStack'}`);
      logger.debug(`  Current Value: ${this.formatValue(currentValue)}`);
      logger.debug(`  Default Value: ${this.formatValue(defaultValue)}`);
      logger.debug(`  Active Modifiers: ${modifierCount}`);

      if (verbose && modifierCount > 0) {
        logger.debug(`  Modifiers:`);
        stackInfo.modifiers = [];

        const modifiers = stack.modifiers;
        for (let i = 0; i < modifiers.length; i++) {
          const modifier = modifiers[i];
          const modifierName = getModifierName(stackName, modifier);

          const modifierInfo: any = {
            index: i,
            name: modifierName || '(unnamed)',
            value: modifier.getValue(),
            priority: modifier.prio,
            enabled: modifier.enabled,
            sequence: modifier.sequence,
          };

          logger.debug(`    [${i}] ${modifierName ? `Name: "${modifierName}", ` : ''}` +
            `Value: ${this.formatValue(modifier.getValue())}, ` +
            `Priority: ${modifier.prio}, ` +
            `Enabled: ${modifier.enabled}, ` +
            `Sequence: ${modifier.sequence}`);

          stackInfo.modifiers.push(modifierInfo);
        }
      }

      logger.debug(''); // Empty line between stacks
      stacksInfo.push(stackInfo);
    }

    logger.debug(`${'='.repeat(80)}\n`);

    // Summary
    const animationStackCount = stacksInfo.filter(s => s.type === 'AnimationStack').length;
    const regularStackCount = stacksInfo.filter(s => s.type === 'ModifierStack').length;
    const totalModifiers = stacksInfo.reduce((sum, s) => sum + s.modifierCount, 0);

    logger.debug('Summary:');
    logger.debug(`  Total Stacks: ${stacksInfo.length}`);
    logger.debug(`  - AnimationStacks: ${animationStackCount}`);
    logger.debug(`  - ModifierStacks: ${regularStackCount}`);
    logger.debug(`  Total Active Modifiers: ${totalModifiers}`);
    logger.debug('');

    return {
      stacks: stacksInfo,
      summary: {
        totalStacks: stacksInfo.length,
        animationStacks: animationStackCount,
        modifierStacks: regularStackCount,
        totalModifiers,
      },
    };
  }

  /**
   * Format a value for display
   */
  private formatValue(value: any): string {
    if (typeof value === 'number') {
      return value.toFixed(3);
    } else if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    } else if (typeof value === 'string') {
      return `"${value}"`;
    } else if (value === null || value === undefined) {
      return 'null';
    } else {
      return JSON.stringify(value);
    }
  }
}
