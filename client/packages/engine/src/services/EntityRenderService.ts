/**
 * EntityRenderService - Renders entities in the world
 *
 * Manages entity visualization:
 * - Loads entity models via ModelService
 * - Creates and updates entity meshes
 * - Handles entity animations and poses
 * - Responds to EntityService events (appear, disappear, transform, pose changes)
 */

import { Mesh, Scene, Vector3, AnimationGroup } from '@babylonjs/core';
import { getLogger, ExceptionHandler, type ClientEntity, type EntityPathway } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { EntityService } from './EntityService';
import type { ModelService } from './ModelService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { EntityLabelRenderer } from '../rendering/EntityLabelRenderer';

const logger = getLogger('EntityRenderService');

/**
 * Rendered entity data
 */
interface RenderedEntity {
  /** Entity ID */
  id: string;

  /** Root mesh */
  mesh: Mesh;

  /** Pathway lines (for debugging) */
  pathwayLines?: any;

  /** Animation groups (if any) */
  animations?: AnimationGroup[];

  /** Current animation group playing */
  currentAnimation?: AnimationGroup;

  /** Current pose ID (to detect changes) */
  currentPose?: number;
}

/**
 * EntityRenderService - Manages entity rendering
 *
 * Features:
 * - Listens to EntityService events
 * - Loads models via ModelService
 * - Creates and updates entity meshes
 * - Handles animations and poses
 * - Automatic cleanup when entities disappear
 */
export class EntityRenderService {
  private scene: Scene;
  private appContext: AppContext;
  private entityService: EntityService;
  private modelService: ModelService;

  // Rendered entities: entityId -> RenderedEntity
  private renderedEntities: Map<string, RenderedEntity> = new Map();

  // Entity label renderer
  private entityLabelRenderer: EntityLabelRenderer;

  // Debug: Show pathway lines
  private _showPathways: boolean = false;

  // Track warned missing poses to avoid spam (entityId:pose -> true)
  private warnedMissingPoses: Set<string> = new Set();

  constructor(scene: Scene, appContext: AppContext, entityService: EntityService, modelService: ModelService) {
    this.scene = scene;
    this.appContext = appContext;
    this.entityService = entityService;
    this.modelService = modelService;

    // Initialize label renderer
    this.entityLabelRenderer = new EntityLabelRenderer(scene);

    logger.debug('EntityRenderService initialized');

    // Register event listeners
    this.registerEventListeners();
  }

  /**
   * Register event listeners on EntityService
   */
  private registerEventListeners(): void {
    // Listen for pathway updates (entity appears or moves)
    this.entityService.on('pathway', async (pathway: EntityPathway) => {
      await this.onEntityPathway(pathway);

      // Create label when entity appears
      const clientEntity = await this.entityService.getEntity(pathway.entityId);
      if (clientEntity) {
        this.entityLabelRenderer.createLabel(clientEntity);
      }
    });

    // Listen for transform updates (position/rotation/pose changes)
    this.entityService.on('transform', async (data: any) => {
      this.updateEntityTransform(data.entityId, data.position, data.rotation);
      if (data.pose !== undefined) {
        this.updateEntityPose(data.entityId, data.pose, data.velocity);
      }

      // Update label
      const clientEntity = await this.entityService.getEntity(data.entityId);
      if (clientEntity) {
        this.entityLabelRenderer.updateLabel(clientEntity);
      }
    });

    // Listen for entity visibility changes
    this.entityService.on('visibility', (data: { entityId: string; visible: boolean }) => {
      this.onEntityVisibility(data.entityId, data.visible);

      // Update label visibility
      this.entityLabelRenderer.setLabelVisibility(data.entityId, data.visible);
    });

    // Listen for entity removal
    this.entityService.on('removed', (entityId: string) => {
      this.onEntityRemoved(entityId);

      // Remove label
      this.entityLabelRenderer.removeLabel(entityId);
    });

    logger.debug('Event listeners registered');
  }

  /**
   * Handle entity pathway update (entity appears or moves)
   */
  /**
   * Update entity pathway (creates entity if it doesn't exist)
   * @param pathway Entity pathway
   */
  async updateEntityPathway(pathway: EntityPathway): Promise<void> {
    try {
      const entityId = pathway.entityId;

      logger.debug('Pathway event received', { entityId, waypointCount: pathway.waypoints.length });

      // Check if entity is already rendered
      if (this.renderedEntities.has(entityId)) {
        // Entity already exists, update pathway lines if enabled
        if (this._showPathways) {
          logger.debug('Entity pathway updated, redrawing lines', { entityId });
          await this.drawPathwayLines(entityId, pathway);
        }
        return;
      }

      // Entity doesn't exist yet, create it
      logger.debug('Creating new entity', { entityId });
      await this.createEntity(entityId);

      // Draw pathway lines if enabled
      if (this._showPathways) {
        await this.drawPathwayLines(entityId, pathway);
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityRenderService.updateEntityPathway', {
        entityId: pathway.entityId,
      });
    }
  }

  /**
   * Handle entity pathway events (private event handler)
   */
  private async onEntityPathway(pathway: EntityPathway): Promise<void> {
    await this.updateEntityPathway(pathway);
  }

  /**
   * Handle entity visibility change
   */
  /**
   * Set entity visibility
   * @param entityId Entity ID
   * @param visible Whether entity should be visible
   */
  setEntityVisibility(entityId: string, visible: boolean): void {
    try {
      const rendered = this.renderedEntities.get(entityId);
      if (!rendered) {
        return;
      }

      rendered.mesh.setEnabled(visible);
      logger.debug('Entity visibility changed', { entityId, visible });
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityRenderService.setEntityVisibility', { entityId, visible });
    }
  }

  /**
   * Handle entity visibility events (private event handler)
   */
  private onEntityVisibility(entityId: string, visible: boolean): void {
    this.setEntityVisibility(entityId, visible);
  }

  /**
   * Handle entity removal
   */
  private onEntityRemoved(entityId: string): void {
    try {
      this.removeEntity(entityId);
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityRenderService.onEntityRemoved', { entityId });
    }
  }

  /**
   * Create and render entity
   */
  private async createEntity(entityId: string): Promise<void> {
    try {
      // Get entity from EntityService
      const clientEntity = await this.entityService.getEntity(entityId);
      if (!clientEntity) {
        logger.warn('Entity not found in EntityService', { entityId });
        return;
      }

      // Load GLB container via ModelService
      const modelPath = clientEntity.model.modelPath;
      const container = await this.modelService.loadGlbContainer(modelPath);
      if (!container) {
        logger.error('Failed to load entity model container', { entityId, modelPath });
        return;
      }

      // Instantiate model for this entity (clones mesh + skeleton + animations)
      const result = container.instantiateModelsToScene(
        name => `entity_${entityId}_${name}`,
        false, // Don't clone materials
        { doNotInstantiate: false }
      );

      if (!result.rootNodes || result.rootNodes.length === 0) {
        logger.error('No root nodes in instantiated model', { entityId, modelPath });
        return;
      }

      // Get root node (might be TransformNode, not Mesh)
      const modelRootNode = result.rootNodes[0];
      modelRootNode.setEnabled(true);

      // Set rendering group for all meshes in the entity model
      modelRootNode.getChildMeshes().forEach(mesh => {
        // Skip instanced meshes (they inherit from source mesh)
        if (!mesh.isAnInstance) {
          mesh.renderingGroupId = RENDERING_GROUPS.WORLD;
        }
      });

      // Store entity ID in metadata for selection/raycasting
      modelRootNode.metadata = modelRootNode.metadata || {};
      modelRootNode.metadata.entityId = entityId;

      // Create a parent TransformNode for manual rotation control
      // This prevents animations from overwriting our rotation
      const { TransformNode } = await import('@babylonjs/core');
      const rotationNode = new TransformNode(`entity_rotation_${entityId}`, this.scene);
      rotationNode.metadata = { entityId }; // Also store in rotation node
      modelRootNode.parent = rotationNode;

      // Enable shadows for entity meshes
      const envService = this.appContext.services.environment;
      if (envService && envService.getShadowGenerator) {
        const shadowGenerator = envService.getShadowGenerator();
        if (shadowGenerator) {
          const shadowMap = shadowGenerator.getShadowMap();
          if (shadowMap && shadowMap.renderList) {
            // Get all child meshes and enable shadows
            const childMeshes = modelRootNode.getChildMeshes();
            for (const mesh of childMeshes) {
              // Skip instanced meshes - they inherit receiveShadows from source mesh
              if (!mesh.isAnInstance) {
                mesh.receiveShadows = true;
              }
              // Add to renderList directly (like chunks)
              shadowMap.renderList.push(mesh);
            }
            if (childMeshes.length > 0) {
              logger.debug('Entity meshes registered for shadows (casting + receiving)', {
                entityId,
                meshCount: childMeshes.length,
              });
            }
          }
        }
      }

      // Get cloned animation groups (automatically retargeted to this instance)
      const animations = result.animationGroups || [];

      logger.debug('Entity model instantiated', {
        entityId,
        modelPath,
        animationCount: animations.length,
      });

      // Apply initial transform to rotation node
      const pos = clientEntity.currentPosition;
      const rot = clientEntity.currentRotation;

      // Apply position with offset from model
      const offset = clientEntity.model.positionOffset;
      rotationNode.position = new Vector3(
        pos.x + offset.x,
        pos.y + offset.y,
        pos.z + offset.z
      );

      // Apply rotation with offset from model
      rotationNode.rotation.y = (rot.y * Math.PI) / 180;
      const rotOffset = clientEntity.model.rotationOffset;
      rotationNode.rotation.y += (rotOffset.y * Math.PI) / 180;
      if ('p' in rotOffset) {
        rotationNode.rotation.x += ((rotOffset as any).p * Math.PI) / 180;
      }

      // Apply scale from model
      const scale = clientEntity.model.scale;
      rotationNode.scaling = new Vector3(scale.x, scale.y, scale.z);

      // Store rendered entity (use rotationNode as mesh for transform updates)
      const rendered: RenderedEntity = {
        id: entityId,
        mesh: rotationNode as any, // Store rotation node (controls position/rotation)
        animations: animations.length > 0 ? animations : undefined,
      };

      this.renderedEntities.set(entityId, rendered);

      // Start initial animation based on current pose
      if (animations.length > 0) {
        this.updateEntityPose(entityId, clientEntity.currentPose);
      }

      // Store mesh reference in ClientEntity for EntityService
      clientEntity.meshes = [modelRootNode as any];

      // Make all meshes pickable for entity selection (raycasting)
      if (result.rootNodes) {
        for (const node of result.rootNodes) {
          // Set isPickable on all mesh children recursively
          node.getChildMeshes().forEach((mesh) => {
            mesh.isPickable = true;
            // Also store entityId in child mesh metadata for selection
            mesh.metadata = mesh.metadata || {};
            mesh.metadata.entityId = entityId;
          });
          // Also set on root if it's a mesh
          if ('isPickable' in node) {
            (node as any).isPickable = true;
          }
        }
      }

      logger.debug('Entity created and rendered', { entityId, modelPath });
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'EntityRenderService.createEntity', { entityId });
    }
  }

  /**
   * Remove entity from scene
   */
  private removeEntity(entityId: string): void {
    const rendered = this.renderedEntities.get(entityId);
    if (!rendered) {
      return;
    }

    // Stop animations
    if (rendered.currentAnimation) {
      rendered.currentAnimation.stop();
    }

    // Dispose animations
    if (rendered.animations) {
      for (const anim of rendered.animations) {
        anim.dispose();
      }
    }

    // Dispose pathway lines
    if (rendered.pathwayLines) {
      rendered.pathwayLines.dispose();
    }

    // Dispose mesh
    rendered.mesh.dispose();

    this.renderedEntities.delete(entityId);
    logger.debug('Entity removed', { entityId });
  }

  /**
   * Update entity transform (called by EntityService or interpolation)
   */
  updateEntityTransform(
    entityId: string,
    position: { x: number; y: number; z: number },
    rotation: { y: number; p?: number }
  ): void {
    const rendered = this.renderedEntities.get(entityId);
    if (!rendered) {
      return;
    }

    // Get entity to check offsets
    const clientEntity = this.entityService.getAllEntities().find(e => e.id === entityId);
    if (!clientEntity) {
      return;
    }

    // Update position with offset from model
    const offset = clientEntity.model.positionOffset;
    rendered.mesh.position.set(
      position.x + offset.x,
      position.y + offset.y,
      position.z + offset.z
    );

    // Update rotation with offset from model
    if ('rotation' in rendered.mesh) {
      const rotOffset = clientEntity.model.rotationOffset;
      (rendered.mesh as any).rotation.y = (rotation.y * Math.PI) / 180 + (rotOffset.y * Math.PI) / 180;
      if (rotation.p !== undefined) {
        const rotOffsetP = ('p' in rotOffset) ? (rotOffset as any).p : 0;
        (rendered.mesh as any).rotation.x = (rotation.p * Math.PI) / 180 + (rotOffsetP * Math.PI) / 180;
      }
    }
  }

  /**
   * Update entity pose/animation
   */
  updateEntityPose(entityId: string, pose: number, velocity?: number): void {
    const rendered = this.renderedEntities.get(entityId);
    if (!rendered || !rendered.animations) {
      return;
    }

    // if (entityId === '@player_avatar') {
    //   logger.debug('updateEntityPose', entityId, pose, rendered.currentPose, velocity);
    // }
    // Check if pose actually changed
    if (rendered.currentPose === pose) {
      return; // Pose hasn't changed, don't restart animation
    }

    // Get entity to check pose mapping
    const clientEntity = this.entityService.getAllEntities().find(e => e.id === entityId);
    if (!clientEntity) {
      return;
    }

    // Get animation config from pose mapping
    const poseConfig = clientEntity.model.poseMapping.get(pose);
    if (!poseConfig) {
      // Only warn once per entityId:pose combination to avoid spam
      const warnKey = `${entityId}:${pose}`;
      if (!this.warnedMissingPoses.has(warnKey)) {
        logger.warn('No animation config found for pose', { entityId, pose });
        this.warnedMissingPoses.add(warnKey);
      }
      return;
    }

    // Find animation group by name (instantiated animations have entity prefix)
    const animation = rendered.animations.find(a =>
      a.name === poseConfig.animationName ||
      a.name === `entity_${entityId}_${poseConfig.animationName}`
    );
    if (!animation) {
      logger.warn('Animation not found', {
        entityId,
        animationName: poseConfig.animationName,
        searchedFor: [poseConfig.animationName, `entity_${entityId}_${poseConfig.animationName}`],
        availableAnimations: rendered.animations.map(a => a.name),
      });
      return;
    }

    // Stop current animation of THIS entity (not all animations in scene!)
    if (rendered.currentAnimation) {
      rendered.currentAnimation.stop();
    }

    // Calculate speed ratio from speedMultiplier and velocity
    let speedRatio = poseConfig.speedMultiplier;
    if (velocity !== undefined && velocity > 0) {
      // Adjust speed based on actual movement velocity
      speedRatio = poseConfig.speedMultiplier * velocity;
    }

    // Play new animation
    animation.start(poseConfig.loop, speedRatio);
    rendered.currentAnimation = animation;
    rendered.currentPose = pose; // Remember current pose

    logger.debug('Entity animation changed', { entityId, pose, animationName: poseConfig.animationName });
  }

  /**
   * Get all rendered entities
   */
  getRenderedEntities(): RenderedEntity[] {
    return Array.from(this.renderedEntities.values());
  }

  /**
   * Get rendered entity by ID
   */
  getRenderedEntity(entityId: string): RenderedEntity | undefined {
    return this.renderedEntities.get(entityId);
  }

  /**
   * Draw pathway lines for debugging
   */
  private async drawPathwayLines(entityId: string, pathway: EntityPathway): Promise<void> {
    try {
      const rendered = this.renderedEntities.get(entityId);
      if (!rendered) {
        return;
      }

      // Remove old pathway lines if they exist
      if (rendered.pathwayLines) {
        rendered.pathwayLines.dispose();
        rendered.pathwayLines = undefined;
      }

      if (pathway.waypoints.length === 0) {
        return; // No waypoints to draw
      }

      const { MeshBuilder, Color3, Vector3 } = await import('@babylonjs/core');

      // Get entity's current position from EntityService
      const entityService = this.appContext.services.entity;
      const clientEntity = entityService ? await entityService.getEntity(entityId) : null;
      const currentPosition = clientEntity?.currentPosition;

      // Build points array: Start from current position, then all waypoints
      const points: Vector3[] = [];

      if (currentPosition) {
        points.push(new Vector3(currentPosition.x, currentPosition.y, currentPosition.z));
      }

      pathway.waypoints.forEach(wp => {
        points.push(new Vector3(wp.target.x, wp.target.y, wp.target.z));
      });

      // Need at least 2 points to draw a line
      if (points.length < 2) {
        return;
      }

      const lines = MeshBuilder.CreateLines(
        `pathway_lines_${entityId}`,
        { points },
        this.scene
      );
      lines.color = new Color3(0, 1, 0); // Green lines

      rendered.pathwayLines = lines;
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityRenderService.drawPathwayLines', { entityId });
    }
  }

  /**
   * Get showPathways setting
   */
  get showPathways(): boolean {
    return this._showPathways;
  }

  /**
   * Set showPathways setting
   */
  set showPathways(value: boolean) {
    this._showPathways = value;
    logger.debug('Pathway visibility changed', { showPathways: value });

    // Update all existing entities
    for (const [entityId, rendered] of this.renderedEntities.entries()) {
      if (value) {
        // Show pathways - redraw if not present
        if (!rendered.pathwayLines) {
          const pathway = this.entityService.getEntityPathway(entityId);
          if (pathway) {
            this.drawPathwayLines(entityId, pathway);
          }
        }
      } else {
        // Hide pathways - remove lines
        if (rendered.pathwayLines) {
          rendered.pathwayLines.dispose();
          rendered.pathwayLines = undefined;
        }
      }
    }
  }

  /**
   * Dispose service (cleanup all entities)
   */
  dispose(): void {
    // Dispose label renderer
    this.entityLabelRenderer.dispose();

    // Remove all entities
    for (const entityId of Array.from(this.renderedEntities.keys())) {
      this.removeEntity(entityId);
    }

    logger.debug('EntityRenderService disposed');
  }
}
