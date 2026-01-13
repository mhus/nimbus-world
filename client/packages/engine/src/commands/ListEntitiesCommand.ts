/**
 * ListEntitiesCommand - Lists all loaded entities with their basic information
 *
 * Usage: listEntities
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ListEntitiesCommand');
import type { AppContext } from '../AppContext';

/**
 * ListEntities command - Shows list of all loaded entities
 */
export class ListEntitiesCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'listEntities';
  }

  description(): string {
    return 'Lists all loaded entities with their basic information. Usage: listEntities';
  }

  execute(parameters: any[]): any {
    const entityService = this.appContext.services.entity;

    if (!entityService) {
      logger.error('EntityService not available');
      return { error: 'EntityService not available' };
    }

    const entities = entityService.getAllEntities();

    if (entities.length === 0) {
      logger.debug('No entities loaded');
      return {
        message: 'No entities loaded',
        count: 0,
      };
    }

    const lines: string[] = [];
    lines.push(`=== Loaded Entities (${entities.length}) ===`);
    lines.push('');

    // Show visibility radius
    lines.push(`Visibility Radius: ${entityService.visibilityRadius} blocks`);
    lines.push('');

    // Table header
    lines.push('ID                    | Model      | Visible | Position (X, Y, Z)           | Distance');
    lines.push('----------------------|------------|---------|------------------------------|----------');

    // Get player position for distance calculation
    const playerService = this.appContext.services.player;
    const playerPos = playerService?.getPosition() ?? { x: 0, y: 0, z: 0 };

    // Sort by distance to player
    const sortedEntities = entities.slice().sort((a, b) => {
      const distA = Math.sqrt(
        Math.pow(a.currentPosition.x - playerPos.x, 2) +
        Math.pow(a.currentPosition.y - playerPos.y, 2) +
        Math.pow(a.currentPosition.z - playerPos.z, 2)
      );
      const distB = Math.sqrt(
        Math.pow(b.currentPosition.x - playerPos.x, 2) +
        Math.pow(b.currentPosition.y - playerPos.y, 2) +
        Math.pow(b.currentPosition.z - playerPos.z, 2)
      );
      return distA - distB;
    });

    // List entities
    for (const entity of sortedEntities) {
      const id = entity.id.padEnd(21).substring(0, 21);
      const modelId = entity.model.id.padEnd(10).substring(0, 10);
      const visible = entity.visible ? 'Yes    ' : 'No     ';
      const pos = `${entity.currentPosition.x.toFixed(1)}, ${entity.currentPosition.y.toFixed(1)}, ${entity.currentPosition.z.toFixed(1)}`;
      const posFormatted = pos.padEnd(28);

      // Calculate distance
      const distance = Math.sqrt(
        Math.pow(entity.currentPosition.x - playerPos.x, 2) +
        Math.pow(entity.currentPosition.y - playerPos.y, 2) +
        Math.pow(entity.currentPosition.z - playerPos.z, 2)
      );
      const distFormatted = distance.toFixed(1).padStart(8);

      lines.push(`${id} | ${modelId} | ${visible} | ${posFormatted} | ${distFormatted}`);
    }

    lines.push('');
    lines.push(`Total: ${entities.length} entities`);
    lines.push(`Visible: ${entities.filter(e => e.visible).length} entities`);
    lines.push('');
    lines.push('Use "entityInfo <entityId>" for detailed information about a specific entity');
    lines.push('================================');

    const output = lines.join('\n');
    logger.debug(output);

    // Return structured data
    return {
      count: entities.length,
      visibleCount: entities.filter(e => e.visible).length,
      visibilityRadius: entityService.visibilityRadius,
      entities: entities.map(e => ({
        id: e.id,
        modelId: e.model.id,
        visible: e.visible,
        position: e.currentPosition,
        rotation: e.currentRotation,
        pose: e.currentPose,
      })),
    };
  }
}
