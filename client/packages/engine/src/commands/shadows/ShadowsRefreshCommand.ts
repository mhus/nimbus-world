/**
 * ShadowsRefreshCommand - Refresh shadow casters and receivers
 *
 * Usage: shadowsRefresh
 * Re-registers all entities and chunks as shadow casters/receivers
 * Useful after enabling shadows on-the-fly
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShadowsRefreshCommand');

export class ShadowsRefreshCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsRefresh';
  }

  description(): string {
    return 'Refresh shadow casters and receivers for all entities and chunks';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    const shadowGenerator = envService.getShadowGenerator();
    if (!shadowGenerator) {
      return 'Shadow generator not initialized. Enable shadows first with: shadowsEnable true';
    }

    let entityMeshCount = 0;
    let chunkMeshCount = 0;
    let receiverCount = 0;

    // Register all entities as shadow casters
    const entityRenderService = this.appContext.services.entityRender;
    const hasEntityService = !!entityRenderService;
    const entities = entityRenderService ? (entityRenderService as any).renderedEntities : null;
    const entityCount = entities ? entities.size : 0;

    if (entities) {
      for (const [entityId, rendered] of entities) {
        if (rendered.mesh) {
          // Get all child meshes from the parent node
          const childMeshes = rendered.mesh.getChildMeshes ? rendered.mesh.getChildMeshes() : [];
          for (const mesh of childMeshes) {
            // Skip instanced meshes - they inherit from source mesh
            if (!mesh.isAnInstance) {
              mesh.receiveShadows = true;
            }
            shadowGenerator.addShadowCaster(mesh);
            entityMeshCount++;
          }
        }
      }
    }

    // Register all chunk meshes as shadow receivers
    const renderService = this.appContext.services.render;
    const hasRenderService = !!renderService;
    const chunkMeshes = renderService ? (renderService as any).chunkMeshes : null;
    const chunkMapCount = chunkMeshes ? chunkMeshes.size : 0;

    if (chunkMeshes) {
      for (const [chunkKey, meshMap] of chunkMeshes) {
        for (const mesh of meshMap.values()) {
          mesh.receiveShadows = true;
          chunkMeshCount++;
          receiverCount++;
        }
      }
    }

    logger.info('Shadow casters and receivers refreshed', {
      entityMeshCount,
      chunkMeshCount,
      receiverCount,
    });

    return `Shadow system refreshed:
  EntityRenderService: ${hasEntityService ? 'found' : 'missing'}
  Entities in map: ${entityCount}
  Entity meshes registered: ${entityMeshCount}

  RenderService: ${hasRenderService ? 'found' : 'missing'}
  Chunk maps: ${chunkMapCount}
  Chunk meshes registered: ${chunkMeshCount}

  Total shadow receivers: ${receiverCount}
  Total shadow casters: ${shadowGenerator.getShadowMap()?.renderList?.length || 0}`;
  }
}
