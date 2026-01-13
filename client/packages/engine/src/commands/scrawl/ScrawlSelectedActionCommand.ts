/**
 * ScrawlSelectedActionCommand - Execute a scrawl action with auto-filled source/target
 *
 * Automatically fills source (player) and target (selected block/entity) from current selection.
 * Sends to server for multiplayer synchronization.
 */

import { CommandHandler } from '../CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { ScriptActionDefinition } from '@nimbus/shared';
import type { AppContext } from '../../AppContext';

const logger = getLogger('ScrawlSelectedActionCommand');

export class ScrawlSelectedActionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'scrawlSelectedAction';
  }

  description(): string {
    return 'Execute a scrawl action with auto-filled source/target (synchronized to server)';
  }

  async execute(args: any[]): Promise<void> {
    const { appContext } = this;

    if (!appContext.services.scrawl) {
      logger.error('ScrawlService not available');
      return;
    }

    if (args.length === 0) {
      logger.error('Usage: scrawlAction <json|object>');
      logger.debug('Examples:');
      logger.debug('  scrawlAction \'{"script":{"id":"test","root":{"kind":"Play","effectId":"log"}}}\'');
      logger.debug('  scrawlAction({script:{id:"test",root:{kind:"Play",effectId:"circleMarker"}}})');
      return;
    }

    const scrawlService = appContext.services.scrawl;
    const playerService = appContext.services.player;
    const selectService = appContext.services.select;

    try {
      let action: ScriptActionDefinition;

      // Check if first argument is already an object
      if (typeof args[0] === 'object' && args[0] !== null) {
        action = args[0] as ScriptActionDefinition;
      } else {
        // Parse JSON string
        const input = args.join(' ');
        action = JSON.parse(input);
      }

      // Get source (player) and target (selected block/entity)
      const source = playerService?.getPlayerEntity();
      const selectedEntity = selectService?.getCurrentSelectedEntity();
      const selectedBlock = selectService?.getCurrentSelectedBlock();

      let target: any = undefined;
      if (selectedEntity) {
        target = selectedEntity;
      } else if (selectedBlock) {
        // Create wrapper with .position for ClientBlock
        target = {
          position: {
            x: selectedBlock.block.position.x + 0.5,
            y: selectedBlock.block.position.y + 0.5,
            z: selectedBlock.block.position.z + 0.5,
          },
          block: selectedBlock.block,
          blockType: selectedBlock.blockType,
        };
      }

      // Prepare context with source/target in vars
      const context = {
        vars: {
          source,
          target,
          targets: target ? [target] : [],
        },
      };

      logger.debug('Executing scrawl action', {
        hasScript: !!action.script,
        scriptId: action.scriptId || action.script?.id,
        hasSource: !!source,
        hasTarget: !!target,
        sendToServer: action.sendToServer !== false,
      });

      // Execute action (will send to server if sendToServer !== false)
      const executorId = await scrawlService.executeAction(action, context);

      logger.debug(`Scrawl action started with executor ID: ${executorId}`);
    } catch (error: any) {
      logger.error('Failed to execute scrawl action', { error: error.message });
    }
  }
}
