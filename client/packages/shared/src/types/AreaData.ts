/**
 * AreaData - Defines an area with effects
 */

import type { Vector3 } from './Vector3';

export interface AreaData {
  /** Start position */
  a: Vector3;

  /** End position */
  b: Vector3;

  /** Parameters */
  p: Record<string, string>;
}
