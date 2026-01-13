/**
 * BlockTypeInfoCommand - Shows detailed information about a BlockType
 *
 * Usage: blockTypeInfo <blockTypeId>
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('BlockTypeInfoCommand');

/**
 * BlockTypeInfo command - Shows complete information about a BlockType
 */
export class BlockTypeInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'blockTypeInfo';
  }

  description(): string {
    return 'Show detailed information about a BlockType';
  }

  async execute(parameters: any[]): Promise<any> {
    if (parameters.length === 0) {
      return 'Usage: blockTypeInfo <blockTypeId>\n\nExample: blockTypeInfo 1';
    }

    const blockTypeId = String(parameters[0]);
    if (!blockTypeId) {
      return 'Error: blockTypeId is required';
    }

    const blockTypeService = this.appContext.services.blockType;
    if (!blockTypeService) {
      return 'Error: BlockTypeService not available';
    }

    try {
      // Get BlockType from service
      const blockType = await blockTypeService.getBlockType(blockTypeId);

      if (!blockType) {
        return `BlockType ${blockTypeId} not found in cache.\n\nTry accessing a block with this type first, or it may not exist on the server.`;
      }

      // Format BlockType information
      const info = [
        `=== BlockType Info ===\n`,
        `BlockType ID: ${blockType.id}`,
        `Description: ${blockType.description || 'N/A'}`,
        `Initial Status: ${blockType.initialStatus ?? 0}`,
        `\nModifiers:`,
      ];

      // List all status modifiers
      const statusKeys = Object.keys(blockType.modifiers).map(Number).sort((a, b) => a - b);
      for (const status of statusKeys) {
        const modifier = blockType.modifiers[status];
        info.push(`\n  Status ${status}:`);

        if (modifier.visibility) {
          info.push(`    Visibility:`);
          info.push(`      Shape: ${modifier.visibility.shape ?? 'N/A'}`);
          if (modifier.visibility.textures) {
            info.push(`      Textures: ${JSON.stringify(modifier.visibility.textures, null, 8)}`);
          }
          if (modifier.visibility.scalingX !== undefined) info.push(`      scalingX: ${modifier.visibility.scalingX}`);
          if (modifier.visibility.scalingY !== undefined) info.push(`      scalingY: ${modifier.visibility.scalingY}`);
          if (modifier.visibility.scalingZ !== undefined) info.push(`      scalingZ: ${modifier.visibility.scalingZ}`);
        }

        if (modifier.physics) {
          info.push(`    Physics:`);
          info.push(`      Solid: ${modifier.physics.solid ?? 'N/A'}`);
          info.push(`      Interactive: ${modifier.physics.interactive ?? 'N/A'}`);
          if (modifier.physics.autoClimbable !== undefined) info.push(`      AutoClimbable: ${modifier.physics.autoClimbable}`);
          if (modifier.physics.collisionEvent !== undefined) info.push(`      CollisionEvent: ${modifier.physics.collisionEvent}`);
        }

        if (modifier.wind) {
          info.push(`    Wind: ${JSON.stringify(modifier.wind)}`);
        }

        if (modifier.illumination) {
          info.push(`    Illumination: ${JSON.stringify(modifier.illumination)}`);
        }

        if (modifier.effects) {
          info.push(`    Effects: ${JSON.stringify(modifier.effects)}`);
        }

        if (modifier.audio) {
          info.push(`    Audio: ${JSON.stringify(modifier.audio)}`);
        }
      }

      info.push(`\n\nComplete BlockType JSON:`);
      info.push(JSON.stringify(blockType, null, 2));
      info.push('\n==================');

      return info.join('\n');
    } catch (error) {
      logger.error('Failed to get BlockType info', { blockTypeId }, error as Error);
      return `Error: ${(error as Error).message}`;
    }
  }
}
