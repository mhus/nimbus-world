/**
 * SetAutoSelectCommand - Set or toggle auto-select mode
 *
 * Controls the default selection mode for block/entity selection.
 * This is the mode used by the '.' key toggle.
 *
 * Usage:
 *   setAutoSelect                 - Show current mode
 *   setAutoSelect toggle          - Toggle through modes (NONE -> INTERACTIVE -> BLOCK -> ALL -> NONE)
 *   setAutoSelect <mode>          - Set specific mode
 *
 * Modes:
 *   NONE        - No auto-selection
 *   INTERACTIVE - Only interactive blocks
 *   BLOCK       - Any solid block
 *   AIR         - Only air blocks (empty spaces)
 *   ALL         - Any block or air
 *
 * Examples:
 *   setAutoSelect interactive     - Set to interactive mode
 *   setAutoSelect toggle          - Toggle to next mode
 *   setAutoSelect none            - Disable auto-select
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, toString } from '@nimbus/shared';
import { SelectMode } from '../services/SelectService';

const logger = getLogger('SetAutoSelectCommand');

export class SetAutoSelectCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'setAutoSelect';
  }

  description(): string {
    return 'Set or toggle auto-select mode (NONE|INTERACTIVE|BLOCK|AIR|ALL)';
  }

  execute(parameters: any[]): any {
    const selectService = this.appContext.services.select;
    if (!selectService) {
      return 'Error: SelectService not available';
    }

    // No parameters - show current mode
    if (parameters.length === 0) {
      const currentMode = selectService.getAutoSelectMode();
      return {
        currentMode,
        usage: 'setAutoSelect [mode|toggle]',
        modes: ['NONE', 'INTERACTIVE', 'BLOCK', 'AIR', 'ALL'],
        examples: [
          'setAutoSelect interactive  - Set to interactive mode',
          'setAutoSelect toggle       - Toggle to next mode',
          'setAutoSelect none         - Disable auto-select',
        ],
      };
    }

    const modeParam = toString(parameters[0]).toUpperCase();

    // Handle toggle
    if (modeParam === 'TOGGLE') {
      const currentMode = selectService.getAutoSelectMode();
      const nextMode = this.getNextMode(currentMode);
      selectService.autoSelectMode = nextMode;
      logger.info('Auto-select mode toggled', { from: currentMode, to: nextMode });
      return `Auto-select mode: ${currentMode} → ${nextMode}`;
    }

    // Parse and set specific mode
    const mode = this.parseMode(modeParam);
    if (mode === null) {
      return {
        error: `Invalid mode: ${parameters[0]}`,
        validModes: ['NONE', 'INTERACTIVE', 'BLOCK', 'AIR', 'ALL', 'toggle'],
      };
    }

    selectService.autoSelectMode = mode;
    logger.info('Auto-select mode set', { mode });
    return `Auto-select mode set to: ${mode}`;
  }

  /**
   * Parse mode string to SelectMode enum
   */
  private parseMode(value: string): SelectMode | null {
    switch (value) {
      case 'NONE':
        return SelectMode.NONE;
      case 'INTERACTIVE':
        return SelectMode.INTERACTIVE;
      case 'BLOCK':
        return SelectMode.BLOCK;
      case 'AIR':
        return SelectMode.AIR;
      case 'ALL':
        return SelectMode.ALL;
      default:
        return null;
    }
  }

  /**
   * Get next mode in cycle: NONE -> INTERACTIVE -> BLOCK -> ALL -> NONE
   * (skipping AIR as it's rarely used)
   */
  private getNextMode(current: SelectMode): SelectMode {
    switch (current) {
      case SelectMode.NONE:
        return SelectMode.INTERACTIVE;
      case SelectMode.INTERACTIVE:
        return SelectMode.BLOCK;
      case SelectMode.BLOCK:
        return SelectMode.ALL;
      case SelectMode.ALL:
      case SelectMode.AIR:
      default:
        return SelectMode.NONE;
    }
  }
}
