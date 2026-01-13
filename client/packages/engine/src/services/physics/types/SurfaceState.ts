/**
 * SurfaceState - Surface analysis result
 *
 * @deprecated Reserved for future surface-based physics
 * Currently not used - information is available in PlayerBlockContext instead.
 *
 * Contains all surface properties at current entity position.
 */

import type { PlayerBlockContext } from './BlockContext';

/**
 * Surface state analysis result
 * @deprecated Not used in current implementation
 */
export interface SurfaceState {
  /** Type of surface player is on/in */
  type: 'flat' | 'slope' | 'none' | 'climbing';

  /** Interpolated surface height at current position */
  surfaceHeight: number;

  /** Corner heights if surface is a slope */
  cornerHeights?: [number, number, number, number];

  /** Slope vector (0 if flat) */
  slope: { x: number; z: number };

  /** Movement resistance (0 = no resistance, 1 = full resistance) */
  resistance: number;

  /** Can player walk on this surface? */
  canWalkOn: boolean;

  /** Is surface solid? */
  isSolid: boolean;

  /** Block context (cached) */
  context: PlayerBlockContext;
}
