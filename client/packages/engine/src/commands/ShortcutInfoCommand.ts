/**
 * ShortcutInfoCommand - Shows detailed information about active shortcuts
 *
 * Displays:
 * - Active shortcuts with their state
 * - Shortcut configuration from PlayerInfo
 * - Item information (if bound to an item)
 * - Targeting mode and current target
 * - Executor state
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShortcutInfoCommand');

/**
 * ShortcutInfo command - Shows active shortcut details
 *
 * Usage:
 *   shortcutInfo              - Show all active shortcuts
 *   shortcutInfo <number>     - Show specific shortcut by number (0-9)
 */
export class ShortcutInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'shortcutInfo';
  }

  description(): string {
    return 'Shows detailed information about active shortcuts';
  }

  async execute(parameters: any[]): Promise<any> {
    const shortcutService = this.appContext.services.shortcut;
    const playerService = this.appContext.services.player;
    const itemService = this.appContext.services.item;
    const targetingService = this.appContext.services.targeting;
    const scrawlService = this.appContext.services.scrawl;

    if (!shortcutService || !playerService) {
      logger.error('Required services not available');
      return { error: 'Services not available' };
    }

    const lines: string[] = [];
    lines.push('=== Shortcut Info ===');
    lines.push('');

    // Parse shortcut parameter (can be number or key like 'click0', 'key1')
    let shortcutKey: string | undefined;
    let shortcutNr: number | undefined;

    if (parameters[0] !== undefined) {
      const param = String(parameters[0]);

      // Check if it's a shortcut key (click0, key1, etc.)
      if (param.startsWith('click') || param.startsWith('key') || param.startsWith('slot')) {
        shortcutKey = param;
        // Extract number from key
        if (param.startsWith('click')) {
          shortcutNr = parseInt(param.replace('click', ''), 10);
        } else if (param.startsWith('key')) {
          shortcutNr = parseInt(param.replace('key', ''), 10);
        } else if (param.startsWith('slot')) {
          shortcutNr = parseInt(param.replace('slot', ''), 10);
        }
      } else {
        // It's a number
        shortcutNr = parseInt(param, 10);
        shortcutKey = shortcutNr === 0 ? 'key0' : `key${shortcutNr}`;
      }
    }

    if (shortcutNr !== undefined && shortcutKey !== undefined) {
      // Show specific shortcut
      await this.showShortcutDetails(lines, shortcutNr, shortcutKey);
    } else {
      // Show all active shortcuts
      const activeShortcuts = shortcutService.getActiveShortcuts();

      if (activeShortcuts.length === 0) {
        lines.push('No active shortcuts');
      } else {
        lines.push(`Active Shortcuts: ${activeShortcuts.length}`);
        lines.push('');

        for (const shortcut of activeShortcuts) {
          await this.showShortcutSummary(lines, shortcut);
          lines.push('');
        }
      }
    }

    lines.push('===================');

    const output = lines.join('\n');
    logger.debug(output);

    // Build detailed return object
    const result: any = {
      activeShortcuts: shortcutService.getActiveShortcuts(),
    };

    // If specific shortcut requested, include full objects
    if (shortcutNr !== undefined && shortcutKey !== undefined) {
      const activeShortcut = shortcutService.getActiveShortcut(shortcutNr);
      result.shortcut = activeShortcut;

      // Get shortcut configuration (even if not active)
      const playerInfo = playerService.getPlayerEntity().playerInfo;
      const shortcutDef = playerInfo.shortcuts?.[shortcutKey];
      result.shortcutConfig = shortcutDef;

      // Get item details
      const itemId = activeShortcut?.itemId || shortcutDef?.itemId;
      if (itemId && itemService) {
        const item = await itemService.getItem(itemId);
        result.item = item;

        if (item) {
          const itemType = await itemService.getItemType(item.itemType);
          result.itemType = itemType;

          const mergedModifier = await itemService.getMergedModifier(item);
          result.mergedModifier = mergedModifier;

          // Explicitly show actionTargeting
          logger.debug('\n=== ACTION TARGETING ===');
          logger.debug('ItemType.modifier.actionTargeting:', itemType?.modifier?.actionTargeting);
          logger.debug('Item.modifier.actionTargeting:', item.modifier?.actionTargeting);
          logger.debug('MergedModifier.actionTargeting:', mergedModifier?.actionTargeting);
          logger.debug('========================\n');
        }
      }

      // Add current target
      if (targetingService && activeShortcut) {
        const currentTarget = targetingService.resolveTarget(activeShortcut.targetingMode);
        result.currentTarget = currentTarget;
      }

      // Add executor info
      if (scrawlService && activeShortcut) {
        const effectId = scrawlService.getEffectIdForExecutor(activeShortcut.executorId);
        result.effectId = effectId;
        result.hasEffectId = !!effectId;
      }
    }

    logger.debug('\n=== FULL OBJECTS (JSON) ===');
    logger.debug('Shortcut:', JSON.stringify(result.shortcut, null, 2));
    logger.debug('\nItem:', JSON.stringify(result.item, null, 2));
    logger.debug('\nItemType:', JSON.stringify(result.itemType, null, 2));
    logger.debug('\nMergedModifier:', JSON.stringify(result.mergedModifier, null, 2));
    logger.debug('\nShortcutConfig:', JSON.stringify(result.shortcutConfig, null, 2));
    logger.debug('\nCurrentTarget:', JSON.stringify(result.currentTarget, null, 2));
    logger.debug('===========================\n');

    return result;
  }

  /**
   * Show summary of a shortcut
   */
  private async showShortcutSummary(lines: string[], shortcut: any): Promise<void> {
    const duration = (Date.now() - shortcut.startTime) / 1000;

    lines.push(`Shortcut #${shortcut.shortcutNr} (${shortcut.shortcutKey})`);
    lines.push(`  Executor ID    : ${shortcut.executorId}`);
    lines.push(`  Duration       : ${duration.toFixed(2)}s`);
    lines.push(`  Exclusive      : ${shortcut.exclusive}`);
    lines.push(`  Targeting Mode : ${shortcut.targetingMode}`);

    if (shortcut.itemId) {
      lines.push(`  Item ID        : ${shortcut.itemId}`);

      // Load item details
      const itemService = this.appContext.services.item;
      if (itemService) {
        const item = await itemService.getItem(shortcut.itemId);
        if (item) {
          lines.push(`  Item Name      : ${item.name}`);
          const mergedModifier = await itemService.getMergedModifier(item);
          if (mergedModifier) {
            lines.push(`  Action Target  : ${mergedModifier.actionTargeting ?? 'ALL'}`);
            lines.push(`  Pose           : ${mergedModifier.pose ?? 'none'}`);
            lines.push(`  Exclusive      : ${mergedModifier.exclusive ?? false}`);
          }
        }
      }
    }
  }

  /**
   * Show detailed information about a specific shortcut
   */
  private async showShortcutDetails(lines: string[], shortcutNr: number, shortcutKey: string): Promise<void> {
    const shortcutService = this.appContext.services.shortcut;
    const playerService = this.appContext.services.player;
    const itemService = this.appContext.services.item;
    const targetingService = this.appContext.services.targeting;
    const scrawlService = this.appContext.services.scrawl;

    if (!shortcutService || !playerService) {
      lines.push('Required services not available');
      return;
    }

    // Get active shortcut
    const activeShortcut = shortcutService.getActiveShortcut(shortcutNr);

    if (!activeShortcut) {
      lines.push(`Shortcut #${shortcutNr} (${shortcutKey}): Not active`);
      lines.push('');

      // Show configuration anyway
      const playerInfo = playerService.getPlayerEntity().playerInfo;
      const shortcutDef = playerInfo.shortcuts?.[shortcutKey];

      if (shortcutDef) {
        lines.push('Shortcut Configuration:');
        lines.push(`  Key            : ${shortcutKey}`);
        lines.push(`  Type           : ${shortcutDef.type}`);
        lines.push(`  Item ID        : ${shortcutDef.itemId ?? 'none'}`);

        if (shortcutDef.itemId && itemService) {
          const item = await itemService.getItem(shortcutDef.itemId);
          if (item) {
            lines.push(`  Item Name      : ${item.name}`);
            const mergedModifier = await itemService.getMergedModifier(item);
            if (mergedModifier) {
              lines.push(`  Action Target  : ${mergedModifier.actionTargeting ?? 'ALL'}`);
              lines.push(`  Pose           : ${mergedModifier.pose ?? 'none'}`);
              lines.push(`  Has onUseEffect: ${!!mergedModifier.onUseEffect}`);
            }
          }
        }
      } else {
        lines.push('No shortcut configuration found');
      }
      return;
    }

    // Show active shortcut details
    const duration = (Date.now() - activeShortcut.startTime) / 1000;

    lines.push(`Shortcut #${activeShortcut.shortcutNr} - ACTIVE`);
    lines.push('');
    lines.push('State:');
    lines.push(`  Shortcut Key   : ${activeShortcut.shortcutKey}`);
    lines.push(`  Executor ID    : ${activeShortcut.executorId}`);
    lines.push(`  Duration       : ${duration.toFixed(2)}s`);
    lines.push(`  Exclusive      : ${activeShortcut.exclusive}`);
    lines.push(`  Targeting Mode : ${activeShortcut.targetingMode}`);
    lines.push('');

    // Show item details
    if (activeShortcut.itemId && itemService) {
      const item = await itemService.getItem(activeShortcut.itemId);
      if (item) {
        lines.push('Item:');
        lines.push(`  Item ID        : ${activeShortcut.itemId}`);
        lines.push(`  Item Name      : ${item.name}`);
        lines.push(`  Item Type      : ${item.itemType}`);
        lines.push('');

        const mergedModifier = await itemService.getMergedModifier(item);
        if (mergedModifier) {
          lines.push('Item Modifier:');
          lines.push(`  Texture        : ${mergedModifier.texture}`);
          lines.push(`  Action Target  : ${mergedModifier.actionTargeting ?? 'ALL (default)'}`);
          lines.push(`  Pose           : ${mergedModifier.pose ?? 'none'}`);
          lines.push(`  Exclusive      : ${mergedModifier.exclusive ?? false}`);
          lines.push(`  Has onUseEffect: ${!!mergedModifier.onUseEffect}`);
          lines.push(`  Has actionScript: ${!!mergedModifier.actionScript}`);
          lines.push('');
        }
      }
    }

    // Show current target resolution
    if (targetingService) {
      const currentTarget = targetingService.resolveTarget(activeShortcut.targetingMode);
      lines.push('Current Target:');
      lines.push(`  Type           : ${currentTarget.type}`);

      if (currentTarget.type !== 'none') {
        lines.push(`  Position       : (${currentTarget.position.x.toFixed(2)}, ${currentTarget.position.y.toFixed(2)}, ${currentTarget.position.z.toFixed(2)})`);

        if (currentTarget.type === 'entity') {
          lines.push(`  Entity ID      : ${currentTarget.entity.id}`);
        } else if (currentTarget.type === 'block') {
          const blockPos = currentTarget.block.block.position;
          lines.push(`  Block Position : (${blockPos.x}, ${blockPos.y}, ${blockPos.z})`);
          lines.push(`  Block Type ID  : ${currentTarget.block.blockType?.id}`);
        }
      }
      lines.push('');
    }

    // Show executor state
    if (scrawlService) {
      const effectId = scrawlService.getEffectIdForExecutor(activeShortcut.executorId);
      lines.push('Executor:');
      lines.push(`  Executor ID    : ${activeShortcut.executorId}`);
      lines.push(`  Effect ID      : ${effectId ?? 'none (no server sync)'}`);
      lines.push(`  Has Effect ID  : ${!!effectId}`);
      lines.push('');
    }

    // Show deprecated position data if present
    if (activeShortcut.lastPlayerPos || activeShortcut.lastTargetPos) {
      lines.push('Deprecated Position Data:');
      if (activeShortcut.lastPlayerPos) {
        const pos = activeShortcut.lastPlayerPos;
        lines.push(`  Last Player Pos: (${pos.x.toFixed(2)}, ${pos.y.toFixed(2)}, ${pos.z.toFixed(2)})`);
      }
      if (activeShortcut.lastTargetPos) {
        const pos = activeShortcut.lastTargetPos;
        lines.push(`  Last Target Pos: (${pos.x.toFixed(2)}, ${pos.y.toFixed(2)}, ${pos.z.toFixed(2)})`);
      }
      lines.push('');
    }
  }
}
