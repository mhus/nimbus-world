/**
 * AreaData - Defines an area with effects
 */

import type { Vector3 } from './Vector3';
import type { EffectData } from './EffectData';

export interface AreaData {
  /** Start position */
  a: Vector3;

  /** End position */
  b: Vector3;

  /** Effects applied to this area */
  e: EffectData[];
}
