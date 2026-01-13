/**
 * SetEntityStatusCommand - Updates entity status (health, healthMax)
 *
 * Usage: setEntityStatus <entityId> <health> [healthMax]
 *
 * Updates the entity's health and optionally healthMax values.
 * Triggers UI updates (health bars, labels) via transform event.
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { toNumber, getLogger } from '@nimbus/shared';

const logger = getLogger('SetEntityStatusCommand');

/**
 * SetEntityStatus command - Update entity health/healthMax
 */
export class SetEntityStatusCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'setEntityStatus';
  }

  description(): string {
    return 'Updates entity status (health, healthMax). Usage: setEntityStatus <entityId> <health> [healthMax]';
  }

  async execute(parameters: any[]): Promise<any> {
    // Validate parameters
    if (parameters.length < 2 || parameters.length > 3) {
      logger.error('Usage: setEntityStatus <entityId> <health> [healthMax]');
      return {
        error: 'Invalid parameters',
        usage: 'setEntityStatus <entityId> <health> [healthMax]',
        example: 'setEntityStatus test_cow1 50 100',
      };
    }

    const entityId = parameters[0];
    const health = toNumber(parameters[1]);
    const healthMax = parameters.length === 3 ? toNumber(parameters[2]) : undefined;

    if (isNaN(health)) {
      logger.error('Health must be a number');
      return {
        error: 'Invalid health value',
        health: parameters[1],
      };
    }

    if (healthMax !== undefined && isNaN(healthMax)) {
      logger.error('HealthMax must be a number');
      return {
        error: 'Invalid healthMax value',
        healthMax: parameters[2],
      };
    }

    const entityService = this.appContext.services.entity;

    if (!entityService) {
      logger.error('EntityService not available');
      return { error: 'EntityService not available' };
    }

    try {
      // Get entity
      const clientEntity = await entityService.getEntity(entityId);

      if (!clientEntity) {
        logger.error(`Entity '${entityId}' not found`);
        logger.debug('Use "listEntities" to see all loaded entities');
        return {
          error: 'Entity not found',
          entityId,
        };
      }

      logger.info(`Updating entity status for '${entityId}'...`);

      // Update health
      const oldHealth = clientEntity.entity.health;
      clientEntity.entity.health = health;
      logger.info(`  health: ${oldHealth} → ${health}`);

      // Update healthMax if provided
      if (healthMax !== undefined) {
        const oldHealthMax = clientEntity.entity.healthMax;
        clientEntity.entity.healthMax = healthMax;
        logger.info(`  healthMax: ${oldHealthMax} → ${healthMax}`);
      }

      // Update lastAccess for cache management
      clientEntity.lastAccess = Date.now();

      // Trigger transform event to update UI (labels, health bars)
      entityService.updateEntityTransform(
        entityId,
        clientEntity.currentPosition,
        clientEntity.currentRotation,
        clientEntity.currentPose
      );

      logger.info('✓ Entity status updated successfully');

      const result: any = {
        success: true,
        entityId,
        health,
        currentHealthMax: clientEntity.entity.healthMax,
      };

      if (healthMax !== undefined) {
        result.healthMax = healthMax;
      }

      return result;
    } catch (error) {
      logger.error('Failed to update entity status:', error);
      return {
        error: 'Failed to update entity status',
        message: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }
}
