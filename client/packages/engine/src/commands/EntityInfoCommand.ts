/**
 * EntityInfoCommand - Shows detailed information about a specific entity
 *
 * Usage: entityInfo <entityId>
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('EntityInfoCommand');
import type { AppContext } from '../AppContext';

/**
 * EntityInfo command - Shows complete information about an entity
 */
export class EntityInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'entityInfo';
  }

  description(): string {
    return 'Shows detailed information about an entity. Usage: entityInfo <entityId>';
  }

  execute(parameters: any[]): any {
    // Validate parameters
    if (parameters.length !== 1) {
      logger.error('Usage: entityInfo <entityId>');
      return {
        error: 'Invalid parameters',
        usage: 'entityInfo <entityId>',
        example: 'entityInfo cow1',
      };
    }

    const entityId = parameters[0];
    const entityService = this.appContext.services.entity;

    if (!entityService) {
      logger.error('EntityService not available');
      return { error: 'EntityService not available' };
    }

    // Get all entities to find the requested one
    const allEntities = entityService.getAllEntities();
    const clientEntity = allEntities.find(e => e.id === entityId);

    if (!clientEntity) {
      logger.error(`Entity '${entityId}' not found`);
      logger.debug('');
      logger.debug('Available entities:');
      allEntities.forEach(e => logger.debug(`  - ${e.id}`));
      return {
        error: 'Entity not found',
        entityId,
        availableEntities: allEntities.map(e => e.id),
      };
    }

    const lines: string[] = [];
    lines.push('=== Entity Info ===');
    lines.push('');

    // Basic info
    lines.push('Entity:');
    lines.push(`  ID              : ${clientEntity.id}`);
    lines.push(`  Name            : ${clientEntity.entity.name || '(unnamed)'}`);
    lines.push(`  Model ID        : ${clientEntity.model.id}`);
    lines.push(`  Model Type      : ${clientEntity.model.type}`);
    lines.push(`  Visible         : ${clientEntity.visible ? 'Yes' : 'No'}`);
    lines.push('');

    // Position and rotation
    lines.push('Transform:');
    lines.push(`  Position        : (${clientEntity.currentPosition.x.toFixed(2)}, ${clientEntity.currentPosition.y.toFixed(2)}, ${clientEntity.currentPosition.z.toFixed(2)})`);
    lines.push(`  Rotation        : Y=${clientEntity.currentRotation.y.toFixed(2)}°, P=${clientEntity.currentRotation.p?.toFixed(2) ?? 0}°`);
    lines.push(`  Current Pose    : ${clientEntity.currentPose}`);
    lines.push('');

    // Calculate distance to player
    const playerService = this.appContext.services.player;
    if (playerService) {
      const playerPos = playerService.getPosition();
      const distance = Math.sqrt(
        Math.pow(clientEntity.currentPosition.x - playerPos.x, 2) +
        Math.pow(clientEntity.currentPosition.y - playerPos.y, 2) +
        Math.pow(clientEntity.currentPosition.z - playerPos.z, 2)
      );
      lines.push(`  Distance to Player: ${distance.toFixed(2)} blocks`);
      lines.push('');
    }

    // Model info
    lines.push('Entity Model:');
    lines.push(`  Model Path      : ${clientEntity.model.modelPath}`);
    lines.push(`  Pose Type       : ${clientEntity.model.poseType}`);
    lines.push(`  Movement Type   : ${clientEntity.entity.movementType}`);
    lines.push(`  Solid           : ${clientEntity.entity.solid ?? false}`);
    lines.push(`  Interactive     : ${clientEntity.entity.interactive ?? false}`);
    lines.push('');

    // Dimensions
    lines.push('Dimensions:');
    const dims = clientEntity.model.dimensions;
    if (dims.walk) {
      lines.push(`  Walk            : H=${dims.walk.height}, W=${dims.walk.width}, F=${dims.walk.footprint}`);
    }
    if (dims.sprint) {
      lines.push(`  Sprint          : H=${dims.sprint.height}, W=${dims.sprint.width}, F=${dims.sprint.footprint}`);
    }
    if (dims.crouch) {
      lines.push(`  Crouch          : H=${dims.crouch.height}, W=${dims.crouch.width}, F=${dims.crouch.footprint}`);
    }
    if (dims.swim) {
      lines.push(`  Swim            : H=${dims.swim.height}, W=${dims.swim.width}, F=${dims.swim.footprint}`);
    }
    if (dims.climb) {
      lines.push(`  Climb           : H=${dims.climb.height}, W=${dims.climb.width}, F=${dims.climb.footprint}`);
    }
    if (dims.fly) {
      lines.push(`  Fly             : H=${dims.fly.height}, W=${dims.fly.width}, F=${dims.fly.footprint}`);
    }
    if (dims.teleport) {
      lines.push(`  Teleport        : H=${dims.teleport.height}, W=${dims.teleport.width}, F=${dims.teleport.footprint}`);
    }
    lines.push('');

    // Offsets
    lines.push('Offsets:');
    lines.push(`  Position        : (${clientEntity.model.positionOffset.x}, ${clientEntity.model.positionOffset.y}, ${clientEntity.model.positionOffset.z})`);
    lines.push(`  Rotation        : Y=${clientEntity.model.rotationOffset.y}°`);
    lines.push('');

    // Pose mapping
    if (clientEntity.model.poseMapping.size > 0) {
      lines.push('Pose Mapping:');
      for (const [poseId, poseName] of clientEntity.model.poseMapping.entries()) {
        const current = poseId === clientEntity.currentPose ? ' (current)' : '';
        lines.push(`  ${poseId}: ${poseName}${current}`);
      }
      lines.push('');
    }

    // Waypoints
    const pathway = entityService.getEntityPathway(entityId);
    if (pathway && pathway.waypoints.length > 0) {
      lines.push('Current Pathway:');
      lines.push(`  Waypoints       : ${pathway.waypoints.length}`);
      lines.push(`  Current Index   : ${clientEntity.currentWaypointIndex}`);
      lines.push(`  Looping         : ${pathway.isLooping ?? false}`);
      lines.push(`  Idle Pose       : ${pathway.idlePose ?? 0}`);
      lines.push('');

      // Show next few waypoints
      const nextWaypoints = pathway.waypoints.slice(
        clientEntity.currentWaypointIndex,
        clientEntity.currentWaypointIndex + 3
      );
      if (nextWaypoints.length > 0) {
        lines.push('  Next Waypoints:');
        nextWaypoints.forEach((wp, idx) => {
          const wpIndex = clientEntity.currentWaypointIndex + idx;
          const time = new Date(wp.timestamp).toLocaleTimeString();
          lines.push(`    [${wpIndex}] (${wp.target.x.toFixed(1)}, ${wp.target.y.toFixed(1)}, ${wp.target.z.toFixed(1)}) at ${time}`);
        });
        lines.push('');
      }
    } else {
      lines.push('Current Pathway: None');
      lines.push('');
    }

    // Model modifiers
    if (clientEntity.entity.modelModifier && Object.keys(clientEntity.entity.modelModifier).length > 0) {
      lines.push('Model Modifiers:');
      lines.push(JSON.stringify(clientEntity.entity.modelModifier, null, 2).split('\n').map(line => '  ' + line).join('\n'));
      lines.push('');
    }

    // Meshes and Animations
    if (clientEntity.meshes.length > 0) {
      lines.push(`Rendered Meshes: ${clientEntity.meshes.length}`);
      lines.push('');

      // Get all available animations from the scene
      const engineService = this.appContext.services.engine;
      if (engineService) {
        const scene = engineService.getScene();
        if (scene) {
          const allAnimations = scene.animationGroups;
          if (allAnimations.length > 0) {
            lines.push('Available Animations in Scene:');
            allAnimations.forEach(ag => {
              const isPlaying = ag.isPlaying ? ' (PLAYING)' : '';
              lines.push(`  - ${ag.name}${isPlaying}`);
            });
            lines.push('');
          } else {
            lines.push('No animations found in scene');
            lines.push('');
          }
        }
      }
    }

    // Cache info
    const cacheAge = Date.now() - clientEntity.lastAccess;
    lines.push('Cache Info:');
    lines.push(`  Last Access     : ${(cacheAge / 1000).toFixed(1)}s ago`);
    lines.push('');

    lines.push('==================');

    const output = lines.join('\n');
    logger.debug(output);

    // Output complete Entity object
    logger.debug('');
    logger.debug('=== Complete Entity Object ===');
    logger.debug(JSON.stringify(clientEntity.entity, null, 2));

    // Output complete EntityModel object
    logger.debug('');
    logger.debug('=== Complete EntityModel Object ===');
    logger.debug(JSON.stringify(clientEntity.model, null, 2));

    // Return structured data
    return {
      entity: clientEntity.entity,
      model: {
        id: clientEntity.model.id,
        type: clientEntity.model.type,
        modelPath: clientEntity.model.modelPath,
        dimensions: clientEntity.model.dimensions,
      },
      transform: {
        position: clientEntity.currentPosition,
        rotation: clientEntity.currentRotation,
        pose: clientEntity.currentPose,
      },
      visible: clientEntity.visible,
      pathway: pathway
        ? {
            waypointCount: pathway.waypoints.length,
            currentIndex: clientEntity.currentWaypointIndex,
            isLooping: pathway.isLooping,
          }
        : null,
      meshCount: clientEntity.meshes.length,
      cacheAge: cacheAge,
    };
  }
}
