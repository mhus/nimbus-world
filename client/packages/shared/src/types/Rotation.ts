/**
 * Rotation - 3D rotation representation
 */

/**
 * Rotation (yaw, pitch, roll)
 */
export interface Rotation {
  /** Yaw - horizontal rotation (degrees) */
  y: number;

  /** Pitch - vertical rotation (degrees) */
  p: number;

  /** Roll - tilt rotation (degrees, optional) */
  r?: number;
}

/**
 * Rotation utility functions
 */
export namespace RotationUtils {
  /**
   * Create a new Rotation
   * @param y Yaw (horizontal)
   * @param p Pitch (vertical)
   * @param r Roll (optional)
   * @returns New Rotation
   */
  export function create(y: number, p: number, r?: number): Rotation {
    return r !== undefined ? { y, p, r } : { y, p };
  }

  /**
   * Create a zero rotation (0, 0, 0)
   * @returns Zero rotation
   */
  export function zero(): Rotation {
    return { y: 0, p: 0, r: 0 };
  }

  /**
   * Clone rotation
   * @param rotation Rotation to clone
   * @returns New rotation with same values
   */
  export function clone(rotation: Rotation): Rotation {
    return { ...rotation };
  }

  /**
   * Check if two rotations are equal
   * @param a First rotation
   * @param b Second rotation
   * @param epsilon Epsilon for float comparison (default: 0.01)
   * @returns True if equal
   */
  export function equals(
    a: Rotation,
    b: Rotation,
    epsilon: number = 0.01
  ): boolean {
    return (
      Math.abs(a.y - b.y) < epsilon &&
      Math.abs(a.p - b.p) < epsilon &&
      Math.abs((a.r ?? 0) - (b.r ?? 0)) < epsilon
    );
  }

  /**
   * Normalize rotation angles to 0-360 range
   * @param rotation Rotation to normalize
   * @returns Normalized rotation
   */
  export function normalize(rotation: Rotation): Rotation {
    return {
      y: ((rotation.y % 360) + 360) % 360,
      p: ((rotation.p % 360) + 360) % 360,
      r: rotation.r !== undefined ? ((rotation.r % 360) + 360) % 360 : undefined,
    };
  }

  /**
   * Clamp pitch to prevent camera flip
   * @param rotation Rotation to clamp
   * @param minPitch Minimum pitch (default: -89)
   * @param maxPitch Maximum pitch (default: 89)
   * @returns Clamped rotation
   */
  export function clampPitch(
    rotation: Rotation,
    minPitch: number = -89,
    maxPitch: number = 89
  ): Rotation {
    return {
      ...rotation,
      p: Math.max(minPitch, Math.min(maxPitch, rotation.p)),
    };
  }

  /**
   * Linearly interpolate between two rotations
   * @param a Start rotation
   * @param b End rotation
   * @param t Interpolation factor (0-1)
   * @returns Interpolated rotation
   */
  export function lerp(a: Rotation, b: Rotation, t: number): Rotation {
    return {
      y: a.y + (b.y - a.y) * t,
      p: a.p + (b.p - a.p) * t,
      r:
        a.r !== undefined && b.r !== undefined
          ? a.r + (b.r - a.r) * t
          : undefined,
    };
  }

  /**
   * Convert rotation to radians
   * @param rotation Rotation in degrees
   * @returns Rotation in radians
   */
  export function toRadians(rotation: Rotation): Rotation {
    return {
      y: (rotation.y * Math.PI) / 180,
      p: (rotation.p * Math.PI) / 180,
      r: rotation.r !== undefined ? (rotation.r * Math.PI) / 180 : undefined,
    };
  }

  /**
   * Convert rotation to degrees (from radians)
   * @param rotation Rotation in radians
   * @returns Rotation in degrees
   */
  export function toDegrees(rotation: Rotation): Rotation {
    return {
      y: (rotation.y * 180) / Math.PI,
      p: (rotation.p * 180) / Math.PI,
      r: rotation.r !== undefined ? (rotation.r * 180) / Math.PI : undefined,
    };
  }

  /**
   * Get forward direction vector from rotation
   * @param rotation Rotation
   * @returns Forward direction vector (normalized)
   */
  export function getForwardVector(rotation: Rotation): Vector3 {
    const yawRad = (rotation.y * Math.PI) / 180;
    const pitchRad = (rotation.p * Math.PI) / 180;

    return {
      x: Math.sin(yawRad) * Math.cos(pitchRad),
      y: -Math.sin(pitchRad),
      z: Math.cos(yawRad) * Math.cos(pitchRad),
    };
  }

  /**
   * Get right direction vector from rotation
   * @param rotation Rotation
   * @returns Right direction vector (normalized)
   */
  export function getRightVector(rotation: Rotation): Vector3 {
    const yawRad = (rotation.y * Math.PI) / 180;

    return {
      x: Math.cos(yawRad),
      y: 0,
      z: -Math.sin(yawRad),
    };
  }

  /**
   * Convert to string representation for debugging
   * @param rotation Rotation
   * @returns String representation
   */
  export function toString(rotation: Rotation): string {
    if (rotation.r !== undefined) {
      return `Rotation(y:${rotation.y.toFixed(1)}°, p:${rotation.p.toFixed(1)}°, r:${rotation.r.toFixed(1)}°)`;
    }
    return `Rotation(y:${rotation.y.toFixed(1)}°, p:${rotation.p.toFixed(1)}°)`;
  }
}

// Re-export Vector3 for convenience
import type { Vector3 } from './Vector3';
