/**
 * BlockInfoCommand - Shows detailed information about a block at specific coordinates
 *
 * Usage: blockInfo <x> <y> <z>
 */

import { CommandHandler } from './CommandHandler';
import {  toNumber , getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('BlockInfoCommand');

/**
 * BlockInfo command - Shows complete information about a block at coordinates
 */
export class BlockInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'blockInfo';
  }

  description(): string {
    return 'Shows block info at coordinates (x, y, z). Usage: blockInfo <x> <y> <z>';
  }

  execute(parameters: any[]): any {
    // Validate parameters
    if (parameters.length !== 3) {
      logger.error('Usage: blockInfo <x> <y> <z>');
      return {
        error: 'Invalid parameters',
        usage: 'blockInfo <x> <y> <z>',
        example: 'blockInfo 2 64 -8',
      };
    }

    const x = toNumber(parameters[0]);
    const y = toNumber(parameters[1]);
    const z = toNumber(parameters[2]);

    if (isNaN(x) || isNaN(y) || isNaN(z)) {
      logger.error('Coordinates must be numbers');
      return {
        error: 'Invalid coordinates',
        x: parameters[0],
        y: parameters[1],
        z: parameters[2],
      };
    }

    const chunkService = this.appContext.services.chunk;

    if (!chunkService) {
      logger.error('ChunkService not available');
      return { error: 'ChunkService not available' };
    }

    // Get block at position (no loading)
    const clientBlock = chunkService.getBlockAt(x, y, z);

    if (!clientBlock) {
      logger.debug(`No block found at (${x}, ${y}, ${z}) - chunk may not be loaded`);
      return {
        message: `No block at (${x}, ${y}, ${z})`,
        hint: 'Chunk may not be loaded or block is air',
      };
    }

    const lines: string[] = [];
    lines.push('=== Block Info ===');
    lines.push('');

    const block = clientBlock.block;
    const blockType = clientBlock.blockType;

    // Basic info
    lines.push('Block Info:');
    lines.push(`  Position     : (${block.position.x}, ${block.position.y}, ${block.position.z})`);
    lines.push(`  Block Type ID: ${block.blockTypeId}`);
    if (blockType) {
      lines.push(`  Description  : ${blockType.description || '(unnamed)'}`);
    }
    lines.push('');

    // Current modifier info
    if (clientBlock.currentModifier) {
      const mod = clientBlock.currentModifier;
      lines.push('Current Modifier:');

      if (mod.visibility) {
        lines.push('  Visibility:');
        lines.push(`    Shape      : ${mod.visibility.shape}`);
        if (mod.visibility.offsets) {
          lines.push(`    Offsets    : [${mod.visibility.offsets.join(', ')}]`);
        }
      }

      if (mod.physics) {
        lines.push('  Physics:');
        lines.push(`    Solid             : ${mod.physics.solid ?? false}`);
        lines.push(`    Interactive       : ${mod.physics.interactive ?? false}`);
        lines.push(`    CollisionEvent    : ${mod.physics.collisionEvent ?? false}`);
        lines.push(`    AutoClimbable     : ${mod.physics.autoClimbable ?? false}`);
        if (mod.physics.resistance !== undefined) {
          lines.push(`    Resistance        : ${mod.physics.resistance}`);
        }
      }

      lines.push('');
    }

    // Block-level properties
    if (block.offsets) {
      lines.push('Block Offsets:');
      lines.push(`  [${block.offsets.join(', ')}]`);
      lines.push('');
    }

    // Block modifiers (instance-specific)
    if (block.modifiers && Object.keys(block.modifiers).length > 0) {
      lines.push('Instance Modifiers:');
      lines.push(JSON.stringify(block.modifiers, null, 2).split('\n').map(line => '  ' + line).join('\n'));
      lines.push('');
    }

    // Block status
    if (block.status !== undefined) {
      lines.push(`Status: ${block.status}`);
      lines.push('');
    }

    // Metadata if available
    if (block.metadata) {
      lines.push('Metadata:');
      for (const [key, value] of Object.entries(block.metadata)) {
        lines.push(`  ${key}: ${JSON.stringify(value)}`);
      }
      lines.push('');
    }

    // Output complete block as JSON for debugging
    lines.push('Complete Block JSON:');
    lines.push(JSON.stringify(block, null, 2).split('\n').map(line => '  ' + line).join('\n'));

    lines.push('');

    // Output complete ClientBlock as JSON for debugging
    lines.push('Complete ClientBlock JSON:');
    lines.push(JSON.stringify(clientBlock, null, 2).split('\n').map(line => '  ' + line).join('\n'));

    lines.push('');
    lines.push('==================');

    const output = lines.join('\n');
    logger.debug(output);

    // Return structured data
    return {
      position: block.position,
      blockTypeId: block.blockTypeId,
      block,
      blockType,
      currentModifier: clientBlock.currentModifier,
    };
  }
}
