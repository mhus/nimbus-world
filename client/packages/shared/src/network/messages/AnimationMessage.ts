/**
 * Animation-related messages
 */

import type { BaseMessage } from '../BaseMessage';
import type { AnimationData } from '../../types/AnimationData';

/**
 * Animation start data
 */
export interface AnimationStartData {
  /** Block X position */
  x: number;

  /** Block Y position */
  y: number;

  /** Block Z position */
  z: number;

  /** Animation to execute */
  animation: AnimationData;
}

/**
 * Animation start (Server -> Client)
 * Server sends instruction to start animation
 */
export type AnimationStartMessage = BaseMessage<AnimationStartData[]>;
