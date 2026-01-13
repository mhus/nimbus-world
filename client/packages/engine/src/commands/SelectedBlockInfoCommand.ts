/**
 * SelectedBlockInfoCommand - Shows detailed information about the currently selected block
 *
 * Displays:
 * - Block position and type
 * - Current modifier (visibility, physics, alpha)
 * - Textures with all properties (including backFaceCulling)
 * - Status and metadata
 * - Complete block JSON for debugging
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('SelectedBlockInfoCommand');
import type { AppContext } from '../AppContext';

/**
 * SelectedBlockInfo command - Shows complete information about the selected block
 */
export class SelectedBlockInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'selectedBlockInfo';
  }

  description(): string {
    return 'Shows detailed information about the currently selected block (position, modifier, textures, metadata, complete JSON)';
  }

  execute(parameters: any[]): any {
    const selectService = this.appContext.services.select;

    if (!selectService) {
      logger.error('SelectService not available');
      return { error: 'SelectService not available' };
    }

    const selectedBlock = selectService.getCurrentSelectedBlock();

    if (!selectedBlock) {
      logger.debug('No block currently selected');
      return { error: 'No block selected' };
    }

    const lines: string[] = [];
    lines.push('=== Selected Block Info ===');
    lines.push('');

    const block = selectedBlock.block;
    const blockType = selectedBlock.blockType;

    // Basic info
    lines.push('Block Info:');
    lines.push(`  Position     : (${block.position.x}, ${block.position.y}, ${block.position.z})`);
    lines.push(`  Block Type ID: ${block.blockTypeId}`);
    if (blockType) {
      lines.push(`  Block Type   : ${blockType.id}`);
    }
    lines.push(`  Chunk        : (${selectedBlock.chunk.cx}, ${selectedBlock.chunk.cz})`);
    lines.push('');

    // Current modifier info
    if (selectedBlock.currentModifier) {
      const mod = selectedBlock.currentModifier;
      lines.push('Current Modifier:');

      if (mod.visibility) {
        lines.push('  Visibility:');
        lines.push(`    Shape      : ${mod.visibility.shape}`);
        if (mod.visibility.effect !== undefined) {
          lines.push(`    Effect     : ${mod.visibility.effect}`);
        }
        if (mod.visibility.path !== undefined) {
          lines.push(`    Path       : ${mod.visibility.path}`);
        }

        // Show textures in detail
        if (mod.visibility.textures) {
          lines.push('    Textures:');
          for (const [key, texture] of Object.entries(mod.visibility.textures)) {
            lines.push(`      [${key}]:`);
            if (typeof texture === 'string') {
              lines.push(`        Path: ${texture}`);
            } else {
              // TextureDefinition object
              lines.push(`        ${JSON.stringify(texture, null, 2).split('\n').map(line => '        ' + line).join('\n').trim()}`);
            }
          }
        }

        // Scaling
        if (mod.visibility.scalingX !== undefined || mod.visibility.scalingY !== undefined) {
          lines.push(`    Scaling    : (${mod.visibility.scalingX ?? 1}, ${mod.visibility.scalingY ?? 1})`);
        }

        // Rotation
        if (mod.visibility.rotation?.x !== undefined || mod.visibility.rotation?.y !== undefined) {
          lines.push(`    Rotation   : (${mod.visibility.rotation?.x ?? 0}, ${mod.visibility.rotation?.y ?? 0})`);
        }
      }

      if (mod.physics) {
        lines.push('  Physics:');
        lines.push(`    Solid      : ${mod.physics.solid}`);
        if (mod.physics.interactive !== undefined) {
          lines.push(`    Interactive: ${mod.physics.interactive}`);
        }
        if (mod.physics.climbable !== undefined) {
          lines.push(`    Climbable  : ${mod.physics.climbable}`);
        }
      }

      lines.push('');
    }

    // Block modifiers (instance-specific)
    if (block.modifiers && Object.keys(block.modifiers).length > 0) {
      lines.push('Instance Modifiers:');
      lines.push(JSON.stringify(block.modifiers, null, 2).split('\n').map(line => '  ' + line).join('\n'));
      lines.push('');
    }

    // Block status if available
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
    lines.push('============================');

    const output = lines.join('\n');
    logger.debug(output);

    // Return structured data
    return {
      block,
      blockType,
      chunk: {
        cx: selectedBlock.chunk.cx,
        cz: selectedBlock.chunk.cz,
      },
      currentModifier: selectedBlock.currentModifier,
    };
  }
}
