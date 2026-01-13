/**
 * Vector3Color - 3D position with color information
 *
 * Used for model selector elements where each position needs an associated color.
 */

export interface Vector3Color {
  x: number;
  y: number;
  z: number;
  color: string; // Hex color string (e.g., '#ff0000')
}

/**
 * Vector3Color utility functions
 */
export namespace Vector3ColorUtils {
  /**
   * Create a new Vector3Color
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @param color Hex color string
   * @returns New Vector3Color
   */
  export function create(x: number, y: number, z: number, color: string): Vector3Color {
    return { x, y, z, color };
  }

  /**
   * Clone a Vector3Color
   * @param v Vector3Color to clone
   * @returns New Vector3Color with same values
   */
  export function clone(v: Vector3Color): Vector3Color {
    return { x: v.x, y: v.y, z: v.z, color: v.color };
  }

  /**
   * Convert to string representation
   * @param v Vector3Color
   * @returns String representation
   */
  export function toString(v: Vector3Color): string {
    return `Vector3Color(${v.x}, ${v.y}, ${v.z}, ${v.color})`;
  }

  /**
   * Check if two Vector3Color instances are equal
   * @param a First vector
   * @param b Second vector
   * @returns True if equal
   */
  export function equals(a: Vector3Color, b: Vector3Color): boolean {
    return a.x === b.x && a.y === b.y && a.z === b.z && a.color === b.color;
  }

  /**
   * Check if position matches (ignoring color)
   * @param a First vector
   * @param b Second vector
   * @returns True if position matches
   */
  export function positionEquals(a: Vector3Color, b: Vector3Color): boolean {
    return a.x === b.x && a.y === b.y && a.z === b.z;
  }
}
