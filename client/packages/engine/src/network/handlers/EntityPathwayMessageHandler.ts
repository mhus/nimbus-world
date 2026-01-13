/**
 * EntityPathwayMessageHandler - Handles entity pathway messages (e.p)
 *
 * Receives entity pathways from server based on registered chunks
 * and forwards them to EntityService for interpolation and rendering.
 *
 * Message format:
 * - Type: "e.p"
 * - Data: EntityPathway[] (array of pathways)
 */

import {
  BaseMessage,
  MessageType,
  type EntityPathway,
  getLogger,
  ExceptionHandler,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { EntityService } from '../../services/EntityService';

const logger = getLogger('EntityPathwayMessageHandler');

/**
 * Handles ENTITY_CHUNK_PATHWAY messages from server (e.p)
 */
export class EntityPathwayMessageHandler extends MessageHandler<EntityPathway[]> {
  readonly messageType = MessageType.ENTITY_CHUNK_PATHWAY;

  constructor(private entityService: EntityService) {
    super();
  }

  async handle(message: BaseMessage<EntityPathway[]>): Promise<void> {
    try {
      const pathways = message.d;

      if (!pathways || pathways.length === 0) {
        logger.debug('Received empty entity pathway message');
        return;
      }

      logger.debug('Entity pathways received', {
        count: pathways.length,
        entityIds: pathways.map(p => p.entityId),
      });

      // Process each pathway
      for (const pathway of pathways) {
        try {
          // Set pathway in EntityService (auto-loads unknown entities)
          await this.entityService.setEntityPathway(pathway);

          logger.debug('Entity pathway processed', {
            entityId: pathway.entityId,
            waypointCount: pathway.waypoints.length,
          });
        } catch (error) {
          ExceptionHandler.handle(error, 'EntityPathwayMessageHandler.handle.pathway', {
            entityId: pathway.entityId,
          });
        }
      }

      logger.debug('All entity pathways processed', { count: pathways.length });
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityPathwayMessageHandler.handle', {
        message,
      });
    }
  }
}
