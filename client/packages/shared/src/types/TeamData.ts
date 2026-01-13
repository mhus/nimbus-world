/**
 * Team Data Types
 *
 * Defines team structures and team member status
 */

import type { Vector3 } from './Vector3';

/**
 * Team Member Interface
 */
export interface TeamMember {
  /** Player ID */
  playerId: string;

  /** Display Name */
  name: string;

  /** Optional Avatar Icon Path */
  icon?: string;

  /** Member Status: 0=disconnected, 1=alive, 2=dead */
  status: 0 | 1 | 2;

  /** Optional Position */
  position?: Vector3;

  /** Optional Health (0-100) */
  health?: number; // javaType: int
}

/**
 * Team Data Interface
 */
export interface TeamData {
  /** Team Name */
  name: string;

  /** Team ID */
  id: string;

  /** Team Members */
  members: TeamMember[];
}

/**
 * Team Status Update Interface
 * Used for frequent status updates
 */
export interface TeamStatusUpdate {
  /** Team ID */
  id: string;

  /** Member Status Updates */
  ms: Array<{
    /** Player ID */
    id: string;

    /** Same World? */
    w?: boolean;

    /** Health Percent (optional) */
    h?: number; // javaType: int

    /** Position (optional) */
    po?: Vector3;

    /** Status Code (optional): 0=disconnected, 1=alive, 2=dead */
    st?: 0 | 1 | 2;
  }>;
}
