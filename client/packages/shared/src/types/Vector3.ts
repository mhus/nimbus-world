/**
 * Vector3 - 3D position/offset
 */

export interface Vector3 {
  x: number;
  y: number;
  z: number;
}

/**
 * Vector3 utility functions
 */
export namespace Vector3Utils {
  /**
   * Create a new Vector3
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @returns New Vector3
   */
  export function create(x: number, y: number, z: number): Vector3 {
    return { x, y, z };
  }

  /**
   * Create a zero vector (0, 0, 0)
   * @returns Zero vector
   */
  export function zero(): Vector3 {
    return { x: 0, y: 0, z: 0 };
  }

  /**
   * Create a unit vector (1, 1, 1)
   * @returns Unit vector
   */
  export function one(): Vector3 {
    return { x: 1, y: 1, z: 1 };
  }

  /**
   * Add two vectors
   * @param a First vector
   * @param b Second vector
   * @returns Result vector (a + b)
   */
  export function add(a: Vector3, b: Vector3): Vector3 {
    return {
      x: a.x + b.x,
      y: a.y + b.y,
      z: a.z + b.z,
    };
  }

  /**
   * Subtract two vectors
   * @param a First vector
   * @param b Second vector
   * @returns Result vector (a - b)
   */
  export function subtract(a: Vector3, b: Vector3): Vector3 {
    return {
      x: a.x - b.x,
      y: a.y - b.y,
      z: a.z - b.z,
    };
  }

  /**
   * Multiply vector by scalar
   * @param v Vector
   * @param scalar Scalar value
   * @returns Scaled vector
   */
  export function multiply(v: Vector3, scalar: number): Vector3 {
    return {
      x: v.x * scalar,
      y: v.y * scalar,
      z: v.z * scalar,
    };
  }

  /**
   * Divide vector by scalar
   * @param v Vector
   * @param scalar Scalar value
   * @returns Scaled vector
   */
  export function divide(v: Vector3, scalar: number): Vector3 {
    return {
      x: v.x / scalar,
      y: v.y / scalar,
      z: v.z / scalar,
    };
  }

  /**
   * Calculate dot product of two vectors
   * @param a First vector
   * @param b Second vector
   * @returns Dot product
   */
  export function dot(a: Vector3, b: Vector3): number {
    return a.x * b.x + a.y * b.y + a.z * b.z;
  }

  /**
   * Calculate cross product of two vectors
   * @param a First vector
   * @param b Second vector
   * @returns Cross product vector
   */
  export function cross(a: Vector3, b: Vector3): Vector3 {
    return {
      x: a.y * b.z - a.z * b.y,
      y: a.z * b.x - a.x * b.z,
      z: a.x * b.y - a.y * b.x,
    };
  }

  /**
   * Calculate length (magnitude) of vector
   * @param v Vector
   * @returns Length
   */
  export function length(v: Vector3): number {
    return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
  }

  /**
   * Calculate squared length of vector (faster than length)
   * @param v Vector
   * @returns Squared length
   */
  export function lengthSquared(v: Vector3): number {
    return v.x * v.x + v.y * v.y + v.z * v.z;
  }

  /**
   * Calculate distance between two points
   * @param a First point
   * @param b Second point
   * @returns Distance
   */
  export function distance(a: Vector3, b: Vector3): number {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const dz = b.z - a.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Calculate squared distance between two points (faster than distance)
   * @param a First point
   * @param b Second point
   * @returns Squared distance
   */
  export function distanceSquared(a: Vector3, b: Vector3): number {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const dz = b.z - a.z;
    return dx * dx + dy * dy + dz * dz;
  }

  /**
   * Normalize vector to unit length
   * @param v Vector
   * @returns Normalized vector
   */
  export function normalize(v: Vector3): Vector3 {
    const len = length(v);
    if (len === 0) {
      return zero();
    }
    return divide(v, len);
  }

  /**
   * Check if two vectors are equal
   * @param a First vector
   * @param b Second vector
   * @param epsilon Epsilon for float comparison (default: 0.0001)
   * @returns True if equal
   */
  export function equals(
    a: Vector3,
    b: Vector3,
    epsilon: number = 0.0001
  ): boolean {
    return (
      Math.abs(a.x - b.x) < epsilon &&
      Math.abs(a.y - b.y) < epsilon &&
      Math.abs(a.z - b.z) < epsilon
    );
  }

  /**
   * Linearly interpolate between two vectors
   * @param a Start vector
   * @param b End vector
   * @param t Interpolation factor (0-1)
   * @returns Interpolated vector
   */
  export function lerp(a: Vector3, b: Vector3, t: number): Vector3 {
    return {
      x: a.x + (b.x - a.x) * t,
      y: a.y + (b.y - a.y) * t,
      z: a.z + (b.z - a.z) * t,
    };
  }

  /**
   * Clamp vector components between min and max
   * @param v Vector
   * @param min Minimum value
   * @param max Maximum value
   * @returns Clamped vector
   */
  export function clamp(v: Vector3, min: number, max: number): Vector3 {
    return {
      x: Math.max(min, Math.min(max, v.x)),
      y: Math.max(min, Math.min(max, v.y)),
      z: Math.max(min, Math.min(max, v.z)),
    };
  }

  /**
   * Floor vector components
   * @param v Vector
   * @returns Floored vector
   */
  export function floor(v: Vector3): Vector3 {
    return {
      x: Math.floor(v.x),
      y: Math.floor(v.y),
      z: Math.floor(v.z),
    };
  }

  /**
   * Ceil vector components
   * @param v Vector
   * @returns Ceiled vector
   */
  export function ceil(v: Vector3): Vector3 {
    return {
      x: Math.ceil(v.x),
      y: Math.ceil(v.y),
      z: Math.ceil(v.z),
    };
  }

  /**
   * Round vector components
   * @param v Vector
   * @returns Rounded vector
   */
  export function round(v: Vector3): Vector3 {
    return {
      x: Math.round(v.x),
      y: Math.round(v.y),
      z: Math.round(v.z),
    };
  }

  /**
   * Negate vector (multiply by -1)
   * @param v Vector
   * @returns Negated vector
   */
  export function negate(v: Vector3): Vector3 {
    return {
      x: -v.x,
      y: -v.y,
      z: -v.z,
    };
  }

  /**
   * Clone vector
   * @param v Vector to clone
   * @returns New vector with same values
   */
  export function clone(v: Vector3): Vector3 {
    return { x: v.x, y: v.y, z: v.z };
  }

  /**
   * Convert vector to string
   * @param v Vector
   * @returns String representation
   */
  export function toString(v: Vector3): string {
    return `Vector3(${v.x.toFixed(2)}, ${v.y.toFixed(2)}, ${v.z.toFixed(2)})`;
  }

  /**
   * Convert vector to array
   * @param v Vector
   * @returns Array [x, y, z]
   */
  export function toArray(v: Vector3): [number, number, number] {
    return [v.x, v.y, v.z];
  }

  /**
   * Create vector from array
   * @param arr Array [x, y, z]
   * @returns Vector3
   */
  export function fromArray(arr: number[]): Vector3 {
    return { x: arr[0] ?? 0, y: arr[1] ?? 0, z: arr[2] ?? 0 };
  }

  /**
   * Calculate Manhattan distance (taxicab distance)
   * @param a First point
   * @param b Second point
   * @returns Manhattan distance
   */
  export function manhattanDistance(a: Vector3, b: Vector3): number {
    return Math.abs(b.x - a.x) + Math.abs(b.y - a.y) + Math.abs(b.z - a.z);
  }

  /**
   * Check if vector is zero
   * @param v Vector
   * @param epsilon Epsilon for float comparison (default: 0.0001)
   * @returns True if zero
   */
  export function isZero(v: Vector3, epsilon: number = 0.0001): boolean {
    return (
      Math.abs(v.x) < epsilon &&
      Math.abs(v.y) < epsilon &&
      Math.abs(v.z) < epsilon
    );
  }

  /**
   * Get minimum of two vectors (component-wise)
   * @param a First vector
   * @param b Second vector
   * @returns Vector with minimum components
   */
  export function min(a: Vector3, b: Vector3): Vector3 {
    return {
      x: Math.min(a.x, b.x),
      y: Math.min(a.y, b.y),
      z: Math.min(a.z, b.z),
    };
  }

  /**
   * Get maximum of two vectors (component-wise)
   * @param a First vector
   * @param b Second vector
   * @returns Vector with maximum components
   */
  export function max(a: Vector3, b: Vector3): Vector3 {
    return {
      x: Math.max(a.x, b.x),
      y: Math.max(a.y, b.y),
      z: Math.max(a.z, b.z),
    };
  }
}
