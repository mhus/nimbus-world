/**
 * ForceState - Accumulated forces
 *
 * @deprecated Reserved for future force-based physics implementation
 * Currently not used - system uses velocity-based approach instead.
 *
 * Contains all forces acting on an entity during physics simulation.
 */

/**
 * Accumulated forces acting on entity
 * @deprecated Not used in current implementation
 */
export interface ForceState {
  /** Gravity force (Y-axis) */
  gravity: { x: 0; y: number; z: 0 };

  /** Player input force (movement) */
  input: { x: number; y: number; z: number };

  /** Slope sliding force */
  slope: { x: number; y: number; z: number };

  /** AutoMove force (conveyors, currents) */
  autoMove: { x: number; y: number; z: number };

  /** Climbable force (ladders) */
  climb: { x: 0; y: number; z: 0 };

  /** Combined total force */
  total: { x: number; y: number; z: number };

  /** Should gravity be applied? */
  applyGravity: boolean;

  /** Is entity climbing? */
  isClimbing: boolean;
}
