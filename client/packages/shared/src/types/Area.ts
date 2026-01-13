/**
 * AreaData - Defines an area with effects
 */

import type { Vector3Int } from './Vector3Int';

export interface Area {
  /** Start position */
  position: Vector3Int;

  /** End position */
  size: Vector3Int;

}
