/**
 * SelectService - Manages block selection in front of the player
 *
 * Provides raycasting functionality to find blocks in the player's line of sight.
 * Supports different selection modes: INTERACTIVE, BLOCK, AIR, ALL, NONE.
 * Includes auto-select mode with visual highlighting.
 */

import { Vector3, Mesh, MeshBuilder, StandardMaterial, Color3, Scene, Ray, Constants } from '@babylonjs/core';
import { AdvancedDynamicTexture, TextBlock, Control } from '@babylonjs/gui';
import {
  getLogger,
  ExceptionHandler,
  getStateValues,
  type ClientEntity,
  type BlockType,
  type Vector3Color,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ChunkService } from './ChunkService';
import type { PlayerService } from './PlayerService';
import type { EntityService } from './EntityService';
import type { Block } from '@nimbus/shared';
import type { ClientBlock } from '../types/ClientBlock';
import { mergeBlockModifier } from '../utils/BlockModifierMerge';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('SelectService');

/**
 * Minimum distance from camera to start selecting blocks (in block units)
 * Prevents selecting blocks too close to the camera/player
 */
const MIN_SELECT_DISTANCE = 2.0;

/**
 * Selection modes
 */
export enum SelectMode {
  /** No selection */
  NONE = 'NONE',

  /** Only interactive blocks (block.metadata.interactive OR currentModifier.physics.interactive) */
  INTERACTIVE = 'INTERACTIVE',

  /** Any solid block */
  BLOCK = 'BLOCK',

  /** Only AIR blocks (empty spaces) */
  AIR = 'AIR',

  /** Any block or AIR if no block found */
  ALL = 'ALL',
}


/**
 * SelectService - Manages block selection
 *
 * Features:
 * - Raycasting from player position in view direction
 * - Multiple selection modes
 * - Distance-based selection (radius)
 * - AIR block creation for empty spaces
 * - Auto-select mode with visual highlighting
 */
export class SelectService {
  private appContext: AppContext;
  private chunkService: ChunkService;
  private playerService: PlayerService;
  private entityService?: EntityService;
  private scene?: Scene;

  // Auto-select mode
  private _autoSelectMode: SelectMode = SelectMode.NONE;
  private currentSelectedBlock: ClientBlock | null = null;
  private currentSelectedEntity: ClientEntity | null = null;

  // Highlight rendering
  private highlightMesh?: Mesh;
  private highlightMaterial?: StandardMaterial;

  // Label rendering
  private guiTexture?: AdvancedDynamicTexture;
  private labelTextBlock?: TextBlock;

  // Edit block selection (green highlight)
  private selectedEditBlock: Vector3 | null = null;
  private editHighlightMesh?: Mesh;
  private editHighlightMaterial?: StandardMaterial;

  // Model selector (multi-block selection with custom color per element)
  private modelSelectorEnabled: boolean = false;
  private modelSelectorVisible: boolean = false;
  private modelSelectorWatchBlocks: string | null = null; // Source filter: null = disabled, string = only blocks with matching source
  private modelSelectorDefaultColor: string = '#ffff00'; // Default color for new blocks from watchBlocks
  private modelSelectorCoordinates: Vector3Color[] = [];
  private modelSelectorMeshes: Map<string, { mesh: Mesh; material: StandardMaterial }> = new Map();

  // Cached player properties (updated via event)
  private playerEyeHeight: number = 1.6; // Default value
  private playerSelectionRadius: number = 5.0; // Default value (fallback)

  /**
   * Get current selection radius based on player movement state
   * Selection radius varies by state (e.g., shorter in crouch, longer in fly)
   * Uses cached value from PlayerEntity (updated on state change)
   */
  private getSelectionRadius(): number {
    const playerEntity = this.playerService.getPlayerEntity();
    return playerEntity.cachedSelectionRadius;
  }

  constructor(
    appContext: AppContext,
    chunkService: ChunkService,
    playerService: PlayerService,
    scene?: Scene,
    entityService?: EntityService
  ) {
    this.appContext = appContext;
    this.chunkService = chunkService;
    this.playerService = playerService;
    this.entityService = entityService;
    this.scene = scene;

    // Initialize player properties from PlayerInfo default state values
    // Note: selectionRadius is now state-dependent (via getSelectionRadius())
    // eyeHeight is also state-dependent but we keep a fallback cache
    if (appContext.playerInfo) {
      const defaultValues = getStateValues(appContext.playerInfo, 'default');
      this.playerEyeHeight = defaultValues.eyeHeight;
      this.playerSelectionRadius = defaultValues.selectionRadius; // Fallback only
    }

    // Subscribe to PlayerInfo updates
    playerService.on('playerInfo:updated', (info: import('@nimbus/shared').PlayerInfo) => {
      // Update cached eye height (state-dependent value is fetched dynamically)
      const defaultValues = getStateValues(info, 'default');
      this.playerEyeHeight = defaultValues.eyeHeight;
      this.playerSelectionRadius = defaultValues.selectionRadius; // Fallback only
      logger.debug('SelectService: PlayerInfo updated');
    });

    // Initialize highlighting if scene is available
    if (scene) {
      this.initializeHighlight();
    }

    logger.debug('SelectService initialized');
  }

  /**
   * Get selected block based on mode, position, rotation, and radius
   *
   * @param mode Selection mode
   * @param position Player position (world coordinates)
   * @param rotation Camera rotation (pitch, yaw, roll in radians)
   * @param radius Maximum search distance
   * @returns Selected ClientBlock or null
   */
  getSelectedBlock(
    mode: SelectMode,
    position: Vector3,
    rotation: Vector3,
    radius: number
  ): ClientBlock | null {
    try {
      // NONE mode returns immediately
      if (mode === SelectMode.NONE) {
        return null;
      }

      // Calculate ray direction from rotation
      const direction = this.calculateRayDirection(rotation);

      // Perform raycasting
      return this.raycast(mode, position, direction, radius);
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.getSelectedBlock', {
        mode,
        position,
        rotation,
        radius,
      });
      return null;
    }
  }

  /**
   * Calculate ray direction from camera rotation
   *
   * @param rotation Camera rotation (pitch, yaw, roll)
   * @returns Normalized direction vector
   */
  private calculateRayDirection(rotation: Vector3): Vector3 {
    // rotation.x = pitch (up/down)
    // rotation.y = yaw (left/right)
    // rotation.z = roll (usually 0)

    const pitch = rotation.x;
    const yaw = rotation.y;

    // Convert rotation to direction vector
    const direction = new Vector3(
      Math.sin(yaw) * Math.cos(pitch),  // x
      -Math.sin(pitch),                  // y (negative because Babylon.js Y is up)
      Math.cos(yaw) * Math.cos(pitch)   // z
    );

    return direction.normalize();
  }

  /**
   * Perform raycasting using DDA algorithm (Digital Differential Analyzer)
   *
   * @param mode Selection mode
   * @param origin Ray origin (player position)
   * @param direction Ray direction (normalized)
   * @param maxDistance Maximum ray distance
   * @returns ClientBlock or null
   */
  private raycast(
    mode: SelectMode,
    origin: Vector3,
    direction: Vector3,
    maxDistance: number
  ): ClientBlock | null {
    // Step size for ray marching (smaller = more accurate, slower)
    const stepSize = 0.1;

    // Calculate step increments
    const stepX = direction.x * stepSize;
    const stepY = direction.y * stepSize;
    const stepZ = direction.z * stepSize;

    // Start ray at minimum distance to avoid selecting blocks too close to camera
    const startDistance = MIN_SELECT_DISTANCE;

    // Current position along the ray (start at minimum distance)
    let currentX = origin.x + direction.x * startDistance;
    let currentY = origin.y + direction.y * startDistance;
    let currentZ = origin.z + direction.z * startDistance;

    // Track distance traveled (starting from MIN_SELECT_DISTANCE)
    let distance = startDistance;

    // Variables to track last AIR position (for ALL mode fallback)
    let lastAirBlock: { x: number; y: number; z: number } | null = null;

    // March along the ray
    while (distance <= maxDistance) {
      // Get current block coordinates (floor to get block position)
      const blockX = Math.floor(currentX);
      const blockY = Math.floor(currentY);
      const blockZ = Math.floor(currentZ);

      // Query block at this position
      const clientBlock = this.chunkService.getBlockAt(blockX, blockY, blockZ);

      if (clientBlock && clientBlock.block) {
        // Found a solid block
        const block = clientBlock.block;

        // Check if block matches the selection mode
        if (this.matchesMode(mode, clientBlock, false)) {
          return clientBlock;
        }
      } else {
        // Empty space (AIR)
        lastAirBlock = { x: blockX, y: blockY, z: blockZ };

        // For AIR mode, return immediately
        if (mode === SelectMode.AIR) {
          return this.createAirClientBlock(blockX, blockY, blockZ);
        }
      }

      // Step forward along the ray
      currentX += stepX;
      currentY += stepY;
      currentZ += stepZ;
      distance += stepSize;
    }

    // No block found within radius
    // For ALL mode, return last AIR position if we found one
    if (mode === SelectMode.ALL && lastAirBlock) {
      return this.createAirClientBlock(
        lastAirBlock.x,
        lastAirBlock.y,
        lastAirBlock.z
      );
    }

    return null;
  }

  /**
   * Check if a block matches the selection mode
   *
   * @param mode Selection mode
   * @param clientBlock ClientBlock to check (contains merged modifier)
   * @param isAir Whether this is an AIR block
   * @returns True if block matches mode
   */
  private matchesMode(mode: SelectMode, clientBlock: ClientBlock, isAir: boolean): boolean {
    switch (mode) {
      case SelectMode.NONE:
        return false;

      case SelectMode.INTERACTIVE:
        // Check currentModifier.physics.interactive (from BlockType or Block modifier)
        if (isAir) return false;
        return clientBlock.currentModifier?.physics?.interactive === true;

      case SelectMode.BLOCK:
        // Any solid block
        return !isAir;

      case SelectMode.AIR:
        // Only AIR blocks
        return isAir;

      case SelectMode.ALL:
        // Any block or AIR
        return true;

      default:
        return false;
    }
  }

  /**
   * Create an AIR ClientBlock at the given position
   *
   * @param x World X coordinate
   * @param y World Y coordinate
   * @param z World Z coordinate
   * @returns New AIR ClientBlock with BlockTypeId 0
   */
  private createAirClientBlock(x: number, y: number, z: number): ClientBlock {
    // Get AIR blockType from registry (id 0)
    const blockTypeService = this.appContext.services.blockType;
    let airBlockType: BlockType | undefined = blockTypeService?.getBlockTypeSync('0');

    // Fallback: Create minimal AIR BlockType if not found
    if (!airBlockType) {
      airBlockType = {
        id: '0',
        initialStatus: 0,
        modifiers: {
          0: {
            visibility: {
              shape: 0, // INVISIBLE
            },
            physics: {
              solid: false,
            },
          },
        },
      };
    }

    // Get chunk coordinates
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    const cx = Math.floor(x / chunkSize);
    const cz = Math.floor(z / chunkSize);

    // Create Block instance
    const block: Block = {
      position: { x, y, z },
      blockTypeId: '0', // AIR block type
    };

    // Get merged modifier using utility function
    const currentModifier = mergeBlockModifier(this.appContext, block, airBlockType);

    // Create ClientBlock
    const clientBlock: ClientBlock = {
      block,
      chunk: { cx, cz },
      blockType: airBlockType,
      currentModifier,
      isVisible: true,
      isDirty: false,
      lastUpdate: Date.now(),
    };

    return clientBlock;
  }

  /**
   * Get selected block using current player position and rotation
   *
   * FIX: Raycast now starts from eye level (player.position + eyeHeight)
   * instead of from feet (player.position). This gives correct block selection
   * matching where the player is actually looking.
   *
   * @param mode Selection mode
   * @param radius Maximum search distance
   * @returns ClientBlock or null
   */
  getSelectedBlockFromPlayer(
    mode: SelectMode,
    radius: number = 5.0
  ): ClientBlock | null {
    try {
      // Get player feet position
      const feetPosition = this.playerService.getPosition();

      // FIX: Start raycast from eye level, not feet!
      const eyePosition = feetPosition.clone();
      eyePosition.y += this.playerEyeHeight; // Add cached eye height

      // Get camera rotation from CameraService via reflection
      // (PlayerService doesn't expose rotation directly)
      const cameraService = (this.playerService as any).cameraService;
      if (!cameraService) {
        logger.warn('CameraService not available');
        return null;
      }

      const rotation = cameraService.getRotation();

      // Use eye position for raycast (not feet!)
      return this.getSelectedBlock(mode, eyePosition, rotation, radius);
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.getSelectedBlockFromPlayer', {
        mode,
        radius,
      });
      return null;
    }
  }

  /**
   * Get selected entity using BabylonJS raycasting
   *
   * Uses scene.pickWithRay() to find interactive entities in front of the camera.
   *
   * @param radius Maximum search distance
   * @returns ClientEntity or null
   */
  getSelectedEntityFromPlayer(radius: number = 5.0): ClientEntity | null {
    try {
      if (!this.scene || !this.entityService) {
        return null;
      }

      // Get player eye position
      const feetPosition = this.playerService.getPosition();
      const eyePosition = feetPosition.clone();
      eyePosition.y += this.playerEyeHeight;

      // Get camera rotation
      const cameraService = (this.playerService as any).cameraService;
      if (!cameraService) {
        return null;
      }

      const rotation = cameraService.getRotation();

      // Calculate ray direction from rotation
      const direction = this.calculateRayDirection(rotation);

      // Create ray from eye position
      const ray = new Ray(eyePosition, direction, radius);

      // Pick with ray - only check visible meshes
      const pickInfo = this.scene.pickWithRay(ray, (mesh) => {
        // Only pick entity meshes (not blocks, terrain, etc.)
        if (!mesh.name.startsWith('entity_') || !mesh.isVisible) {
          return false;
        }

        // Get entity ID from metadata
        const meshEntityId = mesh.metadata?.entityId;
        if (!meshEntityId) {
          return false;
        }

        // Get entity from cache to check properties
        const entities = this.entityService!.getAllEntities();
        const entity = entities.find(e => e.id === meshEntityId);

        if (!entity) {
          return false;
        }

        // Filter by distance (early exit for entities outside radius)
        const entityPos = entity.currentPosition;
        const distance = Math.sqrt(
          Math.pow(entityPos.x - eyePosition.x, 2) +
          Math.pow(entityPos.y - eyePosition.y, 2) +
          Math.pow(entityPos.z - eyePosition.z, 2)
        );
        if (distance > radius) {
          return false;
        }

        // Filter out player entities BEFORE picking
        if (entity.entity.controlledBy === 'player') {
          return false;
        }

        // Only pick interactive entities
        if (!entity.entity.interactive) {
          return false;
        }

        return true;
      });

      // If raycast hit an entity, return it
      if (pickInfo && pickInfo.hit && pickInfo.pickedMesh) {
        const entityId = pickInfo.pickedMesh.metadata?.entityId;
        if (entityId) {
          const entities = this.entityService.getAllEntities();
          const entity = entities.find(e => e.id === entityId);
          if (entity) {
            logger.debug('Interactive entity selected (raycast)', {
              entityId: entity.id,
              name: entity.entity.name,
              distance: pickInfo.distance?.toFixed(2),
            });
            return entity;
          }
        }
      }

      // Fallback: If raycast missed, find nearest entity in view cone
      // This provides tolerance for entities not exactly in crosshair
      const nearestEntity = this.findNearestEntityInViewCone(eyePosition, direction, radius);

      if (nearestEntity) {
        logger.debug('Interactive entity selected (cone fallback)', {
          entityId: nearestEntity.id,
          name: nearestEntity.entity.name,
        });
      }

      return nearestEntity;
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.getSelectedEntityFromPlayer', {
        radius,
      });
      return null;
    }
  }

  /**
   * Initialize highlight mesh for selected blocks
   */
  private initializeHighlight(): void {
    if (!this.scene) {
      logger.warn('Cannot initialize highlight: scene not available');
      return;
    }

    try {
      // Create wireframe box for highlighting (yellow)
      this.highlightMesh = MeshBuilder.CreateBox(
        'blockHighlight',
        { size: 1.0 },
        this.scene
      );

      // Create highlight material (white semi-transparent block)
      this.highlightMaterial = new StandardMaterial('highlightMaterial', this.scene);
      this.highlightMaterial.diffuseColor = new Color3(1, 1, 1); // White
      this.highlightMaterial.emissiveColor = new Color3(0.5, 0.5, 0.5); // Slight emission for visibility
      this.highlightMaterial.alpha = 0.3; // Semi-transparent
      this.highlightMaterial.wireframe = false;
      this.highlightMaterial.disableDepthWrite = true; // Don't write to depth buffer

      this.highlightMesh.material = this.highlightMaterial;
      this.highlightMesh.isPickable = false;
      this.highlightMesh.renderingGroupId = RENDERING_GROUPS.SELECTION_OVERLAY; // Render on top
      this.highlightMesh.setEnabled(false); // Hidden by default

      // Enable edge rendering for thick, opaque borders
      this.highlightMesh.enableEdgesRendering();
      this.highlightMesh.edgesWidth = 3.0; // Thick edges
      this.highlightMesh.edgesColor = new Color3(1, 1, 1).toColor4(1.0); // Opaque white edges

      // Create wireframe box for edit block highlighting (green)
      this.editHighlightMesh = MeshBuilder.CreateBox(
        'editBlockHighlight',
        { size: 1.0 },
        this.scene
      );

      // Create edit highlight material (red semi-transparent block)
      this.editHighlightMaterial = new StandardMaterial('editHighlightMaterial', this.scene);
      this.editHighlightMaterial.diffuseColor = new Color3(1, 0, 0); // Red
      this.editHighlightMaterial.emissiveColor = new Color3(0.5, 0, 0); // Red emission for visibility
      this.editHighlightMaterial.alpha = 0.3; // Semi-transparent
      this.editHighlightMaterial.wireframe = false;
      this.editHighlightMaterial.disableDepthWrite = true; // Don't write to depth buffer

      this.editHighlightMesh.material = this.editHighlightMaterial;
      this.editHighlightMesh.isPickable = false;
      this.editHighlightMesh.renderingGroupId = RENDERING_GROUPS.SELECTION_OVERLAY; // Render on top
      this.editHighlightMesh.setEnabled(false); // Hidden by default

      // Enable edge rendering for thick, opaque borders
      this.editHighlightMesh.enableEdgesRendering();
      this.editHighlightMesh.edgesWidth = 3.0; // Thick edges
      this.editHighlightMesh.edgesColor = new Color3(1, 0, 0).toColor4(1.0); // Opaque red edges

      // Initialize BabylonJS GUI for label display
      this.guiTexture = AdvancedDynamicTexture.CreateFullscreenUI('UI', true, this.scene);

      // Create text block for item/block name display
      this.labelTextBlock = new TextBlock('blockLabel');
      this.labelTextBlock.text = '';
      this.labelTextBlock.color = 'white';
      this.labelTextBlock.fontSize = 16;
      this.labelTextBlock.fontWeight = 'bold';
      this.labelTextBlock.outlineWidth = 2;
      this.labelTextBlock.outlineColor = 'black';
      this.labelTextBlock.isVisible = false;
      this.labelTextBlock.verticalAlignment = Control.VERTICAL_ALIGNMENT_TOP;
      this.labelTextBlock.textVerticalAlignment = Control.VERTICAL_ALIGNMENT_TOP;

      this.guiTexture.addControl(this.labelTextBlock);

      logger.debug('Highlight meshes and label GUI initialized');
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.initializeHighlight');
    }
  }

  /**
   * Get current auto-select mode
   */
  get autoSelectMode(): SelectMode {
    return this._autoSelectMode;
  }

  /**
   * Get current auto-select mode (method for compatibility)
   */
  getAutoSelectMode(): SelectMode {
    return this._autoSelectMode;
  }

  /**
   * Set auto-select mode
   *
   * @param mode Selection mode for auto-select
   */
  set autoSelectMode(mode: SelectMode) {
    this._autoSelectMode = mode;

    // Hide highlight when mode is NONE
    if (mode === SelectMode.NONE) {
      this.hideHighlight();
      this.currentSelectedBlock = null;
    }

    logger.debug('Auto-select mode set', { mode });
  }

  /**
   * Get auto-select radius (state-dependent)
   * Returns current selection radius based on movement state
   */
  getAutoSelectRadius(): number {
    return this.getSelectionRadius();
  }

  /**
   * Set auto-select radius (updates PlayerInfo)
   *
   * @param radius Maximum search distance for auto-select
   */
  setAutoSelectRadius(radius: number): void {
    // Clamp between 1 and 20
    const clampedRadius = Math.max(1.0, Math.min(radius, 20.0));
    this.playerSelectionRadius = clampedRadius;

    // Update PlayerInfo if available
    if (this.appContext.playerInfo) {
      this.appContext.playerInfo.selectionRadius = clampedRadius;
      // Trigger update event via PlayerService
      this.playerService.updatePlayerInfo({ selectionRadius: clampedRadius });
    }
  }

  /**
   * Get currently selected block (from auto-select)
   */
  getCurrentSelectedBlock(): ClientBlock | null {
    return this.currentSelectedBlock;
  }

  /**
   * Get currently selected entity (from auto-select)
   */
  getCurrentSelectedEntity(): ClientEntity | null {
    return this.currentSelectedEntity;
  }

  /**
   * Update auto-select (called each frame)
   *
   * @param deltaTime Time since last frame in seconds
   */
  update(deltaTime: number): void {
    // Skip if auto-select is disabled
    if (this._autoSelectMode === SelectMode.NONE) {
      return;
    }

    try {
      // Priority 1: Try to select entity first (entities are closer to camera typically)
      // Get current selection radius based on movement state
      const selectionRadius = this.getSelectionRadius();

      let selectedEntity: ClientEntity | null = null;
      if (this._autoSelectMode === SelectMode.INTERACTIVE && this.scene && this.entityService) {
        selectedEntity = this.getSelectedEntityFromPlayer(selectionRadius);
      }

      // Priority 2: If no entity selected, try to select block
      let selectedBlock: ClientBlock | null = null;
      if (!selectedEntity) {
        selectedBlock = this.getSelectedBlockFromPlayer(
          this._autoSelectMode,
          selectionRadius
        );
      }

      // Update current selections
      this.currentSelectedEntity = selectedEntity;
      this.currentSelectedBlock = selectedBlock;

      // Update highlight (entity has priority over block)
      if (selectedEntity) {
        this.showEntityHighlight(selectedEntity);
      } else if (selectedBlock) {
        this.showHighlight(selectedBlock);
      } else {
        this.hideHighlight();
      }

      // Update edit block highlight
      if (this.selectedEditBlock) {
        this.showEditHighlight(this.selectedEditBlock);
      } else {
        this.hideEditHighlight();
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.update');
    }
  }

  /**
   * Show highlight at block position
   *
   * @param clientBlock Block to highlight
   */
  private showHighlight(clientBlock: ClientBlock): void {
    if (!this.highlightMesh || !this.scene) {
      return;
    }

    const pos = clientBlock.block.position;

    // Position highlight at block center
    this.highlightMesh.position.set(
      pos.x + 0.5,
      pos.y + 0.5,
      pos.z + 0.5
    );

    // Scale slightly larger than block for better visibility
    const scale = 1.02;
    this.highlightMesh.scaling.set(scale, scale, scale);

    // Enable highlight
    this.highlightMesh.setEnabled(true);

    // Show label if block has displayName
    this.showLabel(clientBlock);
  }

  /**
   * Hide highlight
   */
  private hideHighlight(): void {
    if (this.highlightMesh) {
      this.highlightMesh.setEnabled(false);
    }
    this.hideLabel();
  }

  /**
   * Show highlight at entity position
   *
   * Uses the same highlight mesh as blocks but scales/positions for entity bounding box.
   *
   * @param entity Entity to highlight
   */
  private showEntityHighlight(entity: ClientEntity): void {
    if (!this.highlightMesh || !this.scene) {
      return;
    }

    // Get entity position (ClientEntity uses currentPosition)
    const pos = entity.currentPosition;

    // Get entity model dimensions (use model height/width if available)
    const height = entity.model?.dimensions?.walk?.height || 1.8; // Default player height
    const width = entity.model?.dimensions?.walk?.width || 0.6; // Default player width

    // Position highlight at entity center (entity.currentPosition is at feet)
    this.highlightMesh.position.set(
      pos.x,
      pos.y + height / 2, // Center vertically
      pos.z
    );

    // Scale to entity bounding box
    const scale = 1.05; // Slightly larger for visibility
    this.highlightMesh.scaling.set(
      width * scale,
      height * scale,
      width * scale
    );

    // Enable highlight
    this.highlightMesh.setEnabled(true);

    // Show label with entity name
    this.showEntityLabel(entity);
  }

  /**
   * Show label for selected entity
   *
   * Shows entity name above the entity in screen space.
   *
   * @param entity Entity to show label for
   */
  private showEntityLabel(entity: ClientEntity): void {
    if (!this.guiTexture || !this.labelTextBlock || !this.scene) {
      return;
    }

    // Use entity name for label (from entity.entity.name)
    const displayName = entity.entity.name || entity.id;

    // Position label above entity (use currentPosition)
    const pos = entity.currentPosition;
    const height = entity.model?.dimensions?.walk?.height || 1.8;
    const labelWorldPos = new Vector3(pos.x, pos.y + height + 0.5, pos.z);

    // Update label text
    this.labelTextBlock.text = displayName;

    // Link to world position
    this.labelTextBlock.linkWithMesh(null); // Unlink first

    // Create temporary mesh for label positioning
    const tempMesh = MeshBuilder.CreateBox('labelAnchor', { size: 0.1 }, this.scene);
    tempMesh.position.copyFrom(labelWorldPos);
    tempMesh.isVisible = false;
    tempMesh.isPickable = false;

    this.labelTextBlock.linkWithMesh(tempMesh);
    this.labelTextBlock.linkOffsetY = -30; // Offset above entity

    // Show label
    this.labelTextBlock.isVisible = true;
  }

  /**
   * Show label for selected block
   *
   * Only shows label if block has title metadata.
   * Label is positioned above the block in screen space.
   *
   * @param clientBlock Block to show label for
   */
  private showLabel(clientBlock: ClientBlock): void {
    if (!this.labelTextBlock || !this.scene || !this.highlightMesh) {
      return;
    }

    // Get title from metadata
    const title = clientBlock.block.metadata?.title;
    if (!title) {
      // No title - hide label
      this.hideLabel();
      return;
    }

    // Set label text
    this.labelTextBlock.text = title;

    // Link label to block position (above the block)
    const blockCenter = this.highlightMesh.position.clone();
    blockCenter.y += 0.7; // Position above block

    // Link label to 3D position (will follow block in screen space)
    this.labelTextBlock.linkWithMesh(this.highlightMesh);
    this.labelTextBlock.linkOffsetY = -60; // Offset above block in pixels

    // Show label
    this.labelTextBlock.isVisible = true;

    logger.debug('Label shown', {
      title,
      position: clientBlock.block.position,
    });
  }

  /**
   * Hide label
   */
  private hideLabel(): void {
    if (this.labelTextBlock) {
      this.labelTextBlock.isVisible = false;
      this.labelTextBlock.linkWithMesh(null); // Unlink from mesh
    }
  }

  /**
   * Set highlight color
   *
   * @param color Color as Color3 or hex string
   */
  setHighlightColor(color: Color3 | string): void {
    if (!this.highlightMaterial) {
      return;
    }

    if (typeof color === 'string') {
      // Parse hex color
      this.highlightMaterial.emissiveColor = Color3.FromHexString(color);
    } else {
      this.highlightMaterial.emissiveColor = color;
    }
  }

  /**
   * Set selected edit block (green highlight)
   *
   * @param x World X coordinate (optional, null to clear)
   * @param y World Y coordinate (optional)
   * @param z World Z coordinate (optional)
   */
  setSelectedEditBlock(x?: number, y?: number, z?: number): void {
    try {
      // Clear selection if no parameters provided
      if (x === undefined || y === undefined || z === undefined) {
        this.selectedEditBlock = null;
        this.hideEditHighlight();
        logger.debug('Edit block selection cleared');
        return;
      }

      // Set new selection
      this.selectedEditBlock = new Vector3(x, y, z);
      logger.debug('Edit block selected', { x, y, z });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.setSelectedEditBlock', { x, y, z });
    }
  }

  /**
   * Get selected edit block position
   *
   * @returns Vector3 with block coordinates or null
   */
  getSelectedEditBlock(): { x: number; y: number; z: number } | null {
    if (!this.selectedEditBlock) {
      return null;
    }

    return {
      x: this.selectedEditBlock.x,
      y: this.selectedEditBlock.y,
      z: this.selectedEditBlock.z,
    };
  }

  /**
   * Show edit highlight at block position (green)
   *
   * @param position Block position as Vector3
   */
  private showEditHighlight(position: Vector3): void {
    if (!this.editHighlightMesh || !this.scene) {
      return;
    }

    // Position highlight at block center
    this.editHighlightMesh.position.set(
      position.x + 0.5,
      position.y + 0.5,
      position.z + 0.5
    );

    // Scale slightly larger than block for better visibility
    const scale = 1.02;
    this.editHighlightMesh.scaling.set(scale, scale, scale);

    // Enable highlight
    this.editHighlightMesh.setEnabled(true);
  }

  /**
   * Hide edit highlight
   */
  private hideEditHighlight(): void {
    if (this.editHighlightMesh) {
      this.editHighlightMesh.setEnabled(false);
    }
  }

  /**
   * Enable model selector
   *
   * Shows multiple blocks as selected with custom color per element.
   * If already enabled, automatically disables and re-enables with new parameters.
   *
   * @param defaultColor Default color for new blocks added via watchBlocks (hex string)
   * @param watchBlocks Source filter: null = disabled, string = only add blocks with matching source
   * @param show Make visible immediately
   * @param selected Initial list of coordinates with colors to select
   */
  enableModelSelector(
    defaultColor: string,
    watchBlocks: string | null,
    show: boolean,
    selected: Vector3Color[]
  ): void {
    try {
      // If already enabled, disable first
      if (this.modelSelectorEnabled) {
        this.disableModelSelector();
      }

      // Set state
      this.modelSelectorEnabled = true;
      this.modelSelectorDefaultColor = defaultColor;
      this.modelSelectorWatchBlocks = watchBlocks;
      this.modelSelectorVisible = show;

      // Initialize coordinates
      this.modelSelectorCoordinates = [...selected];

      // Create material if not exists
      if (!this.scene) {
        logger.warn('Cannot initialize model selector: scene not available');
        return;
      }

      // Create meshes for all coordinates
      this.updateModelSelectorMeshes();

      logger.info('Model selector enabled', {
        defaultColor,
        watchBlocks,
        show,
        blockCount: selected.length,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.enableModelSelector');
    }
  }

  /**
   * Disable model selector
   *
   * Clears all meshes and coordinates.
   */
  disableModelSelector(): void {
    try {
      this.modelSelectorEnabled = false;
      this.modelSelectorVisible = false;
      this.modelSelectorWatchBlocks = null;
      this.modelSelectorCoordinates = [];

      // Dispose all meshes and materials
      this.modelSelectorMeshes.forEach(({ mesh, material }) => {
        mesh.dispose();
        material.dispose();
      });
      this.modelSelectorMeshes.clear();

      logger.info('Model selector disabled');
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.disableModelSelector');
    }
  }

  /**
   * Show or hide model selector
   *
   * @param visible Visibility state
   */
  showModelSelector(visible: boolean): void {
    try {
      if (!this.modelSelectorEnabled) {
        logger.warn('Cannot show model selector: not enabled');
        return;
      }

      this.modelSelectorVisible = visible;

      // Update mesh visibility
      this.modelSelectorMeshes.forEach(({ mesh }) => {
        mesh.setEnabled(visible);
      });

      logger.info('Model selector visibility changed', { visible });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.showModelSelector');
    }
  }

  /**
   * Add coordinates to model selector
   *
   * @param selected List of coordinates with colors to add
   */
  addToModelSelector(selected: Vector3Color[]): void {
    try {
      if (!this.modelSelectorEnabled) {
        logger.warn('Cannot add to model selector: not enabled');
        return;
      }

      // Add new coordinates (avoid duplicates based on position)
      for (const coord of selected) {
        const exists = this.modelSelectorCoordinates.some(
          c => c.x === coord.x && c.y === coord.y && c.z === coord.z
        );
        if (!exists) {
          this.modelSelectorCoordinates.push({ ...coord });
        }
      }

      // Update meshes
      this.updateModelSelectorMeshes();

      logger.info('Added coordinates to model selector', {
        addedCount: selected.length,
        totalCount: this.modelSelectorCoordinates.length,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.addToModelSelector');
    }
  }

  /**
   * Remove coordinates from model selector
   *
   * @param selected List of coordinates to remove (color is ignored for removal)
   */
  removeFromModelSelector(selected: Vector3Color[]): void {
    try {
      if (!this.modelSelectorEnabled) {
        logger.warn('Cannot remove from model selector: not enabled');
        return;
      }

      // Remove coordinates (only check position, ignore color)
      this.modelSelectorCoordinates = this.modelSelectorCoordinates.filter(coord => {
        return !selected.some(
          s => s.x === coord.x && s.y === coord.y && s.z === coord.z
        );
      });

      // Update meshes
      this.updateModelSelectorMeshes();

      logger.info('Removed coordinates from model selector', {
        removedCount: selected.length,
        totalCount: this.modelSelectorCoordinates.length,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.removeFromModelSelector');
    }
  }

  /**
   * Get all coordinates in model selector
   *
   * @returns List of Vector3Color coordinates
   */
  getModelSelectorCoordinates(): Vector3Color[] {
    return [...this.modelSelectorCoordinates];
  }

  /**
   * Check if model selector is enabled
   *
   * @returns True if model selector is enabled
   */
  isModelSelectorEnabled(): boolean {
    return this.modelSelectorEnabled;
  }

  /**
   * Check if model selector is visible
   *
   * @returns True if model selector is visible
   */
  isModelSelectorVisible(): boolean {
    return this.modelSelectorVisible;
  }

  /**
   * Get model selector watch blocks filter
   *
   * @returns Source filter string or null if disabled
   */
  getModelSelectorWatchBlocks(): string | null {
    return this.modelSelectorWatchBlocks;
  }

  /**
   * Toggle model selector visibility
   *
   * Convenience method to toggle visibility state.
   */
  toggleModelSelectorVisibility(): void {
    if (!this.modelSelectorEnabled) {
      logger.warn('Cannot toggle model selector visibility: not enabled');
      return;
    }

    this.showModelSelector(!this.modelSelectorVisible);
    logger.info('Model selector visibility toggled', {
      visible: this.modelSelectorVisible,
    });
  }

  /**
   * Move all model selector blocks by an offset
   *
   * Moves all coordinates and updates meshes accordingly.
   * Keeps the color of each element.
   *
   * @param offset Vector3 offset to move blocks by
   */
  moveModelSelector(offset: Vector3): void {
    try {
      if (!this.modelSelectorEnabled) {
        logger.warn('Cannot move model selector: not enabled');
        return;
      }

      if (this.modelSelectorCoordinates.length === 0) {
        logger.warn('Cannot move model selector: no coordinates');
        return;
      }

      // Move all coordinates by offset (keep color)
      this.modelSelectorCoordinates = this.modelSelectorCoordinates.map(coord => {
        return {
          x: coord.x + offset.x,
          y: coord.y + offset.y,
          z: coord.z + offset.z,
          color: coord.color,
        };
      });

      // Update meshes with new coordinates
      this.updateModelSelectorMeshes();

      logger.info('Model selector moved', {
        offset: { x: offset.x, y: offset.y, z: offset.z },
        blockCount: this.modelSelectorCoordinates.length,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.moveModelSelector');
    }
  }

  /**
   * Update model selector meshes based on current coordinates
   *
   * Creates or disposes meshes to match coordinate list.
   * Each coordinate has its own material with individual color.
   */
  private updateModelSelectorMeshes(): void {
    if (!this.scene) {
      return;
    }

    // Dispose all existing meshes and materials
    this.modelSelectorMeshes.forEach(({ mesh, material }) => {
      mesh.dispose();
      material.dispose();
    });
    this.modelSelectorMeshes.clear();

    // Create mesh for each coordinate with individual color
    for (const coord of this.modelSelectorCoordinates) {
      const key = `${coord.x}_${coord.y}_${coord.z}`;

      // Create mesh
      const mesh = MeshBuilder.CreateBox(
        `modelSelector_${key}`,
        { size: 1.0 },
        this.scene
      );

      // Create individual material for this mesh with its specific color
      const material = new StandardMaterial(`modelSelectorMaterial_${key}`, this.scene);
      material.wireframe = false;
      material.disableDepthWrite = true;
      material.alpha = 0.3;

      // Parse color from hex string
      const color3 = Color3.FromHexString(coord.color);
      material.diffuseColor = color3;
      material.emissiveColor = color3.scale(0.5);

      mesh.material = material;
      mesh.isPickable = false;
      mesh.renderingGroupId = RENDERING_GROUPS.SELECTION_OVERLAY;

      // Position at block center
      mesh.position.set(coord.x + 0.5, coord.y + 0.5, coord.z + 0.5);

      // Scale slightly larger
      const scale = 1.02;
      mesh.scaling.set(scale, scale, scale);

      // Enable edge rendering with color
      mesh.enableEdgesRendering();
      mesh.edgesWidth = 3.0;
      mesh.edgesColor = color3.toColor4(1.0);

      // Set visibility
      mesh.setEnabled(this.modelSelectorVisible);

      // Store mesh and material
      this.modelSelectorMeshes.set(key, { mesh, material });
    }

    logger.debug('Model selector meshes updated', {
      meshCount: this.modelSelectorMeshes.size,
    });
  }

  /**
   * Handle new block from network (for watchBlocks mode)
   *
   * Called by NetworkService when new blocks are received in EDITOR mode.
   * Filters blocks by source field and converts them to Vector3Color using the default color.
   *
   * @param blocks List of new blocks with position and optional source field
   */
  onNewBlocks(blocks: { x: number; y: number; z: number; source?: string }[]): void {
    try {
      if (!this.modelSelectorEnabled || !this.modelSelectorWatchBlocks) {
        return;
      }

      // Filter blocks by source if watchBlocks is set
      const filteredBlocks = blocks.filter(b => {
        // If source filter is set, only include blocks with matching source
        if (this.modelSelectorWatchBlocks) {
          return b.source === this.modelSelectorWatchBlocks;
        }
        // If no source filter, include all blocks (backward compatibility)
        return true;
      });

      if (filteredBlocks.length === 0) {
        logger.debug('No blocks matched source filter', {
          sourceFilter: this.modelSelectorWatchBlocks,
          totalBlocks: blocks.length,
        });
        return;
      }

      // Convert blocks to Vector3Color using default color
      const coordinates: Vector3Color[] = filteredBlocks.map(b => ({
        x: b.x,
        y: b.y,
        z: b.z,
        color: this.modelSelectorDefaultColor,
      }));

      // Add new blocks to model selector
      this.addToModelSelector(coordinates);

      logger.debug('New blocks added to model selector', {
        blockCount: filteredBlocks.length,
        filteredOut: blocks.length - filteredBlocks.length,
        sourceFilter: this.modelSelectorWatchBlocks,
        defaultColor: this.modelSelectorDefaultColor,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.onNewBlocks');
    }
  }

  /**
   * Fire shortcut (triggered by number keys 1-9, 0)
   *
   * Sends shortcut event to server with selected block/entity and player context.
   *
   * @param shortcutNr Shortcut number (1-10, where 10 = key '0')
   */
  fireShortcut(shortcutNr: number): void {
    try {
      // Get player position
      const playerPosition = this.playerService.getPosition();

      // Get camera rotation
      const cameraService = (this.playerService as any).cameraService;
      if (!cameraService) {
        logger.warn('CameraService not available for shortcut');
        return;
      }

      const rotation = cameraService.getRotation();

      // Get selected entity (priority) or block
      const selectedEntity = this.currentSelectedEntity;
      const selectedBlock = this.currentSelectedBlock;

      // Calculate distance to selected target
      let distance: number | undefined;
      let targetPosition: { x: number; y: number; z: number } | undefined;
      let entityId: string | undefined;
      let blockId: string | undefined;
      let blockGroupId: string | undefined;
      let blockX: number | undefined;
      let blockY: number | undefined;
      let blockZ: number | undefined;

      if (selectedEntity) {
        // Entity selected
        entityId = selectedEntity.id;
        targetPosition = selectedEntity.currentPosition;
        distance = Math.sqrt(
          Math.pow(targetPosition.x - playerPosition.x, 2) +
          Math.pow(targetPosition.y - playerPosition.y, 2) +
          Math.pow(targetPosition.z - playerPosition.z, 2)
        );
      } else if (selectedBlock) {
        // Block selected
        const pos = selectedBlock.block.position;
        blockX = pos.x;
        blockY = pos.y;
        blockZ = pos.z;
        targetPosition = { x: pos.x + 0.5, y: pos.y + 0.5, z: pos.z + 0.5 }; // Block center
        blockId = selectedBlock.block.metadata?.id;
        blockGroupId = selectedBlock.block.metadata?.groupId;
        distance = Math.sqrt(
          Math.pow(targetPosition.x - playerPosition.x, 2) +
          Math.pow(targetPosition.y - playerPosition.y, 2) +
          Math.pow(targetPosition.z - playerPosition.z, 2)
        );
      }

      // Prepare params with all context
      const params: any = {
        shortcutNr,
        playerPosition: { x: playerPosition.x, y: playerPosition.y, z: playerPosition.z },
        playerRotation: { yaw: rotation.y, pitch: rotation.x }, // rotation.y = yaw, rotation.x = pitch
      };

      if (distance !== undefined) {
        params.distance = parseFloat(distance.toFixed(2));
      }

      if (targetPosition) {
        params.targetPosition = targetPosition;
      }

      if (entityId) {
        params.entityId = entityId;
      }

      // Check if network service is available
      if (!this.appContext.services.network) {
        logger.warn('NetworkService not available for shortcut');
        return;
      }

      // Send to server
      if (selectedEntity) {
        // Send as entity interaction with full context
        this.appContext.services.network.sendEntityInteraction(
          selectedEntity.id,
          'fireShortcut',
          undefined, // clickType not applicable
          params
        );
      } else if (selectedBlock) {
        // Send as block interaction
        this.appContext.services.network.sendBlockInteraction(
          blockX!,
          blockY!,
          blockZ!,
          'fireShortcut',
          params,
          blockId,
          blockGroupId
        );
      } else {
        // No selection - send shortcut without target
        // Use block interaction with position (0,0,0) as placeholder
        this.appContext.services.network.sendBlockInteraction(
          0, 0, 0,
          'fireShortcut',
          params
        );
      }

      logger.debug('Shortcut fired', {
        shortcutNr,
        hasEntity: !!selectedEntity,
        hasBlock: !!selectedBlock,
        distance,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'SelectService.fireShortcut', { shortcutNr });
    }
  }

  /**
   * Find nearest interactive entity in view cone
   *
   * Fallback method when raycast misses - provides tolerance for entity selection.
   * Checks entities within a cone in front of the camera.
   *
   * @param eyePosition Player eye position
   * @param viewDirection Camera view direction (normalized)
   * @param maxDistance Maximum search distance
   * @returns Nearest entity in view cone or null
   */
  private findNearestEntityInViewCone(
    eyePosition: Vector3,
    viewDirection: Vector3,
    maxDistance: number
  ): ClientEntity | null {
    if (!this.entityService) {
      return null;
    }

    const entities = this.entityService.getAllEntities();
    let nearestEntity: ClientEntity | null = null;
    let nearestDistance = Infinity;

    // Cone angle tolerance (in radians) - ~15 degrees
    const coneAngleTolerance = Math.PI / 12;

    for (const entity of entities) {
      // Skip non-interactive entities
      if (!entity.entity.interactive) {
        continue;
      }

      // Skip player-controlled entities
      if (entity.entity.controlledBy === 'player') {
        continue;
      }

      const entityPos = entity.currentPosition;

      // Calculate vector from eye to entity
      const toEntity = new Vector3(
        entityPos.x - eyePosition.x,
        entityPos.y - eyePosition.y,
        entityPos.z - eyePosition.z
      );

      const distance = toEntity.length();

      // Skip entities outside max distance
      if (distance > maxDistance) {
        continue;
      }

      // Normalize toEntity vector
      toEntity.normalize();

      // Calculate angle between view direction and entity direction
      const dotProduct = Vector3.Dot(viewDirection, toEntity);
      const angle = Math.acos(Math.max(-1, Math.min(1, dotProduct)));

      // Skip entities outside view cone
      if (angle > coneAngleTolerance) {
        continue;
      }

      // Track nearest entity in cone
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestEntity = entity;
      }
    }

    return nearestEntity;
  }

  dispose(): void {
    // Dispose highlight resources
    this.highlightMesh?.dispose();
    this.highlightMaterial?.dispose();
    this.editHighlightMesh?.dispose();
    this.editHighlightMaterial?.dispose();

    // Dispose model selector resources (meshes and materials)
    this.modelSelectorMeshes.forEach(({ mesh, material }) => {
      mesh.dispose();
      material.dispose();
    });
    this.modelSelectorMeshes.clear();

    this.currentSelectedBlock = null;
    this.selectedEditBlock = null;

    logger.debug('SelectService disposed');
  }
}
