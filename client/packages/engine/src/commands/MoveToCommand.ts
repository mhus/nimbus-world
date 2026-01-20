/**
 * MoveToCommand - Move player to specific coordinates
 *
 * Usage: moveTo <x> <y> <z> [yRotation] [splashScreen] [splashAudio]
 * Example: moveTo 100 64 200
 * Example: moveTo 100 64 200 1.57
 * Example: moveTo 100 64 200 1.57 images/loading.png audio/teleport.ogg
 */

import { CommandHandler } from './CommandHandler';
import { Vector3 } from '@babylonjs/core';
import { toNumber, toString, getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('MoveToCommand');

/**
 * MoveTo command - Teleports player to specific coordinates
 */
export class MoveToCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'moveTo';
  }

  description(): string {
    return 'Move player to coordinates (moveTo <x> <y> <z> [yRotation] [splashScreen] [splashAudio])';
  }

  execute(parameters: any[]): any {
    if (parameters.length < 3) {
      logger.error('Not enough parameters. Usage: moveTo <x> <y> <z> [yRotation] [splashScreen] [splashAudio]');
      return { error: 'Not enough parameters' };
    }

    // Parse coordinates
    const x = toNumber(parameters[0]);
    const y = toNumber(parameters[1]);
    const z = toNumber(parameters[2]);

    if (x === undefined || y === undefined || z === undefined) {
      logger.error('Invalid coordinates. Must be numbers.');
      return { error: 'Invalid coordinates' };
    }

    // Parse optional yRotation and splash screen/audio
    // Check if parameter 3 is a number (yRotation) or string (splashScreen)
    let yRotation: number | undefined = undefined;
    let splashScreen: string | undefined = undefined;
    let splashAudio: string | undefined = undefined;

    if (parameters.length > 3) {
      const param3 = toNumber(parameters[3]);
      if (param3 !== undefined) {
        // Parameter 3 is a number -> yRotation
        yRotation = param3;
        splashScreen = parameters.length > 4 ? toString(parameters[4]) : undefined;
        splashAudio = parameters.length > 5 ? toString(parameters[5]) : undefined;
      } else {
        // Parameter 3 is not a number -> splashScreen
        splashScreen = toString(parameters[3]);
        splashAudio = parameters.length > 4 ? toString(parameters[4]) : undefined;
      }
    }

    // Get services
    const physicsService = this.appContext.services.physics;
    const notificationService = this.appContext.services.notification;

    if (!physicsService) {
      logger.error('PhysicsService not available');
      return { error: 'PhysicsService not available' };
    }

    // Get player entity
    const playerEntity = physicsService.getEntity('player');
    if (!playerEntity) {
      logger.error('Player entity not found');
      return { error: 'Player entity not found' };
    }

    // Show splash screen if provided
    if (splashScreen && notificationService) {
      try {
        notificationService.showSplashScreen(splashScreen, splashAudio);
        logger.debug('Splash screen shown', { splashScreen, splashAudio });
      } catch (error) {
        logger.error('Failed to show splash screen', undefined, error as Error);
      }
    }

    // Disable physics and activate teleportation
    physicsService.disablePhysics();
    logger.debug('Physics disabled for teleportation');

    // Create target position
    const targetPosition = new Vector3(x, y, z);

    // Prepare rotation if yRotation is provided
    let targetRotation: Vector3 | undefined = undefined;
    if (yRotation !== undefined) {
      // Get current rotation and only change Y component
      const currentRotation = playerEntity.rotation;
      targetRotation = new Vector3(
        currentRotation.x, // Keep pitch
        yRotation,         // Set yaw
        currentRotation.z  // Keep roll
      );
    }

    // Teleport player
    try {
      physicsService.teleport(playerEntity, targetPosition, targetRotation);

      const rotationMsg = yRotation !== undefined ? ` with yRotation ${yRotation}` : '';
      logger.info(`✓ Moving player to (${x}, ${y}, ${z})${rotationMsg}`);

      return {
        success: true,
        position: { x, y, z },
        yRotation,
        splashScreen,
        splashAudio
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error(`✗ Failed to move player: ${errorMessage}`);

      // Re-enable physics on error
      physicsService.enablePhysics();

      throw error;
    }
  }
}
