/**
 * BlockModifierMerge - Merge block modifiers according to priority rules
 *
 * Merge priority (first defined field wins):
 * 1. Block.modifiers[status] (Instance-specific modifier)
 * 2. BlockType.modifiers[status] (Type-defined modifier for status)
 * 3. BlockType.modifiers[0] (Default status fallback)
 * 4. Default values (e.g., shape = 0)
 *
 * Each field (visibility, physics, wind, etc.) is merged independently.
 * If a field is undefined in a higher priority modifier, it falls back to lower priority.
 */
import {BlockModifier, BlockType, Block, BlockStatus, SeasonStatus, Vector3} from '@nimbus/shared';
import type { AppContext } from '../AppContext.js';

/**
 * Deep merge two objects field by field
 * Only merges defined fields (skips undefined)
 *
 * @param target Base object (lower priority)
 * @param source Override object (higher priority)
 * @returns Merged object
 */
function mergeObjects<T extends Record<string, any>>(target: T | undefined, source: T | undefined): T {
  if (!source) {
    return (target || {}) as T;
  }
  if (!target) {
    return source;
  }

  const result = { ...target };
  for (const key in source) {
    if (source[key] !== undefined) {
      result[key] = source[key];
    }
  }
  return result;
}

/**
 * Merge visibility modifiers with special handling for textures
 * Textures are merged by texture key (TextureKey enum values)
 *
 * Special effect/effectParameters handling:
 * - VisibilityModifier.effect is the default for all textures
 * - If a TextureDefinition doesn't have effect set, it inherits from VisibilityModifier
 * - If a TextureDefinition is a string, it's converted to an object with effect from VisibilityModifier
 *
 * @param target Base visibility modifier
 * @param source Override visibility modifier
 * @returns Merged visibility modifier
 */
function mergeVisibility(target: any, source: any): any {
  if (!source) {
    return target;
  }
  if (!target) {
    return source;
  }

  const result = { ...target };

  // Merge all fields except textures
  for (const key in source) {
    if (key === 'textures') {
      continue; // Handle textures separately
    }
    if (source[key] !== undefined) {
      result[key] = source[key];
    }
  }

  // Special handling for textures: merge texture records by key
  if (source.textures !== undefined) {
    result.textures = {
      ...(target.textures || {}),
      ...(source.textures || {}),
    };
  }

  // Apply VisibilityModifier.effect and effectParameters to textures
  if (result.textures && (result.effect !== undefined || result.effectParameters !== undefined)) {
    const defaultEffect = result.effect;
    const defaultEffectParameters = result.effectParameters;

    for (const key in result.textures) {
      let texture = result.textures[key];

      // If texture is a string and we have default effect/effectParameters, convert to object
      if (typeof texture === 'string' && (defaultEffect !== undefined || defaultEffectParameters !== undefined)) {
        texture = { path: texture };
        result.textures[key] = texture;
      }

      // If texture is an object, apply defaults if not set
      if (typeof texture === 'object' && texture !== null) {
        if (defaultEffect !== undefined && texture.effect === undefined) {
          texture.effect = defaultEffect;
        }
        if (defaultEffectParameters !== undefined && texture.effectParameters === undefined) {
          texture.effectParameters = defaultEffectParameters;
        }
      }
    }
  }

  return result;
}

/**
 * Deep merge two BlockModifier objects
 * Fields from source override fields in target only if they are not undefined
 *
 * @param target Base modifier (lower priority)
 * @param source Override modifier (higher priority)
 * @returns Merged BlockModifier
 */
function deepMergeModifiers(target: BlockModifier | undefined, source: BlockModifier | undefined): BlockModifier {
  // If source is undefined, return target or empty object
  if (!source) {
    return target || {};
  }

  // If target is undefined, return source
  if (!target) {
    return source;
  }

  // Merge each top-level field independently with deep merge
  return {
    visibility: source.visibility !== undefined
      ? mergeVisibility(target.visibility, source.visibility)
      : target.visibility,
    wind: source.wind !== undefined
      ? mergeObjects(target.wind, source.wind)
      : target.wind,
    illumination: source.illumination !== undefined
      ? mergeObjects(target.illumination, source.illumination)
      : target.illumination,
    physics: source.physics !== undefined
      ? mergeObjects(target.physics, source.physics)
      : target.physics,
    effects: source.effects !== undefined
      ? mergeObjects(target.effects, source.effects)
      : target.effects,
    audio: source.audio !== undefined
      ? mergeObjects(target.audio, source.audio)
      : target.audio,
  };
}

// Cache for merged modifiers: key = "blockTypeId:status", value = merged modifier
// Only used when block has NO instance-specific modifiers
const modifierCache = new Map<string, BlockModifier>();

/**
 * Merge BlockModifier according to priority rules
 *
 * Each field (visibility, physics, etc.) is merged independently.
 * Higher priority modifiers override lower priority only for defined fields.
 *
 * @param appContext Application context (used for world status override)
 * @param block Block instance
 * @param blockType BlockType definition
 * @param overwriteStatus Optional explicit status override
 * @returns Merged BlockModifier
 */
export function mergeBlockModifier(
  appContext: AppContext,
  block: Block,
  blockType: BlockType,
  overwriteStatus?: number
): BlockModifier {
  // Status is determined from BlockType.initialStatus (default: 0)
  const status = calculateStatus(appContext, block, blockType, overwriteStatus);

  // Check if block has instance-specific modifiers
  const hasInstanceModifiers = block.modifiers && Object.keys(block.modifiers).length > 0;

  // If no instance modifiers, check cache
  if (!hasInstanceModifiers) {
    const cacheKey = `${blockType.id}:${status}`;
    const cached = modifierCache.get(cacheKey);
    if (cached) {
      return cached; // Return cached result
    }

    // Not in cache - compute and cache
    const result = performModifierMerge(blockType, status, null);
    modifierCache.set(cacheKey, result);

    // overwrite from currentModifier to block if set
    // IMPORTANT: Only overwrite if block value is not already set (block has priority over modifier)
    if (result.visibility?.offsets && result.visibility.offsets.length > 0 && !block.offsets) {
      block.offsets = result.visibility.offsets;
    }
    if (result.visibility?.rotation && !block.rotation) {
      block.rotation = result.visibility.rotation;
    }

    return result;
  }

  // Block has custom modifiers - must merge each time (cannot cache)
  const result = performModifierMerge(blockType, status, block.modifiers ?? null);

  // overwrite from currentModifier to block if set
  // IMPORTANT: Only overwrite if block value is not already set (block has priority over modifier)
  if (result.visibility?.offsets && result.visibility.offsets.length > 0 && !block.offsets) {
    block.offsets = result.visibility.offsets;
  }
  if (result.visibility?.rotation && !block.rotation) {
    block.rotation = result.visibility.rotation;
  }

  return result;
}

/**
 * Perform the actual modifier merge
 * Separated from mergeBlockModifier to enable caching
 */
function performModifierMerge(
  blockType: BlockType,
  status: number,
  blockModifiers: { [status: number]: BlockModifier } | null
): BlockModifier {
  // Start with default modifier
  let result: BlockModifier = {
    visibility: {
      shape: 0, // Default shape
      textures: {},
    },
  };

  // Priority 3: Merge BlockType default status (0) as base
  if (blockType.modifiers[0]) {
    result = deepMergeModifiers(result, blockType.modifiers[0]);
  }

  // Priority 2: Merge BlockType status-specific modifiers
  if (status !== 0 && blockType.modifiers[status]) {
    result = deepMergeModifiers(result, blockType.modifiers[status]);
  }

  // Priority 1: Merge Block instance modifiers (highest priority)
  if (blockModifiers && blockModifiers[status]) {
    result = deepMergeModifiers(result, blockModifiers[status]);
  }

  return result;
}

/**
 * Calculate the effective status for a block.
 *
 * Resolution order:
 * 1. overwriteStatus (if provided)
 * 2. block.status (if defined)
 * 3. blockType.initialStatus (fallback to 0)
 * 4. World status override: applies only if resolved status is 0 and a modifier
 *    for worldStatus exists on block instance or block type.
 *
 * @param appContext Application context containing worldInfo
 * @param block Block instance
 * @param blockType BlockType definition
 * @param overwriteStatus Optional explicit status override
 * @returns Final resolved status number
 */
export function calculateStatus(
  appContext: AppContext,
  block: Block,
  blockType: BlockType,
  overwriteStatus?: number
): number {
  // TODO: seasonalStatus if seasonal status is defined in block modifier

  let newStatus: number;
  // Use overwriteStatus if provided
  if (overwriteStatus !== undefined) {
    newStatus = overwriteStatus;
  } else if (block.status !== undefined) {
    // Use block's own status if defined
    newStatus = block.status;
  } else {
    // Fallback to BlockType's initialStatus or default to 0
    newStatus = blockType.initialStatus ?? 0;
  }

  // World status override: if current status is 0 (default) and world status is non-zero
  // and a modifier exists for that world status either on block instance or block type.
  const worldStatus = appContext.worldInfo?.status;
  if (
    newStatus === 0 &&
    worldStatus !== undefined &&
    worldStatus !== 0 &&
    (block.modifiers?.[worldStatus] !== undefined || blockType.modifiers[worldStatus] !== undefined)
  ) {
    return worldStatus; // world status takes precedence
  }

  const hasWinterStatus = (block.modifiers?.[BlockStatus.WINTER] !== undefined) || (blockType.modifiers[BlockStatus.WINTER] !== undefined);
  const hasSpringStatus = (block.modifiers?.[BlockStatus.SPRING] !== undefined) || (blockType.modifiers[BlockStatus.SPRING] !== undefined);
  const hasSummerStatus = (block.modifiers?.[BlockStatus.SUMMER] !== undefined) || (blockType.modifiers[BlockStatus.SUMMER] !== undefined);
  const hasAutumnStatus = (block.modifiers?.[BlockStatus.AUTUMN] !== undefined) || (blockType.modifiers[BlockStatus.AUTUMN] !== undefined);

  if (hasWinterStatus || hasSpringStatus || hasSummerStatus || hasAutumnStatus) {
    const seasonalStatus = appContext.worldInfo?.seasonStatus;
    const seasonalProgress = appContext.worldInfo?.seasonProgress;

    if (seasonalStatus !== undefined && seasonalProgress !== undefined) {
      // Map seasonalStatus and seasonalProgress to specific seasonal BlockStatus
      const rememberStatus = newStatus;
      switch (seasonalStatus) {
        case SeasonStatus.WINTER:
          newStatus = switchSeason(seasonalProgress, block.position) ? BlockStatus.WINTER : BlockStatus.AUTUMN;
          break;
        case SeasonStatus.SPRING:
          newStatus = switchSeason(seasonalProgress, block.position) ? BlockStatus.SPRING : BlockStatus.WINTER;
          break;
        case SeasonStatus.SUMMER:
          newStatus = switchSeason(seasonalProgress, block.position) ? BlockStatus.SUMMER : BlockStatus.SPRING;
          break;
        case SeasonStatus.AUTUMN:
          newStatus = switchSeason(seasonalProgress, block.position) ? BlockStatus.AUTUMN : BlockStatus.SUMMER;
          break;
        default:
          // do not touch status
      }
      if (
          newStatus === BlockStatus.WINTER && !hasWinterStatus ||
          newStatus === BlockStatus.SPRING && !hasSpringStatus ||
          newStatus === BlockStatus.SUMMER && !hasSummerStatus ||
          newStatus === BlockStatus.AUTUMN && !hasAutumnStatus
      ){
        newStatus = rememberStatus;
      }
    }
  }
  return newStatus;
}

/*
Adjusted switchSeason to slow mid progress: added SEASON_PROGRESS_CURVE_POWER = 2.2 and compare hash threshold against eased = progress^power. At progress 0.5 only about 0.5^2.2 ≈ 0.22 of blocks switch now. You can tune the power:
Higher (>2.2) = even slower early fill
Lower (>1) = faster If you prefer another curve (e.g. smoothstep, logistic) say so. Done.
 */
const SEASON_PROGRESS_CURVE_POWER = 2.2; // >1 slows early fill (p=0.5 -> ~0.5^power fraction)

export function switchSeason(
    progress: number,
    position: Vector3
): boolean {
  // Clamp progress to [0,1]
  if (progress <= 0) return false;
  if (progress >= 1) return true;
  const p = progress; // already clamped

  // Apply easing to slow early adoption (power > 1 => ease-in)
  const eased = Math.pow(p, SEASON_PROGRESS_CURVE_POWER);

  // Deterministic pseudo-random in [0,1) based on integer-ish position.
  const x = position.x;
  const y = position.y;
  const z = position.z;
  const xi = Math.floor(x * 1000);
  const yi = Math.floor(y * 1000);
  const zi = Math.floor(z * 1000);

  let n = xi * 374761393 + yi * 668265263 + zi * 2147483647;
  n = (n ^ (n >> 13)) >>> 0;
  n = (n * 1274126177) >>> 0;
  const rand = (n & 0xffffffff) / 0xffffffff;

  // Position switches when eased progress surpasses its threshold.
  return eased >= rand;
}

/**
 * Get block position key for Map lookup
 *
 * @param x World X coordinate
 * @param y World Y coordinate
 * @param z World Z coordinate
 * @returns Position key string
 */
export function getBlockPositionKey(x: number, y: number, z: number): string {
  return `${x},${y},${z}`;
}
