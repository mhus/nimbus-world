/**
 * ModifierService Usage Example
 *
 * This example demonstrates how to use the ModifierService to manage
 * values that can be overridden by multiple sources with different priorities.
 */

import type { ModifierService } from './ModifierService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ModifierService.example');
import type { EnvironmentService } from './EnvironmentService';

/**
 * Example: Managing wind force with modifiers
 *
 * In this example, we create a modifier stack for wind force that can be
 * influenced by multiple sources (weather, effects, etc.) with different priorities.
 */
export function setupWindForceModifiers(
  modifierService: ModifierService,
  environmentService: EnvironmentService
): void {
  // Create a modifier stack for wind force
  // Default value is 0 (no wind)
  // The action sets the wind strength in the environment service
  modifierService.createModifierStack<number>(
    'windForce',
    0,
    (newValue: number) => {
      environmentService.setWindStrength(newValue);
    }
  );

  // Example 1: Weather system adds wind
  const windModifierByWeather = modifierService.addModifier<number>('windForce', {
    value: 0.3, // 30% wind strength
    prio: 1000, // Lower priority
  });
  // Result: windForce = 0.3

  // Example 2: Special effect (storm spell) adds stronger wind
  const windModifierByEffect = modifierService.addModifier<number>('windForce', {
    value: 0.8, // 80% wind strength
    prio: 100, // Higher priority (lower value = higher priority)
  });
  // Result: windForce = 0.8 (effect overrides weather)

  // Example 3: Update weather wind (but effect still wins)
  windModifierByWeather.setValue(0.5);
  // Result: windForce = 0.8 (still using effect value)

  // Example 4: Effect ends
  windModifierByEffect.close();
  // Result: windForce = 0.5 (falls back to weather value)

  // Example 5: Weather calms down
  windModifierByWeather.setValue(0.1);
  // Result: windForce = 0.1

  // Example 6: All modifiers removed
  windModifierByWeather.close();
  // Result: windForce = 0 (default value)
}

/**
 * Example: Managing player movement speed with modifiers
 *
 * Different factors can affect player speed: terrain, buffs, debuffs, etc.
 */
export function setupMovementSpeedModifiers(
  modifierService: ModifierService,
  onSpeedChanged: (speed: number) => void
): void {
  // Base movement speed: 1.0
  modifierService.createModifierStack<number>('playerSpeed', 1.0, onSpeedChanged);

  // Terrain modifier (e.g., water slows down)
  const terrainModifier = modifierService.addModifier<number>('playerSpeed', {
    value: 0.5, // 50% speed in water
    prio: 500,
  });

  // Buff modifier (e.g., speed potion)
  const buffModifier = modifierService.addModifier<number>('playerSpeed', {
    value: 1.5, // 150% speed
    prio: 100, // Higher priority than terrain
  });
  // Result: playerSpeed = 1.5 (buff wins)

  // When buff expires
  buffModifier.close();
  // Result: playerSpeed = 0.5 (terrain modifier active)

  // When leaving water
  terrainModifier.close();
  // Result: playerSpeed = 1.0 (default speed)
}

/**
 * Example: Managing boolean flags with modifiers
 *
 * Some game mechanics might need boolean flags that can be overridden.
 */
export function setupBooleanFlagModifiers(
  modifierService: ModifierService,
  onFlagChanged: (enabled: boolean) => void
): void {
  // Default: flying is disabled
  modifierService.createModifierStack<boolean>('canFly', false, onFlagChanged);

  // Creative mode enables flying
  const creativeModeModifier = modifierService.addModifier<boolean>('canFly', {
    value: true,
    prio: 100,
  });
  // Result: canFly = true

  // Special item also enables flying (lower priority)
  const flyingItemModifier = modifierService.addModifier<boolean>('canFly', {
    value: true,
    prio: 200,
  });
  // Result: canFly = true (creative mode wins, but both would enable it)

  // Exit creative mode
  creativeModeModifier.close();
  // Result: canFly = true (flying item still active)

  // Remove flying item
  flyingItemModifier.close();
  // Result: canFly = false (default)
}

/**
 * Example: Managing complex objects with modifiers
 *
 * Modifiers can also work with complex objects, not just primitives.
 */
export interface CameraSettings {
  fov: number;
  minDistance: number;
  maxDistance: number;
}

export function setupCameraModifiers(
  modifierService: ModifierService,
  onCameraSettingsChanged: (settings: CameraSettings) => void
): void {
  // Default camera settings
  const defaultSettings: CameraSettings = {
    fov: 75,
    minDistance: 2,
    maxDistance: 50,
  };

  modifierService.createModifierStack<CameraSettings>(
    'cameraSettings',
    defaultSettings,
    onCameraSettingsChanged
  );

  // Binoculars modifier
  const binocularsModifier = modifierService.addModifier<CameraSettings>('cameraSettings', {
    value: {
      fov: 30, // Zoomed in
      minDistance: 10,
      maxDistance: 200,
    },
    prio: 100,
  });
  // Result: camera uses binoculars settings

  // When binoculars are put away
  binocularsModifier.close();
  // Result: camera returns to default settings
}

/**
 * Example: Priority collision handling
 *
 * When multiple modifiers have the same priority, the newest one wins.
 */
export function setupPriorityExamples(modifierService: ModifierService): void {
  modifierService.createModifierStack<string>('message', 'default', (msg) => {
    logger.debug('Message:', msg);
  });

  // Add multiple modifiers with same priority
  const mod1 = modifierService.addModifier('message', {
    value: 'First',
    prio: 100,
  });
  // Result: message = "First"

  const mod2 = modifierService.addModifier('message', {
    value: 'Second',
    prio: 100, // Same priority
  });
  // Result: message = "Second" (newer wins)

  const mod3 = modifierService.addModifier('message', {
    value: 'Third',
    prio: 100, // Same priority
  });
  // Result: message = "Third" (newest wins)

  // Remove the newest
  mod3.close();
  // Result: message = "Second"

  // Remove the middle one
  mod2.close();
  // Result: message = "First"

  // Remove the last one
  mod1.close();
  // Result: message = "default"
}

/**
 * Example: Managing multiple stacks
 *
 * You can create multiple independent modifier stacks.
 */
export function setupMultipleStacks(modifierService: ModifierService): void {
  // Stack 1: Light intensity
  modifierService.createModifierStack<number>('lightIntensity', 1.0, (intensity) => {
    logger.debug('Light intensity:', intensity);
  });

  // Stack 2: Fog density
  modifierService.createModifierStack<number>('fogDensity', 0.0, (density) => {
    logger.debug('Fog density:', density);
  });

  // Stack 3: Rain strength
  modifierService.createModifierStack<number>('rainStrength', 0.0, (strength) => {
    logger.debug('Rain strength:', strength);
  });

  // Add modifiers to different stacks independently
  modifierService.addModifier('lightIntensity', { value: 0.5, prio: 100 });
  modifierService.addModifier('fogDensity', { value: 0.8, prio: 100 });
  modifierService.addModifier('rainStrength', { value: 0.6, prio: 100 });

  // Each stack maintains its own state and priority ordering
}

/**
 * Example: Cleanup
 *
 * Always clean up when done to avoid memory leaks.
 */
export function cleanupExample(modifierService: ModifierService): void {
  // Remove a specific stack
  modifierService.removeStack('windForce');

  // Or dispose all stacks when shutting down
  modifierService.dispose();
}
