/**
 * Utility functions for tracking defined state of optional properties.
 *
 * For optional boolean properties, we need to distinguish between:
 * - undefined (not set, use server default)
 * - true (explicitly enabled)
 * - false (explicitly disabled)
 *
 * This is achieved by adding _[property]Defined fields.
 */

/**
 * Add _defined tracking fields for all optional boolean properties in an object.
 * Recursively processes nested objects.
 *
 * @param obj Object to add _defined fields to
 * @param booleanProps List of property names that are optional booleans
 * @returns Object with _defined fields added
 */
export function addDefinedTracking<T extends Record<string, any>>(
  obj: T | undefined,
  booleanProps: string[]
): T {
  if (!obj) return {} as T;

  const result = { ...obj };

  // Add _defined field for each boolean property
  for (const prop of booleanProps) {
    const definedKey = `_${prop}Defined` as keyof T;
    const propKey = prop as keyof T;

    // Check if property exists and is not undefined
    const hasProperty = propKey in obj;
    const propertyValue = obj[propKey];
    const isPropertyDefined = hasProperty && propertyValue !== undefined;

    if (isPropertyDefined) {
      // Property is explicitly set (true or false)
      result[definedKey] = true as any;
      result[propKey] = propertyValue as any;
    } else {
      // Property is not set or undefined
      result[definedKey] = false as any;
      result[propKey] = false as any; // Default value for UI
    }
  }

  return result;
}

/**
 * Remove _defined tracking fields and restore original structure.
 * Properties with _defined=false will be set to undefined.
 *
 * @param obj Object with _defined fields
 * @param booleanProps List of property names that are optional booleans
 * @returns Clean object without _defined fields
 */
export function removeDefinedTracking<T extends Record<string, any>>(
  obj: T,
  booleanProps: string[]
): Partial<T> {
  const result = { ...obj };

  // Process each boolean property
  for (const prop of booleanProps) {
    const definedKey = `_${prop}Defined`;
    const propKey = prop as keyof T;

    // If _defined is false or missing, set property to undefined
    if (!result[definedKey]) {
      delete result[propKey];
    }

    // Remove the _defined field itself
    delete result[definedKey];
  }

  return result;
}

/**
 * Get the defined state of a boolean property.
 *
 * @param obj Object with _defined tracking
 * @param prop Property name
 * @returns true if defined, false if undefined
 */
export function isPropertyDefined(obj: any, prop: string): boolean {
  const definedKey = `_${prop}Defined`;
  return obj[definedKey] === true;
}

/**
 * Set a boolean property with defined tracking.
 *
 * @param obj Object with _defined tracking
 * @param prop Property name
 * @param value New value (undefined means not defined)
 */
export function setDefinedProperty(obj: any, prop: string, value: boolean | undefined): void {
  const definedKey = `_${prop}Defined`;

  if (value === undefined) {
    obj[definedKey] = false;
    obj[prop] = false; // Set to false but mark as undefined
  } else {
    obj[definedKey] = true;
    obj[prop] = value;
  }
}

/**
 * Get a boolean property value with defined tracking.
 * Returns undefined if not defined.
 *
 * @param obj Object with _defined tracking
 * @param prop Property name
 * @returns Boolean value or undefined
 */
export function getDefinedProperty(obj: any, prop: string): boolean | undefined {
  const definedKey = `_${prop}Defined`;

  if (!obj[definedKey]) {
    return undefined;
  }

  return obj[prop] as boolean;
}

/**
 * Add defined tracking to PhysicsModifier.
 */
export function addPhysicsDefinedTracking(physics: any): any {
  return addDefinedTracking(physics, [
    'solid',
    'interactive',
    'collisionEvent',
    'autoClimbable'
  ]);
}

/**
 * Remove defined tracking from PhysicsModifier.
 */
export function removePhysicsDefinedTracking(physics: any): any {
  const cleaned = removeDefinedTracking(physics, [
    'solid',
    'interactive',
    'collisionEvent',
    'autoClimbable'
  ]);

  // If all properties are undefined, return undefined
  if (Object.keys(cleaned).length === 0) {
    return undefined;
  }

  return cleaned;
}

/**
 * Add defined tracking to EffectsModifier.
 */
export function addEffectsDefinedTracking(effects: any): any {
  return addDefinedTracking(effects, [
    'forceEgoView'
  ]);
}

/**
 * Remove defined tracking from EffectsModifier.
 */
export function removeEffectsDefinedTracking(effects: any): any {
  const cleaned = removeDefinedTracking(effects, [
    'forceEgoView'
  ]);

  // If all properties are undefined, return undefined
  if (Object.keys(cleaned).length === 0) {
    return undefined;
  }

  return cleaned;
}

/**
 * Add defined tracking to entire BlockModifier.
 */
export function addBlockModifierDefinedTracking(modifier: any): any {
  const result = { ...modifier };

  if (result.physics) {
    result.physics = addPhysicsDefinedTracking(result.physics);
  }

  if (result.effects) {
    result.effects = addEffectsDefinedTracking(result.effects);
  }

  return result;
}

/**
 * Remove defined tracking from entire BlockModifier.
 */
export function removeBlockModifierDefinedTracking(modifier: any): any {
  const result = { ...modifier };

  if (result.physics) {
    result.physics = removePhysicsDefinedTracking(result.physics);
  }

  if (result.effects) {
    result.effects = removeEffectsDefinedTracking(result.effects);
  }

  return result;
}
