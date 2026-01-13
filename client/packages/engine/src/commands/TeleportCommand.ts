/**
 * TeleportCommand - Teleport player to specific coordinates
 *
 * Usage: teleport <x> <y> <z>
 */

import { Vector3 } from '@babylonjs/core';
import {  toNumber , getLogger } from '@nimbus/shared';
import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';

const logger = getLogger('TeleportCommand');

/**
 * Teleport command - Teleports player to coordinates
 */
export class TeleportCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'teleport';
  }

  description(): string {
    return 'Teleport to coordinates (x, y, z). Usage: teleport <x> <y> <z>';
  }

  execute(parameters: any[]): any {
    // Validate parameters
    if (parameters.length !== 3) {
      logger.error('Usage: teleport <x> <y> <z>');
      return {
        error: 'Invalid parameters',
        usage: 'teleport <x> <y> <z>',
        example: 'teleport 2 64 -8',
      };
    }

    const blockX = toNumber(parameters[0]);
    const blockY = toNumber(parameters[1]);
    const blockZ = toNumber(parameters[2]);

    if (isNaN(blockX) || isNaN(blockY) || isNaN(blockZ)) {
      logger.error('Coordinates must be numbers');
      return {
        error: 'Invalid coordinates',
        x: parameters[0],
        y: parameters[1],
        z: parameters[2],
      };
    }

    const physicsService = this.appContext.services.physics;

    if (!physicsService) {
      logger.error('PhysicsService not available');
      return { error: 'PhysicsService not available' };
    }

    const playerEntity = physicsService.getEntity('player');

    if (!playerEntity) {
      logger.error('Player entity not available');
      return { error: 'Player entity not available' };
    }

    // Use PhysicsService teleport method (converts to position internally)
    physicsService.teleport(playerEntity, new Vector3(blockX, blockY, blockZ));

    logger.debug(`✓ Teleporting to block (${blockX}, ${blockY}, ${blockZ})`);

    return {
      message: `Teleporting to block (${blockX}, ${blockY}, ${blockZ})`,
      blockCoords: { x: blockX, y: blockY, z: blockZ },
    };
  }
}
