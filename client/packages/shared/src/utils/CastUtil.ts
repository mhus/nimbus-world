/**
 * CastUtil - Utility for type conversion
 *
 * Provides safe conversion functions for converting any type to string, number, or boolean.
 * Used primarily for converting script parameters to expected types.
 */

/**
 * Convert any value to boolean
 *
 * Rules:
 * - boolean: returned as-is
 * - string: 'true', '1', 'yes', 'on' -> true (case-insensitive)
 * - string: 'false', '0', 'no', 'off', '' -> false (case-insensitive)
 * - number: 0 -> false, all others -> true
 * - null/undefined -> false
 * - object/array -> true (truthy)
 *
 * @param value - Value to convert
 * @returns Boolean representation of the value
 */
export function toBoolean(value: any): boolean {
  // Handle null/undefined
  if (value === null || value === undefined) {
    return false;
  }

  // Handle boolean
  if (typeof value === 'boolean') {
    return value;
  }

  // Handle number
  if (typeof value === 'number') {
    return value !== 0;
  }

  // Handle string
  if (typeof value === 'string') {
    const lower = value.toLowerCase().trim();
    if (lower === 'true' || lower === '1' || lower === 'yes' || lower === 'on') {
      return true;
    }
    if (lower === 'false' || lower === '0' || lower === 'no' || lower === 'off' || lower === '') {
      return false;
    }
    // Non-empty strings are truthy
    return true;
  }

  // Everything else (objects, arrays, etc.) is truthy
  return true;
}

/**
 * Convert any value to string
 *
 * Rules:
 * - string: returned as-is
 * - number/boolean: converted with String()
 * - null -> 'null'
 * - undefined -> 'undefined'
 * - object/array -> JSON.stringify() if possible, otherwise String()
 *
 * @param value - Value to convert
 * @returns String representation of the value
 */
export function toString(value: any): string {
  // Handle null/undefined
  if (value === null) {
    return 'null';
  }
  if (value === undefined) {
    return 'undefined';
  }

  // Handle string
  if (typeof value === 'string') {
    return value;
  }

  // Handle primitives
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }

  // Handle objects/arrays - try JSON, fallback to String()
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/**
 * Convert any value to number
 *
 * Rules:
 * - number: returned as-is
 * - string: parsed with parseFloat()
 * - boolean: true -> 1, false -> 0
 * - null/undefined -> 0
 * - object/array -> NaN
 *
 * @param value - Value to convert
 * @returns Number representation of the value
 */
export function toNumber(value: any): number {
  // Handle null/undefined
  if (value === null || value === undefined) {
    return 0;
  }

  // Handle number
  if (typeof value === 'number') {
    return value;
  }

  // Handle boolean
  if (typeof value === 'boolean') {
    return value ? 1 : 0;
  }

  // Handle string
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') {
      return 0;
    }
    const parsed = parseFloat(trimmed);
    return isNaN(parsed) ? 0 : parsed;
  }

  // Everything else returns NaN
  return NaN;
}

/**
 * Convert any value to object
 *
 * Rules:
 * - object: returned as-is
 * - string: try JSON.parse(), fallback to empty object {}
 * - null/undefined -> empty object {}
 * - primitives (number, boolean) -> empty object {}
 * - array: returned as-is (arrays are objects in JS)
 *
 * @param value - Value to convert
 * @returns Object representation of the value
 */
export function toObject(value: any): any {
  // Handle null/undefined
  if (value === null || value === undefined) {
    return {};
  }

  // Handle object (including arrays)
  if (typeof value === 'object') {
    return value;
  }

  // Handle string - try JSON.parse()
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') {
      return {};
    }
    try {
      const parsed = JSON.parse(trimmed);
      // Only return if result is actually an object
      if (typeof parsed === 'object' && parsed !== null) {
        return parsed;
      }
      // If parsed to primitive, return empty object
      return {};
    } catch {
      // Invalid JSON, return empty object
      return {};
    }
  }

  // Primitives (number, boolean) return empty object
  return {};
}
