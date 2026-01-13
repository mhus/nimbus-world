/**
 * StackModifierCreator - Central initialization of all StackModifiers
 *
 * This module creates all StackModifier instances centrally at application startup.
 * Services should only retrieve stacks via ModifierService.getModifierStack().
 *
 * Benefits:
 * - All stacks are guaranteed to exist
 * - No scattered lazy initialization checks
 * - Central documentation of all stacks
 * - Type-safe stack names via StackName enum
 */

import { getLogger, PlayerMovementState } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { StackName, createLinearNumberAnimation } from './ModifierService';

const logger = getLogger('StackModifierCreator');

/**
 * Creates all StackModifiers centrally
 *
 * Called once during application initialization in NimbusClient.ts
 * after ModifierService has been created.
 *
 * Note: Action callbacks may fail initially if referenced services
 * are not yet available. This is acceptable - ModifierStack.update()
 * handles errors gracefully with try-catch.
 *
 * @param appContext The application context
 */
export function createAllStackModifiers(appContext: AppContext): void {
  const modifierService = appContext.services.modifier;
  if (!modifierService) {
    logger.error('ModifierService not available - cannot create stacks');
    return;
  }

  logger.debug('Creating all StackModifiers...');

  // ========================================
  // Player View Mode Stack
  // ========================================
  modifierService.createModifierStack<boolean>(
    StackName.PLAYER_VIEW_MODE,
    true, // Default: ego-view (first-person)
    (isEgo: boolean) => {
      // Callback may fail if PlayerService not ready yet - that's ok
      const playerService = appContext.services.player;
      if (playerService) {
        playerService.onViewModeChanged(isEgo);
      }
    }
  );

  // ========================================
  // Player Movement State Stack
  // ========================================
  modifierService.createModifierStack<PlayerMovementState>(
    StackName.PLAYER_MOVEMENT_STATE,
    PlayerMovementState.WALK, // Default: walking
    (newState: PlayerMovementState) => {
      // Callback may fail if PlayerService not ready yet - that's ok
      const playerService = appContext.services.player;
      if (playerService) {
        playerService.onMovementStateChanged(newState);
      }
    }
  );

  // ========================================
  // Player Pose Stack
  // ========================================
  modifierService.createModifierStack<string>(
    StackName.PLAYER_POSE,
    'idle', // Default: idle pose
    (newPose: string) => {
      // Callback when pose changes (optional notification/logging)
      logger.debug('Player pose changed', { pose: newPose });
    }
  );

  // ========================================
  // Fog View Mode Stack
  // ========================================
  modifierService.createModifierStack<number>(
    StackName.FOG_VIEW_MODE,
    0, // Default: fog disabled (0 = off)
    (fogIntensity: number) => {
      // Callback to update CameraService fog mode
      const cameraService = appContext.services.camera;
      if (cameraService) {
        cameraService.setFogMode(fogIntensity);
      }
    }
  );

  // ========================================
  // Ambient Audio Stack
  // ========================================
  modifierService.createModifierStack<string>(
    StackName.AMBIENT_AUDIO,
    '', // Default: no ambient music (empty string)
    (soundPath: string) => {
      // Callback to update AudioService ambient music
      const audioService = appContext.services.audio;
      if (audioService) {
        // Play or stop ambient music based on path
        audioService.playAmbientSound(soundPath, true, 1.0);
      }
    }
  );

  // ========================================
  // Animation Stacks for Environment
  // ========================================

  // Ambient Light Intensity Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.AMBIENT_LIGHT_INTENSITY,
    1.0, // Default: normal intensity
    (intensity: number) => {
      const environmentService = appContext.services.environment;
      if (environmentService) {
        environmentService.setAmbientLightIntensity(intensity);
      }
    },
    createLinearNumberAnimation(0.01), // Linear animation, 0.01 per step
    100 // Default wait time: 100ms
  );

  // Sun Position Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.SUN_POSITION,
    90, // Default: East (90 degrees)
    (angleY: number) => {
      const sunService = appContext.services.sun;
      if (sunService) {
        sunService.setSunPositionOnCircle(angleY);
      }
    },
    createLinearNumberAnimation(0.2), // Linear animation, 1 degree per step
    100 // Default wait time: 100ms
  );

  // Sun Elevation Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.SUN_ELEVATION,
    45, // Default: 45 degrees above horizon
    (elevation: number) => {
      const sunService = appContext.services.sun;
      if (sunService) {
        sunService.setSunHeightOverCamera(elevation);
      }
    },
    createLinearNumberAnimation(0.5), // Linear animation, 0.5 degrees per step
    100 // Default wait time: 100ms
  );

  // Horizon Gradient Alpha Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.HORIZON_GRADIENT_ALPHA,
    0.5, // Default: semi-transparent
    (alpha: number) => {
      const horizonGradientService = appContext.services.horizonGradient;
      if (horizonGradientService) {
        horizonGradientService.setAlpha(alpha);
      }
    },
    createLinearNumberAnimation(0.01), // Linear animation, 0.01 per step
    100 // Default wait time: 100ms
  );

  // Moon 0 Position Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.MOON_0_POSITION,
    0, // Default: North (0 degrees)
    (angleY: number) => {
      const moonService = appContext.services.moon;
      if (moonService) {
        moonService.setMoonPositionOnCircle(0, angleY);
      }
    },
    createLinearNumberAnimation(1.0), // Linear animation, 1 degree per step
    100 // Default wait time: 100ms
  );

  // Moon 1 Position Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.MOON_1_POSITION,
    120, // Default: 120 degrees (evenly spaced from moon 0)
    (angleY: number) => {
      const moonService = appContext.services.moon;
      if (moonService) {
        moonService.setMoonPositionOnCircle(1, angleY);
      }
    },
    createLinearNumberAnimation(1.0), // Linear animation, 1 degree per step
    100 // Default wait time: 100ms
  );

  // Moon 2 Position Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.MOON_2_POSITION,
    240, // Default: 240 degrees (evenly spaced from moon 0 and 1)
    (angleY: number) => {
      const moonService = appContext.services.moon;
      if (moonService) {
        moonService.setMoonPositionOnCircle(2, angleY);
      }
    },
    createLinearNumberAnimation(1.0), // Linear animation, 1 degree per step
    100 // Default wait time: 100ms
  );

  // Sun Light Intensity Multiplier Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.SUN_LIGHT_INTENSITY_MULTIPLIER,
    1.0, // Default: normal intensity
    (multiplier: number) => {
      const sunService = appContext.services.sun;
      if (sunService) {
        sunService.setSunLightIntensityMultiplier(multiplier);
      }
    },
    createLinearNumberAnimation(0.05), // Linear animation, 0.05 per step
    100 // Default wait time: 100ms
  );

  // Ambient Light Intensity Multiplier Animation Stack
  modifierService.createAnimationStack<number>(
    StackName.AMBIENT_LIGHT_INTENSITY_MULTIPLIER,
    0.5, // Default: 0.5 intensity
    (multiplier: number) => {
      const sunService = appContext.services.sun;
      if (sunService) {
        sunService.setAmbientLightIntensityMultiplier(multiplier);
      }
    },
    createLinearNumberAnimation(0.05), // Linear animation, 0.05 per step
    100 // Default wait time: 100ms
  );

  // ========================================
  // Weitere Stacks hier hinzufügen
  // ========================================

  logger.debug('All StackModifiers created', {
    stackCount: modifierService.stackNames.length,
    stacks: modifierService.stackNames,
  });
}
