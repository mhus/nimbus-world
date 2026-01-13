/**
 * AnimationHelpers - Helper functions for creating animation step functions
 */

import type { AnimationStepFunction } from './ModifierService';

/**
 * Create a linear interpolation step function for numbers
 * @param speed The speed of animation (value change per step, default: 0.1)
 * @returns Animation step function for numbers
 */
export function createLinearNumberAnimation(speed: number = 0.1): AnimationStepFunction<number> {
  return (current: number, target: number): [number, boolean] => {
    const diff = target - current;
    const absDiff = Math.abs(diff);

    // If we're close enough, jump to target
    if (absDiff < speed * 0.5) {
      return [target, false];
    }

    // Move towards target
    const step = Math.sign(diff) * Math.min(speed, absDiff);
    const nextValue = current + step;

    return [nextValue, true];
  };
}

/**
 * Create an exponential interpolation step function for numbers (smooth easing)
 * @param factor The easing factor (0-1, higher = faster, default: 0.1)
 * @returns Animation step function for numbers with exponential easing
 */
export function createExponentialNumberAnimation(factor: number = 0.1): AnimationStepFunction<number> {
  return (current: number, target: number): [number, boolean] => {
    const diff = target - current;
    const absDiff = Math.abs(diff);

    // If we're very close, jump to target
    if (absDiff < 0.001) {
      return [target, false];
    }

    // Exponential interpolation
    const nextValue = current + diff * factor;

    return [nextValue, true];
  };
}

/**
 * Create a stepped interpolation function for numbers (discrete steps)
 * @param stepSize The size of each step
 * @returns Animation step function for numbers with discrete steps
 */
export function createSteppedNumberAnimation(stepSize: number = 1): AnimationStepFunction<number> {
  return (current: number, target: number): [number, boolean] => {
    const diff = target - current;
    const absDiff = Math.abs(diff);

    // If we're within one step, jump to target
    if (absDiff < stepSize) {
      return [target, false];
    }

    // Move in discrete steps
    const step = Math.sign(diff) * stepSize;
    const nextValue = current + step;

    return [nextValue, true];
  };
}

/**
 * Create an animation step function for boolean values (instant change)
 * @returns Animation step function for booleans
 */
export function createBooleanAnimation(): AnimationStepFunction<boolean> {
  return (current: boolean, target: boolean): [boolean, boolean] => {
    // Booleans change instantly
    return [target, false];
  };
}

/**
 * Create an animation step function for string values (instant change)
 * @returns Animation step function for strings
 */
export function createStringAnimation(): AnimationStepFunction<string> {
  return (current: string, target: string): [string, boolean] => {
    // Strings change instantly
    return [target, false];
  };
}

/**
 * Create a custom animation step function with threshold
 * @param stepFn Custom step function
 * @param threshold Threshold for considering animation complete (default: 0.001)
 * @returns Animation step function with threshold
 */
export function createThresholdAnimation<T extends number>(
  stepFn: (current: T, target: T) => T,
  threshold: number = 0.001
): AnimationStepFunction<T> {
  return (current: T, target: T): [T, boolean] => {
    const diff = Math.abs(target - current);

    if (diff < threshold) {
      return [target, false];
    }

    const nextValue = stepFn(current, target);
    return [nextValue, true];
  };
}
