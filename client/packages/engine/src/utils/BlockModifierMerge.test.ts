/**
 * BlockModifierMerge - effect normalization
 *
 * The server delivers BlockEffect as a number, a numeric string or the enum name,
 * depending on how the block type was written: Java writes the name, imported assets
 * may carry the raw number. Everything downstream expects the number - MaterialService
 * builds its material key with Number(effect), so an unnormalized name would become
 * 'eff:NaN' and silently drop the shader.
 */

import { BlockEffect } from '@nimbus/shared';
import type { Block, BlockType } from '@nimbus/shared';
import type { AppContext } from '../AppContext.js';
import { mergeBlockModifier, clearModifierCache } from './BlockModifierMerge';

const appContext = { worldInfo: {} } as unknown as AppContext;

const block = (): Block => ({ x: 0, y: 0, z: 0 }) as unknown as Block;

const blockType = (visibility: Record<string, unknown>): BlockType =>
  ({
    name: 'test_block',
    initialStatus: 'default',
    modifiers: { default: { visibility } },
  }) as unknown as BlockType;

const textureEffect = (type: BlockType): unknown => {
  const textures = mergeBlockModifier(appContext, block(), type).visibility?.textures as
    | Record<string, { effect?: unknown }>
    | undefined;
  return textures?.['0']?.effect;
};

describe('BlockModifierMerge effect normalization', () => {
  beforeEach(() => {
    clearModifierCache();
  });

  it('normalizes a texture-level effect name without a visibility-level effect', () => {
    // Migration 003 turned the stored 2 into "WIND"; bamboo carries it per texture
    const type = blockType({ textures: { '0': { path: 'bamboo.png', effect: 'WIND' } } });

    expect(textureEffect(type)).toBe(BlockEffect.WIND);
  });

  it('normalizes a texture-level numeric string', () => {
    const type = blockType({ textures: { '0': { path: 'bamboo.png', effect: '2' } } });

    expect(textureEffect(type)).toBe(BlockEffect.WIND);
  });

  it('normalizes a visibility-level effect name and passes it down to textures', () => {
    const type = blockType({
      effect: 'UNDULATION',
      textures: { '0': { path: 'seaweed.png' } },
    });

    const modifier = mergeBlockModifier(appContext, block(), type);

    expect(modifier.visibility?.effect).toBe(BlockEffect.UNDULATION);
    expect(textureEffect(type)).toBe(BlockEffect.UNDULATION);
  });

  it('keeps a texture-level effect when the visibility level carries a different one', () => {
    const type = blockType({
      effect: 'UNDULATION',
      textures: { '0': { path: 'bamboo.png', effect: 'WIND' } },
    });

    expect(textureEffect(type)).toBe(BlockEffect.WIND);
  });

  it('drops NONE and unknown names rather than passing them on', () => {
    expect(textureEffect(blockType({ textures: { '0': { path: 'x.png', effect: 'NONE' } } }))).toBeUndefined();
    expect(textureEffect(blockType({ textures: { '0': { path: 'x.png', effect: 'NOPE' } } }))).toBeUndefined();
  });
});
