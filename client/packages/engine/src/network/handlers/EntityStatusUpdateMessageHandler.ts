/**
 * EntityStatusUpdateMessageHandler
 *
 * Handles "e.s.u" (Entity Status Update) messages from server
 * Updates entity health and other status fields
 */

import { MessageHandler } from '../MessageHandler';
import { MessageType, BaseMessage, EntityStatusUpdate } from '@nimbus/shared';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { EntityService } from '../../services/EntityService';

const logger = getLogger('EntityStatusUpdateMessageHandler');

export class EntityStatusUpdateMessageHandler extends MessageHandler<EntityStatusUpdate[]> {
  readonly messageType = MessageType.ENTITY_STATUS_UPDATE;

  constructor(private entityService: EntityService) {
    super();
  }

  async handle(message: BaseMessage<EntityStatusUpdate[]>): Promise<void> {
    try {
      const updates = message.d;

      // Validation
      if (!updates || updates.length === 0) {
        logger.warn('Received empty entity status update');
        return;
      }

      logger.debug('Entity status updates received', {
        count: updates.length,
        entityIds: updates.map(u => u.entityId),
      });

      // Apply each update
      for (const update of updates) {
        await this.applyStatusUpdate(update);
      }
    } catch (error) {
      ExceptionHandler.handle(
        error,
        'EntityStatusUpdateMessageHandler.handle',
        { messageType: this.messageType }
      );
    }
  }

  private async applyStatusUpdate(update: EntityStatusUpdate): Promise<void> {
    try {
      const { entityId, status } = update;

      // Check if entity has died
      if (status.gone === 1) {
        logger.info('Entity died, removing from entity list', { entityId });
        await this.entityService.removeEntity(entityId);
        return;
      }

      // Get entity from service
      const clientEntity = await this.entityService.getEntity(entityId);

      if (!clientEntity) {
        logger.warn('Entity not found for status update', { entityId });
        return;
      }

      // Track if anything changed
      let hasChanges = false;

      // Update health
      if (status.health !== undefined && clientEntity.entity.health !== status.health) {
        clientEntity.entity.health = status.health;
        hasChanges = true;
        logger.debug('Entity health updated', {
          entityId,
          health: status.health,
          healthMax: clientEntity.entity.healthMax,
        });
      }

      // Update healthMax
      if (status.healthMax !== undefined && clientEntity.entity.healthMax !== status.healthMax) {
        clientEntity.entity.healthMax = status.healthMax;
        hasChanges = true;
        logger.debug('Entity healthMax updated', {
          entityId,
          healthMax: status.healthMax,
        });
      }

      // Update lastAccess for cache management
      clientEntity.lastAccess = Date.now();

      // Trigger transform update to emit events for UI updates (labels, health bars)
      if (hasChanges) {
        this.entityService.updateEntityTransform(
          entityId,
          clientEntity.currentPosition,
          clientEntity.currentRotation,
          clientEntity.currentPose
        );
      }
    } catch (error) {
      ExceptionHandler.handle(
        error,
        'EntityStatusUpdateMessageHandler.applyStatusUpdate',
        { entityId: update.entityId }
      );
    }
  }
}
