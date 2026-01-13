/**
 * PlayerService - Manages player state and movement
 *
 * Handles player position, movement, and camera synchronization.
 * Initial implementation provides position/logic only (no rendering).
 */

import { Vector3 } from '@babylonjs/core';
import {
  getLogger,
  ENTITY_POSES,
  MessageType,
  PlayerMovementState,
  movementStateToKey,
  getStateValues,
  createClientEntity,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import type { PhysicsService, MovementMode } from './PhysicsService';
import type { PlayerEntity } from '../types/PlayerEntity';
import type { ModifierStack, Modifier } from './ModifierService';
import { StackName } from './ModifierService';
import type { EntityPositionUpdateMessage, EntityPositionUpdateData, PlayerMovementStateChangedEvent, VitalsData } from '@nimbus/shared';
import { EntityRenderService }  from "./EntityRenderService";
import { NotificationType } from '../types/Notification';
import type { StatusEffect } from '../types/StatusEffect';

const logger = getLogger('PlayerService');

/**
 * Event listener type
 */
type EventListener = (...args: any[]) => void;

/**
 * PlayerService - Manages player state
 *
 * Features:
 * - Player position tracking
 * - Camera synchronization
 * - Movement logic
 * - Event emission for position updates
 *
 * Note: Player rendering (third-person view) is not implemented yet
 */
export class PlayerService {
  private appContext: AppContext;
  private cameraService: CameraService;
  private physicsService?: PhysicsService;
  private entityRenderService?: EntityRenderService; // EntityRenderService

  // Player as physics entity with player info
  private playerEntity: PlayerEntity;

  // Last known position (for change detection)
  private lastPosition: { x: number; y: number; z: number };

  // Event system
  private eventListeners: Map<string, EventListener[]> = new Map();

  // View mode (ego vs third-person)
  // Stack is created centrally in StackModifierCreator
  private playerViewModifier?: Modifier<boolean>;
  private underwaterViewModifier?: Modifier<boolean>;
  private fogViewModifier?: Modifier<number>;

  // Ambient audio modifier (priority 10 - death music)
  private deathAmbientAudioModifier?: Modifier<string>;

  // Status effects
  private statusEffects: Map<string, StatusEffect> = new Map();
  private effectTimers: Map<string, number> = new Map();

  // Vitals (health, hunger, mana, etc.)
  private vitals: Map<string, VitalsData> = new Map();
  private vitalsUpdateInterval?: number;
  private lastVitalsUpdate: number = Date.now();

  // DEAD mode state (active on disconnect or death)
  private isDead: boolean = false;

  /**
   * Get the view mode stack (ego vs third-person)
   * Stack is created centrally in StackModifierCreator
   */
  get viewModeStack(): ModifierStack<boolean> | undefined {
    return this.appContext.services.modifier?.getModifierStack<boolean>(
      StackName.PLAYER_VIEW_MODE
    );
  }

  // Movement state (WALK, SPRINT, JUMP, FALL, FLY, SWIM, CROUCH, RIDING)
  // Stack is created centrally in StackModifierCreator
  private currentMovementState: PlayerMovementState = PlayerMovementState.WALK;
  private playerMovementModifier?: Modifier<PlayerMovementState>;

  /**
   * Get the movement state stack
   * Stack is created centrally in StackModifierCreator
   */
  get movementStateStack(): ModifierStack<PlayerMovementState> | undefined {
    return this.appContext.services.modifier?.getModifierStack<PlayerMovementState>(
      StackName.PLAYER_MOVEMENT_STATE
    );
  }

  // Third-person model rendering
  private thirdPersonMesh?: any; // AbstractMesh from Babylon.js
  private thirdPersonAnimations?: any[]; // AnimationGroup[]

  // Character rotation (independent from camera in third-person)
  private characterYaw: number = 0; // Degrees
  private targetCharacterYaw: number = 0; // Target yaw for smooth rotation

  // Pose/Animation state
  private lastMovementTime: number = 0; // Last time player moved
  private idleDelay: number = 500; // ms before switching to IDLE pose
  private isMoving: boolean = false; // Movement state (with hysteresis)
  private isJumping: boolean = false; // Jump state (set by jump event)
  private jumpDuration: number = 300; // ms to stay in JUMP pose
  private jumpStartTime: number = 0; // When jump was triggered

  // Position update sender (client -> server)
  private positionUpdateInterval?: NodeJS.Timeout;
  private lastSentPosition?: { x: number; y: number; z: number };
  private lastSentRotation?: { y: number; p: number };
  private lastSentVelocity?: { x: number; y: number; z: number };
  private lastSentPose?: number;
  private readonly POSITION_UPDATE_INTERVAL_MS = 100; // Send updates every 100ms
  private readonly POSITION_CHANGE_THRESHOLD = 0.01; // Min position change to send update (blocks)
  private readonly ROTATION_CHANGE_THRESHOLD = 0.5; // Min rotation change to send update (degrees)
  private readonly VELOCITY_CHANGE_THRESHOLD = 0.01; // Min velocity change to send update (blocks/s)

  constructor(appContext: AppContext, cameraService: CameraService) {
    this.appContext = appContext;
    this.cameraService = cameraService;

    // Get PlayerInfo from AppContext (must be initialized before PlayerService)
    if (!appContext.playerInfo) {
      throw new Error('PlayerInfo must be initialized in AppContext before creating PlayerService');
    }

    // Get initial state values for WALK state
    const initialStateValues = getStateValues(appContext.playerInfo, 'walk');

    // Calculate initial player position from worldInfo.entryPoint.area
    const initialPosition = this.calculateInitialPlayerPosition(appContext);

    // Create player entity (starts in Walk mode)
    this.playerEntity = {
      entityId: 'player',
      position: initialPosition,
      velocity: Vector3.Zero(),
      rotation: Vector3.Zero(), // Rotation in radians (x: pitch, y: yaw, z: roll)
      movementMode: 'walk' as MovementMode,
      wishMove: Vector3.Zero(), // Movement intention
      grounded: false, // Is on ground
      onSlope: false, // Is on slope
      inWater: false, // Is in water
      autoJump: 0, // Can trigger auto-jump
      jumpRequested: false, // Jump requested this frame
      lastBlockPos: initialPosition.clone(), // Last block position for cache invalidation
      playerInfo: appContext.playerInfo,

      // Initialize fall tracking (added for FALL state detection)
      fallDistance: 0,
      wasFalling: false,

      // Initialize state-dependent cached values (from stateValues matrix)
      effectiveSpeed: initialStateValues.effectiveMoveSpeed,
      effectiveJumpSpeed: initialStateValues.effectiveJumpSpeed,
      effectiveTurnSpeed: initialStateValues.effectiveTurnSpeed,
      cachedEyeHeight: initialStateValues.eyeHeight,
      cachedSelectionRadius: initialStateValues.selectionRadius,
    };

    // Initialize last position for change detection
    this.lastPosition = {
      x: this.playerEntity.position.x,
      y: this.playerEntity.position.y,
      z: this.playerEntity.position.z,
    };

    // Initialize view mode modifiers
    // Stack is created centrally in StackModifierCreator
    // Modifiers are created here on first access
    this.initializeViewModeModifiers();

    // Initialize movement state modifier
    this.initializeMovementStateModifier();

    // Set default movement mode from WorldInfo (if specified)
    const defaultMode = appContext.worldInfo?.settings?.defaultMovementMode;
    if (defaultMode) {
      try {
        const movementState = PlayerMovementState[defaultMode as keyof typeof PlayerMovementState];
        if (movementState) {
          this.currentMovementState = movementState;
          logger.debug('Default movement mode set from WorldInfo', { defaultMode, movementState });
        }
      } catch (error) {
        logger.warn('Invalid defaultMovementMode in WorldInfo', { defaultMode });
      }
    }

    // Initialize player position and sync camera
    this.syncCameraToPlayer();

    // Set up jump event callback
    (this.playerEntity as any).onJump = () => {
      this.onPlayerJump();
    };

    // Load third-person model if configured (async, doesn't block initialization)
    if (appContext.playerInfo.thirdPersonModelId) {
      this.loadThirdPersonModel(appContext.playerInfo.thirdPersonModelId).catch(error => {
        logger.error('Failed to load third-person model during init', {}, error as Error);
      });
    }

    // Start position update sender (sends position to server every 100ms)
    this.startPositionUpdateSender();

    // Start vitals update loop (regen/degen every 100ms)
    this.startVitalsUpdate();

    logger.debug('PlayerService initialized', {
      position: this.playerEntity.position,
      movementMode: this.playerEntity.movementMode,
      displayName: this.playerEntity.playerInfo.title,
    });
  }

  /**
   * Set physics service (called after PhysicsService is created)
   */
  setPhysicsService(physicsService: PhysicsService): void {
    this.physicsService = physicsService;
    this.physicsService.registerEntity(this.playerEntity);
    logger.debug('PhysicsService set and player registered');
  }

  /**
   * Set entity render service (called after EntityRenderService is created)
   */
  setEntityRenderService(entityRenderService: EntityRenderService): void {
    this.entityRenderService = entityRenderService;
    logger.debug('EntityRenderService set');
  }

  /**
   * Get player position
   */
  getPosition(): Vector3 {
    return this.playerEntity.position.clone();
  }

  /**
   * Set player position
   *
   * @param x World X coordinate
   * @param y World Y coordinate
   * @param z World Z coordinate
   */
  setPosition(x: number, y: number, z: number): void {
    this.playerEntity.position.set(x, y, z);
    this.syncCameraToPlayer();
    this.emit('position:changed', this.playerEntity.position.clone());
  }

  /**
   * Move player forward/backward relative to camera direction
   *
   * @param distance Distance to move (positive = forward, negative = backward)
   */
  moveForward(distance: number): void {
    if (!this.physicsService) return;

    const cameraRotation = this.cameraService.getRotation();
    this.physicsService.moveForward(
      this.playerEntity,
      distance,
      cameraRotation.y, // yaw
      cameraRotation.x  // pitch
    );

    this.syncCameraToPlayer();
    this.emit('position:changed', this.playerEntity.position.clone());
  }

  /**
   * Move player left/right relative to camera direction
   *
   * @param distance Distance to move (positive = right, negative = left)
   */
  moveRight(distance: number): void {
    if (!this.physicsService) return;

    const cameraRotation = this.cameraService.getRotation();
    this.physicsService.moveRight(
      this.playerEntity,
      distance,
      cameraRotation.y // yaw
    );

    this.syncCameraToPlayer();
    this.emit('position:changed', this.playerEntity.position.clone());
  }

  /**
   * Move player up/down (Fly mode only)
   *
   * @param distance Distance to move (positive = up, negative = down)
   */
  moveUp(distance: number): void {
    if (!this.physicsService) return;

    this.physicsService.moveUp(this.playerEntity, distance);

    this.syncCameraToPlayer();
    this.emit('position:changed', this.playerEntity.position.clone());
  }

  /**
   * Jump (Walk mode only, if on ground)
   */
  jump(): void {
    if (!this.physicsService) return;

    this.physicsService.jump(this.playerEntity);
  }

  /**
   * Rotate camera (controls player look direction)
   *
   * @param deltaPitch Pitch delta in radians
   * @param deltaYaw Yaw delta in radians
   */
  rotate(deltaPitch: number, deltaYaw: number): void {
    this.cameraService.rotate(deltaPitch, deltaYaw);
  }

  /**
   * Update player (physics is handled by PhysicsService)
   *
   * @param deltaTime Time since last frame in seconds
   */
  update(deltaTime: number): void {
    // Physics is now handled by PhysicsService
    // Just sync camera after physics update
    this.syncCameraToPlayer();

    // Update player avatar (via EntityRenderService)
    this.updateAvatar();

    // Emit position change if moved (for chunk loading, etc.)
    const currentPos = this.playerEntity.position;
    if (
      currentPos.x !== this.lastPosition.x ||
      currentPos.y !== this.lastPosition.y ||
      currentPos.z !== this.lastPosition.z
    ) {
      this.lastPosition.x = currentPos.x;
      this.lastPosition.y = currentPos.y;
      this.lastPosition.z = currentPos.z;

      this.emit('position:changed', currentPos.clone());
    }
  }

  /**
   * Sync camera position to player position
   */
  private syncCameraToPlayer(): void {
    const isEgo = this.isEgoView();

    if (isEgo) {
      // Ego-view: Camera at player eye level (cached, state-dependent)
      const eyeHeight = this.playerEntity.cachedEyeHeight;

      this.cameraService.setPosition(
        this.playerEntity.position.x,
        this.playerEntity.position.y + eyeHeight,
        this.playerEntity.position.z
      );
    } else {
      // Third-person: Camera orbits around player (independent rotation)
      this.cameraService.setThirdPersonPosition(
        this.playerEntity.position,
        5.0 // Distance from player
      );
    }
  }

  /**
   * Update player avatar (position, rotation, visibility)
   * Called every frame
   */
  private updateAvatar(): void {
    const playerAvatarEntityId = (this as any).playerAvatarEntityId;
    if (!playerAvatarEntityId || !this.entityRenderService) {
      return; // Avatar not loaded yet
    }

    // Get camera yaw for character rotation (character faces same direction as camera)
    const cameraYaw = this.cameraService.getCameraYaw();

    // Calculate current pose
    const currentPose = this.calculateCurrentPose();

    // Update entity transform via EntityRenderService
    this.entityRenderService.updateEntityTransform(
      playerAvatarEntityId,
      this.playerEntity.position,
      { y: cameraYaw, p: 0 }
    );

    // Update pose (EntityRenderService checks if it changed internally)
    this.entityRenderService.updateEntityPose(
      playerAvatarEntityId,
      currentPose,
      this.getPlayerSpeed()
    );
  }

  /**
   * Called when player jumps (triggered by PhysicsService)
   */
  private onPlayerJump(): void {
    this.isJumping = true;
    this.jumpStartTime = Date.now();
    logger.debug('Player jump triggered');
  }

  /**
   * Calculate current player pose based on movement state
   * Uses hysteresis to prevent flickering between poses
   * Updates PLAYER_POSE stack with calculated value (priority 100 = default)
   * @returns ENTITY_POSES enum value from stack (allows higher-priority overrides)
   */
  private calculateCurrentPose(): number {
    const movementState = this.currentMovementState;

    // Calculate base pose
    let basePose: number;

    // High-priority states override everything (JUMP, FALL)
    if (movementState === PlayerMovementState.JUMP) {
      basePose = ENTITY_POSES.JUMP;
    } else if (movementState === PlayerMovementState.FALL) {
      basePose = ENTITY_POSES.JUMP; // TODO: Create FALL pose or keep as JUMP
    } else {
      // Check if moving (with hysteresis to prevent flickering)
      const velocity = this.playerEntity.velocity;
      const speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

      // Hysteresis thresholds
      const startMovingThreshold = 0.2; // Must reach this speed to start "moving"
      const stopMovingThreshold = 0.05; // Must drop below this to stop "moving"

      // Update movement state with hysteresis
      if (!this.isMoving && speed > startMovingThreshold) {
        this.isMoving = true;
        this.lastMovementTime = Date.now();
      } else if (this.isMoving && speed < stopMovingThreshold) {
        this.isMoving = false;
      }

      // Update last movement time if still moving
      if (this.isMoving) {
        this.lastMovementTime = Date.now();
      }

      // Determine pose based on movement state (only when actually moving)
      if (this.isMoving) {
        switch (movementState) {
          case PlayerMovementState.SPRINT:
            basePose = ENTITY_POSES.RUN;
            break;
          case PlayerMovementState.CROUCH:
            basePose = ENTITY_POSES.CROUCH;
            break;
          case PlayerMovementState.FREE_FLY:
          case PlayerMovementState.FLY:
            basePose = ENTITY_POSES.FLY;
            break;
          case PlayerMovementState.SWIM:
            basePose = ENTITY_POSES.SWIM;
            break;
          case PlayerMovementState.RIDING:
            basePose = ENTITY_POSES.WALK; // TODO: Create RIDING pose
            break;
          case PlayerMovementState.WALK:
          default:
            basePose = ENTITY_POSES.WALK;
            break;
        }
      } else {
        // Player is not moving - check idle timer
        const timeSinceMovement = Date.now() - this.lastMovementTime;
        if (timeSinceMovement < this.idleDelay) {
          // Still in movement pose for a bit (smooth transition to idle)
          basePose = ENTITY_POSES.WALK;
        } else {
          // Player is idle
          basePose = ENTITY_POSES.IDLE;
        }
      }
    }

    // Update PLAYER_POSE stack with calculated base pose (priority 100 = default/lowest)
    const poseStack = this.appContext.services.modifier?.getModifierStack<number>(StackName.PLAYER_POSE);
    if (poseStack) {
      // Update default modifier value (always exists)
      poseStack.getDefaultModifier().setValue(basePose);

      // Return effective value from stack (may be overridden by higher priority)
      return poseStack.getValue();
    }

    // Fallback if stack not available
    return basePose;
  }

  /**
   * Get player movement speed (for animation speed)
   */
  private getPlayerSpeed(): number {
    const velocity = this.playerEntity.velocity;
    return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
  }

  /**
   * Get PlayerEntity (for advanced access)
   */
  getPlayerEntity(): PlayerEntity {
    return this.playerEntity;
  }

  /**
   * Update player info dynamically (for power-ups, equipment, status effects)
   *
   * Updates PlayerInfo and emits 'playerInfo:updated' event so all services
   * can react to the changes (PhysicsService, CameraService, SelectService, etc.)
   *
   * @param updates Partial PlayerInfo with values to update
   */
  updatePlayerInfo(updates: Partial<import('@nimbus/shared').PlayerInfo>): void {
    // Update PlayerInfo
    Object.assign(this.playerEntity.playerInfo, updates);

    // Update state-dependent caches for current movement state
    // When PlayerInfo changes (power-ups, equipment), re-cache current state's values
    const stateKey = movementStateToKey(this.currentMovementState);
    const stateValues = getStateValues(this.playerEntity.playerInfo, stateKey);

    this.playerEntity.effectiveSpeed = stateValues.effectiveMoveSpeed;
    this.playerEntity.effectiveJumpSpeed = stateValues.effectiveJumpSpeed;
    this.playerEntity.effectiveTurnSpeed = stateValues.effectiveTurnSpeed;
    this.playerEntity.cachedEyeHeight = stateValues.eyeHeight;
    this.playerEntity.cachedSelectionRadius = stateValues.selectionRadius;

    logger.debug('PlayerInfo and caches updated', {
      updates,
      currentState: this.currentMovementState,
      speed: this.playerEntity.effectiveSpeed,
    });

    // Emit event so all services can react
    this.emit('playerInfo:updated', this.playerEntity.playerInfo);

    // Sync camera in case eyeHeight changed (direct update for immediate feedback)
    this.syncCameraToPlayer();
  }

  /**
   * Get current move speed
   */
  getMoveSpeed(): number {
    if (!this.physicsService) return 5.0;
    return this.physicsService.getMoveSpeed(this.playerEntity);
  }

  /**
   * Set DEAD mode state
   *
   * When DEAD mode is active:
   * - Physics is disabled
   * - Fog camera effect is enabled
   * - Input is disabled (handled by InputService)
   * - DEAD pose is set via PLAYER_POSE stack
   *
   * @param isDead Whether player is in DEAD mode
   */
  setPlayerDeadState(isDead: boolean): void {
    if (this.isDead === isDead) {
      return; // No change
    }

    this.isDead = isDead;

    logger.debug('Player DEAD mode changed', { isDead });

    const poseStack = this.appContext.services.modifier?.getModifierStack<number>(StackName.PLAYER_POSE);

    if (isDead) {
      // Enter DEAD mode

      // Disable physics
      if (this.physicsService) {
        this.physicsService.disablePhysics();
      }

      // Enable fog camera effect with heavy intensity
      this.setFogViewMode(0.8);

      // Enable death ambient audio (priority 10, overrides environment)
      this.setDeathAmbientAudio(true);

      // Set DEAD pose via stack (high priority so it overrides everything)
      if (poseStack) {
        poseStack.getDefaultModifier().setValue(ENTITY_POSES.DEATH);
      }

    } else {
      // Exit DEAD mode

      // Re-enable physics
      if (this.physicsService) {
        this.physicsService.enablePhysics();
      }

      // Disable fog camera effect
      this.setFogViewMode(0);

      // Disable death ambient audio
      this.setDeathAmbientAudio(false);

      // Set IDLE pose via stack (will be overridden by normal pose calculation)
      if (poseStack) {
        poseStack.getDefaultModifier().setValue(ENTITY_POSES.IDLE);
      }
    }

    // Emit event for other services (e.g., InputService)
    this.emit('player:deadStateChanged', isDead);
  }

  /**
   * Check if player is in DEAD mode
   */
  isPlayerDead(): boolean {
    return this.isDead;
  }

  /**
   * Check if player is on ground
   */
  isPlayerOnGround(): boolean {
    return this.playerEntity.grounded;
  }

  /**
   * Get current movement mode
   */
  getMovementMode(): MovementMode {
    return this.playerEntity.movementMode;
  }

  /**
   * Set movement mode
   */
  setMovementMode(mode: MovementMode): void {
    if (!this.physicsService) return;
    this.physicsService.setMovementMode(this.playerEntity, mode);
  }

  /**
   * Toggle between Walk and Fly modes
   */
  toggleMovementMode(): void {
    if (!this.physicsService) return;
    this.physicsService.toggleMovementMode(this.playerEntity);
  }

  /**
   * Add event listener
   *
   * @param event Event name
   * @param listener Event listener function
   */
  on(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event) || [];
    listeners.push(listener);
    this.eventListeners.set(event, listeners);
  }

  /**
   * Remove event listener
   *
   * @param event Event name
   * @param listener Event listener function
   */
  off(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index !== -1) {
        listeners.splice(index, 1);
      }
    }
  }

  /**
   * Emit event
   *
   * @param event Event name
   * @param args Event arguments
   */
  private emit(event: string, ...args: any[]): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach((listener) => {
        try {
          listener(...args);
        } catch (error) {
          logger.error('Error in event listener', { event }, error as Error);
        }
      });
    }
  }

  /**
   * Initialize view mode modifiers
   * Stack is created centrally in StackModifierCreator
   */
  private initializeViewModeModifiers(): void {
    const stack = this.viewModeStack;
    if (!stack) {
      logger.debug('View mode stack not available yet');
      return;
    }

    // Add player's default preference if not already added
    if (!this.playerViewModifier) {
      this.playerViewModifier = stack.addModifier(true, 100); // Priority 100 (low)
      logger.debug('Player view modifier created');
    }

    // Create underwater modifier if not already added
    if (!this.underwaterViewModifier) {
      this.underwaterViewModifier = stack.addModifier(true, 10); // Priority 10 (high)
      this.underwaterViewModifier.setEnabled(false); // Disabled initially
      logger.debug('Underwater view modifier created');
    }

    // Initialize fog view modifier
    this.initializeFogViewModifier();
  }

  /**
   * Initialize fog view modifier
   * Stack is created centrally in StackModifierCreator
   */
  private initializeFogViewModifier(): void {
    const stack = this.appContext.services.modifier?.getModifierStack<number>(
      StackName.FOG_VIEW_MODE
    );

    if (!stack) {
      logger.warn('Fog view mode stack not available yet');
      return;
    }

    // Create fog modifier if not already added
    if (!this.fogViewModifier) {
      this.fogViewModifier = stack.addModifier(0.8, 10); // Priority 10 (high), default intensity 0.8
      this.fogViewModifier.setEnabled(false); // Disabled initially
      logger.debug('Fog view modifier created');
    }
  }

  /**
   * Initialize death ambient audio modifier
   * Stack is created centrally in StackModifierCreator
   */
  private initializeDeathAmbientAudioModifier(): void {
    const stack = this.appContext.services.modifier?.getModifierStack<string>(
      StackName.AMBIENT_AUDIO
    );

    if (!stack) {
      logger.warn('Ambient audio stack not available yet');
      return;
    }

    // Create death ambient audio modifier if not already added
    if (!this.deathAmbientAudioModifier) {
      // Get death ambient audio path from WorldInfo settings (optional)
      const settings = this.appContext.worldInfo?.settings as any;
      const deadAmbientAudio = settings?.deadAmbientAudio || '';

      this.deathAmbientAudioModifier = stack.addModifier(deadAmbientAudio, 10); // Priority 10 (high)
      this.deathAmbientAudioModifier.setEnabled(false); // Disabled initially (only on death)
      logger.debug('Death ambient audio modifier created', { deadAmbientAudio });
    }
  }

  /**
   * Enable or disable death ambient audio
   * @param enabled Whether to play death music
   */
  private setDeathAmbientAudio(enabled: boolean): void {
    this.initializeDeathAmbientAudioModifier();

    if (this.deathAmbientAudioModifier) {
      this.deathAmbientAudioModifier.setEnabled(enabled);
      logger.debug('Death ambient audio ' + (enabled ? 'enabled' : 'disabled'));
    }
  }

  /**
   * Initialize movement state modifier
   * Stack is created centrally in StackModifierCreator
   */
  private initializeMovementStateModifier(): void {
    const stack = this.movementStateStack;
    if (!stack) {
      logger.warn('Movement state stack not available yet');
      return;
    }

    // Add player's movement modifier (for FLY, SPRINT, CROUCH, etc.)
    if (!this.playerMovementModifier) {
      this.playerMovementModifier = stack.addModifier(PlayerMovementState.WALK, 100); // Priority 100
      logger.debug('Player movement modifier created');
    }
  }

  /**
   * Set player movement state (FLY, WALK, SPRINT, CROUCH)
   * Used by input handlers to change movement mode
   */
  setMovementState(state: PlayerMovementState): void {
    this.initializeMovementStateModifier();

    if (!this.playerMovementModifier) {
      logger.warn('Player movement modifier not available');
      return;
    }

    this.playerMovementModifier.setValue(state);
    logger.debug('Player movement state set', { state });
  }

  /**
   * Get current movement state
   */
  getMovementState(): PlayerMovementState {
    return this.currentMovementState;
  }

  /**
   * Called when movement state changes (from modifier stack)
   * Public because it's called from StackModifierCreator callback
   */
  onMovementStateChanged(newState: PlayerMovementState): void {
    const oldState = this.currentMovementState;
    if (oldState === newState) {
      return; // No change
    }

    this.currentMovementState = newState;

    // Synchronize entity.movementMode with PlayerMovementState
    // This is critical for PhysicsService to choose the correct controller
    const movementModeMap: Record<PlayerMovementState, MovementMode> = {
      [PlayerMovementState.WALK]: 'walk',
      [PlayerMovementState.SPRINT]: 'sprint',
      [PlayerMovementState.CROUCH]: 'crouch',
      [PlayerMovementState.SWIM]: 'swim',
      [PlayerMovementState.FREE_FLY]: 'free_fly', // FREE_FLY: no physics, no collisions
      [PlayerMovementState.FLY]: 'fly',           // FLY: physics enabled, no gravity
      [PlayerMovementState.RIDING]: 'walk',       // No riding mode yet
      [PlayerMovementState.JUMP]: 'walk',         // Jump uses walk physics
      [PlayerMovementState.FALL]: 'walk',         // Fall uses walk physics
    };

    const newMode = movementModeMap[newState];
    if (this.playerEntity.movementMode !== newMode) {
      this.playerEntity.movementMode = newMode;

      // Update cached state-dependent values
      const stateKey = movementStateToKey(newState);
      const stateValues = getStateValues(this.playerEntity.playerInfo, stateKey);

      this.playerEntity.effectiveSpeed = stateValues.effectiveMoveSpeed;
      this.playerEntity.effectiveJumpSpeed = stateValues.effectiveJumpSpeed;
      this.playerEntity.effectiveTurnSpeed = stateValues.effectiveTurnSpeed;
      this.playerEntity.cachedEyeHeight = stateValues.eyeHeight;
      this.playerEntity.cachedSelectionRadius = stateValues.selectionRadius;

      // Sync camera immediately to apply new eye height
      this.syncCameraToPlayer();

      // Reset velocity when switching to/from fly modes
      // Prevents "falling while flying" or "flying momentum when landing"
      if (newMode === 'fly' || newMode === 'free_fly' || oldState === PlayerMovementState.FLY || oldState === PlayerMovementState.FREE_FLY) {
        this.playerEntity.velocity.set(0, 0, 0);
      }

      // Set grounded false in fly modes (flying = not on ground)
      if (newMode === 'fly' || newMode === 'free_fly') {
        this.playerEntity.grounded = false;
      }

      logger.debug('Entity state caches updated', {
        playerMovementState: newState,
        entityMovementMode: newMode,
        speed: this.playerEntity.effectiveSpeed,
        jumpSpeed: this.playerEntity.effectiveJumpSpeed,
        eyeHeight: this.playerEntity.cachedEyeHeight,
      });
    }

    // Emit event for other services (PhysicsService, CameraService, etc.)
    const event: PlayerMovementStateChangedEvent = {
      playerId: this.appContext.playerInfo?.playerId ?? 'unknown',
      oldState,
      newState,
    };
    this.emit('movementStateChanged', event);

    // Show notifications for important state changes
    // Skip notifications when coming from FALL state (landing is already logged)
    const notificationService = this.appContext.services.notification;
    if (notificationService && oldState !== PlayerMovementState.FALL) {
      let message: string | null = null;

      if (newState === PlayerMovementState.FREE_FLY && oldState !== PlayerMovementState.FREE_FLY) {
        message = 'Free Flight Mode Activated';
      } else if (newState === PlayerMovementState.FLY && oldState !== PlayerMovementState.FLY) {
        message = 'Flight Mode Activated (Physics)';
      } else if ((oldState === PlayerMovementState.FLY || oldState === PlayerMovementState.FREE_FLY) && newState === PlayerMovementState.WALK) {
        message = 'Flight Mode Deactivated';
      } else if (newState === PlayerMovementState.SPRINT) {
        message = 'Sprinting';
      } else if (newState === PlayerMovementState.CROUCH) {
        message = 'Crouching';
      } else if (newState === PlayerMovementState.WALK) {
        message = 'Walking';
      } else if (newState === PlayerMovementState.SWIM && oldState !== PlayerMovementState.SWIM) {
        message = 'Swimming';
      }

      if (message) {
        notificationService.newNotification(NotificationType.SYSTEM_INFO, 'System', message);
      }
    }

    logger.debug('Movement state changed', { oldState, newState });
  }

  /**
   * Toggle view mode (ego vs third-person)
   * Called by F5 key handler
   */
  toggleViewMode(): void {
    // Ensure modifiers are initialized
    this.initializeViewModeModifiers();

    if (!this.viewModeStack || !this.playerViewModifier) {
      logger.warn('View mode stack not available');
      return;
    }

    // Toggle player's preference (priority 100)
    const currentEgo = this.playerViewModifier.getValue();
    this.playerViewModifier.setValue(!currentEgo);

    logger.debug('Player toggled view mode', {
      from: currentEgo ? 'ego' : 'third-person',
      to: !currentEgo ? 'ego' : 'third-person',
    });
  }

  /**
   * Force ego-view (used for underwater auto-switch)
   *
   * @param underwater True if underwater, false if surfaced
   */
  setUnderwaterViewMode(underwater: boolean): void {
    // Ensure modifiers are initialized
    this.initializeViewModeModifiers();

    if (!this.underwaterViewModifier) {
      return;
    }

    // Simply enable/disable the underwater modifier instead of creating/destroying it
    this.underwaterViewModifier.setEnabled(underwater);

    logger.debug('Underwater ego-view modifier', { enabled: underwater });
  }

  /**
   * Set fog view mode with intensity
   *
   * Enables/disables fog effects around the camera (used for DEAD mode).
   *
   * @param intensity Fog intensity (0 = disabled, 0.1-1.0 = intensity level)
   */
  setFogViewMode(intensity: number): void {
    // Ensure modifiers are initialized
    this.initializeFogViewModifier();

    if (!this.fogViewModifier) {
      logger.warn('Fog view modifier not available');
      return;
    }

    // Update intensity value
    this.fogViewModifier.setValue(intensity);

    // Enable/disable based on intensity
    this.fogViewModifier.setEnabled(intensity > 0);

    logger.debug('Fog view modifier', { intensity, enabled: intensity > 0 });
  }

  /**
   * Get current view mode
   */
  isEgoView(): boolean {
    return this.viewModeStack?.currentValue ?? true;
  }

  /**
   * Called when view mode changes (from modifier stack)
   * Public because it's called from StackModifierCreator callback
   */
  onViewModeChanged(isEgo: boolean): void {
    logger.debug('View mode changed', { isEgo });

    if (isEgo) {
      // Switch to ego-view (first-person)
      this.hideThirdPersonModel();
    } else {
      // Switch to third-person
      this.showThirdPersonModel();
    }

    // Update camera position
    this.syncCameraToPlayer();
  }

  /**
   * Load and show third-person model
   */
  private async showThirdPersonModel(): Promise<void> {
    const modelId = this.playerEntity.playerInfo.thirdPersonModelId;
    if (!modelId) {
      logger.warn('No third-person model ID configured in PlayerInfo');
      return;
    }

    const playerAvatarEntityId = (this as any).playerAvatarEntityId;

    // If already loaded, just show it via EntityRenderService
    if (playerAvatarEntityId && this.entityRenderService) {
      // Show entity via visibility event
      this.entityRenderService.setEntityVisibility(playerAvatarEntityId, true);
      logger.debug('Third-person model shown via EntityRenderService');
      return;
    }

    // Not loaded yet - load it (lazy loading)
    await this.loadThirdPersonModel(modelId);
  }

  /**
   * Hide third-person model
   */
  private hideThirdPersonModel(): void {
    const playerAvatarEntityId = (this as any).playerAvatarEntityId;
    if (!playerAvatarEntityId || !this.entityRenderService) {
      return;
    }

    // Hide entity via visibility event
    this.entityRenderService.setEntityVisibility(playerAvatarEntityId, false);
    logger.debug('Third-person model hidden via EntityRenderService');
  }

  /**
   * Load third-person model from entity model ID
   * Delegates to EntityRenderService for rendering
   */
  private async loadThirdPersonModel(modelId: string): Promise<void> {
    try {
      logger.debug('Loading player avatar via EntityRenderService', { modelId });

      // Get entity model
      const entityService = this.appContext.services.entity;
      if (!entityService) {
        logger.error('EntityService not available');
        return;
      }

      const entityModel = await entityService.getEntityModel(modelId);
      if (!entityModel) {
        logger.error('Entity model not found', { modelId });
        return;
      }

      // Check EntityRenderService
      if (!this.entityRenderService) {
        logger.error('EntityRenderService not available');
        return;
      }

      // Create Entity for player avatar
      const playerAvatarEntity: any = {
        id: '@player_avatar', // Special ID for player avatar
        name: this.playerEntity.playerInfo.title,
        model: modelId,
        modelModifier: {},
        movementType: 'dynamic' as const,
        controlledBy: 'player', // Mark as player-controlled
        solid: false,
        interactive: false,
      };

      // Create ClientEntity
      const clientEntity = createClientEntity(
        playerAvatarEntity,
        entityModel,
        this.playerEntity.position,
        { y: 0, p: 0 }
      );

      logger.debug('Created ClientEntity for player avatar', {
        entityId: clientEntity.id,
        position: clientEntity.currentPosition,
      });

      // Register in EntityService cache
      (entityService as any).entityCache.set(clientEntity.id, clientEntity);

      // Render model directly via EntityRenderService
      // First trigger pathway to load the model
      const initialPathway = {
        entityId: clientEntity.id,
        startAt: Date.now(),
        waypoints: [{
          timestamp: Date.now(),
          target: { x: this.playerEntity.position.x, y: this.playerEntity.position.y, z: this.playerEntity.position.z },
          rotation: { y: 0, p: 0 },
          pose: 0 // IDLE
        }],
      };

      await this.entityRenderService.updateEntityPathway(initialPathway);

      logger.debug('Player avatar model loaded via EntityRenderService');

      // Now update transform directly to set initial position
      this.entityRenderService.updateEntityTransform(
        clientEntity.id,
        this.playerEntity.position,
        { y: 0, p: 0 }
      );

      logger.debug('Player avatar transform set');

      // Store entity ID and ClientEntity for later updates
      (this as any).playerAvatarEntityId = '@player_avatar';
      (this as any).playerAvatarClientEntity = clientEntity;

      // Initially hide in ego-mode (if we're starting in ego-mode)
      if (this.isEgoView()) {
        this.entityRenderService.setEntityVisibility('@player_avatar', false);
        logger.debug('Player avatar initially hidden (ego-mode)');
      }

      logger.debug('Player avatar loaded successfully');
    } catch (error) {
      logger.error('Failed to load player avatar via EntityRenderService', { modelId }, error as Error);
    }
  }

  /**
   * Start position update sender
   * Sends position updates to server every 100ms (if changed)
   */
  private startPositionUpdateSender(): void {
    // Clear existing interval if any
    if (this.positionUpdateInterval) {
      clearInterval(this.positionUpdateInterval);
    }

    // Start sending position updates every 100ms
    this.positionUpdateInterval = setInterval(() => {
      this.sendPositionUpdateIfChanged();
    }, this.POSITION_UPDATE_INTERVAL_MS);

    logger.debug('Position update sender started', {
      intervalMs: this.POSITION_UPDATE_INTERVAL_MS,
    });
  }

  /**
   * Stop position update sender
   */
  private stopPositionUpdateSender(): void {
    if (this.positionUpdateInterval) {
      clearInterval(this.positionUpdateInterval);
      this.positionUpdateInterval = undefined;
      logger.debug('Position update sender stopped');
    }
  }

  /**
   * Send position update to server if position/rotation/velocity changed
   */
  private sendPositionUpdateIfChanged(): void {
    const networkService = this.appContext.services.network;
    if (!networkService || !networkService.isConnected()) {
      return; // Not connected, skip update
    }

    const currentPosition = this.playerEntity.position;
    const currentVelocity = this.playerEntity.velocity;
    // Get camera rotation in degrees (only yaw, no pitch for player body rotation)
    const cameraYawDegrees = this.cameraService.getCameraYaw();
    const currentPose = this.calculateCurrentPose();

    // Check if anything changed significantly
    const positionChanged = !this.lastSentPosition ||
      Math.abs(currentPosition.x - this.lastSentPosition.x) > this.POSITION_CHANGE_THRESHOLD ||
      Math.abs(currentPosition.y - this.lastSentPosition.y) > this.POSITION_CHANGE_THRESHOLD ||
      Math.abs(currentPosition.z - this.lastSentPosition.z) > this.POSITION_CHANGE_THRESHOLD;

    const rotationChanged = !this.lastSentRotation ||
      Math.abs(cameraYawDegrees - this.lastSentRotation.y) > this.ROTATION_CHANGE_THRESHOLD;

    const velocityChanged = !this.lastSentVelocity ||
      Math.abs(currentVelocity.x - this.lastSentVelocity.x) > this.VELOCITY_CHANGE_THRESHOLD ||
      Math.abs(currentVelocity.y - this.lastSentVelocity.y) > this.VELOCITY_CHANGE_THRESHOLD ||
      Math.abs(currentVelocity.z - this.lastSentVelocity.z) > this.VELOCITY_CHANGE_THRESHOLD;

    const poseChanged = this.lastSentPose !== currentPose;

    // Only send update if something changed
    if (!positionChanged && !rotationChanged && !velocityChanged && !poseChanged) {
      return;
    }

    // Calculate target position (prediction for next 200ms based on velocity)
    const predictionTimeMs = 200;
    const predictionTimeSec = predictionTimeMs / 1000;
    const targetPosition = {
      x: currentPosition.x + currentVelocity.x * predictionTimeSec,
      y: currentPosition.y + currentVelocity.y * predictionTimeSec,
      z: currentPosition.z + currentVelocity.z * predictionTimeSec,
    };

    // Normalize velocity for network (divide by base speed to get 0-1 range)
    // This makes the animation speed correct on other clients
    // The raw velocity (5.0 blocks/s) makes animations too fast
    const baseSpeed = 4.5; // Base walk speed for normalization
    const normalizedVelocity = {
      x: currentVelocity.x / baseSpeed,
      y: currentVelocity.y / baseSpeed,
      z: currentVelocity.z / baseSpeed,
    };

    // Build update data
    const updateData: EntityPositionUpdateData = {
      pl: 'player', // Local entity ID (not the unique @player_uuid, just "player" for local reference)
      p: {
        x: currentPosition.x,
        y: currentPosition.y,
        z: currentPosition.z,
      },
      r: {
        y: cameraYawDegrees,
        p: 0, // Player body doesn't pitch up/down, only camera does
      },
      v: normalizedVelocity, // Send normalized velocity (not raw blocks/s)
      po: currentPose,
      ts: Date.now(),
      ta: {
        x: targetPosition.x,
        y: targetPosition.y,
        z: targetPosition.z,
        ts: Date.now() + predictionTimeMs,
      },
    };

    // Send message
    const message: EntityPositionUpdateMessage = {
      t: MessageType.ENTITY_POSITION_UPDATE,
      d: [updateData],
    };

    networkService.send(message);

    // Update last sent values
    this.lastSentPosition = { x: currentPosition.x, y: currentPosition.y, z: currentPosition.z };
    this.lastSentRotation = { y: cameraYawDegrees, p: 0 };
    this.lastSentVelocity = { x: currentVelocity.x, y: currentVelocity.y, z: currentVelocity.z };
    this.lastSentPose = currentPose;

    logger.debug('Position update sent to server', {
      position: updateData.p,
      rotation: updateData.r,
      velocity: updateData.v,
      pose: updateData.po,
      targetPosition: updateData.ta,
    });
  }

  /**
   * Highlight a shortcut slot
   *
   * Emits 'shortcut:highlight' event to trigger visual highlight in UI.
   * If shortcuts are currently displayed, the NotificationService will:
   * - Switch to the appropriate mode if needed (keys/clicks/slots0/slots1)
   * - Highlight the specific slot with animation (yellow border, 1s duration)
   *
   * @param shortcutKey Shortcut key to highlight (e.g., 'key1', 'click2', 'slot5')
   *
   * @example
   * playerService.highlightShortcut('click1'); // Switches to clicks mode and highlights click1
   */
  highlightShortcut(shortcutKey: string): void {
    this.emit('shortcut:highlight', shortcutKey);
    logger.debug('Shortcut highlight requested', { shortcutKey });
  }

  /**
   * Emit shortcut activation event
   *
   * Emits 'shortcut:activated' event for ItemService to handle pose activation.
   *
   * @param shortcutKey Shortcut key (e.g., 'key1', 'click2')
   * @param itemId Item ID from shortcut definition
   * @param target Target object (Block or Entity) from ShortcutService
   * @param targetPosition Target position from ShortcutService
   */
  emitShortcutActivated(
    shortcutKey: string,
    itemId?: string,
    target?: any,
    targetPosition?: { x: number; y: number; z: number }
  ): void {
    this.emit('shortcut:activated', { shortcutKey, itemId, target, targetPosition });
    logger.debug('Shortcut activated event emitted', { shortcutKey, itemId, hasTarget: !!target });
  }

  /**
   * Emit shortcut started event
   *
   * Emits 'shortcut:started' event when a shortcut script execution begins.
   *
   * @param data Shortcut start data
   */
  emitShortcutStarted(data: {
    shortcutNr: number;
    shortcutKey: string;
    executorId: string;
    itemId?: string;
    exclusive: boolean;
  }): void {
    this.emit('shortcut:started', data);
    logger.debug('Shortcut started event emitted', data);
  }

  /**
   * Emit shortcut ended event
   *
   * Emits 'shortcut:ended' event when a shortcut key is released.
   *
   * @param data Shortcut end data
   */
  emitShortcutEnded(data: {
    shortcutNr: number;
    shortcutKey: string;
    executorId: string;
    duration: number;
  }): void {
    this.emit('shortcut:ended', data);
    logger.debug('Shortcut ended event emitted', data);
  }

  // ============================================
  // Status Effects Management
  // ============================================

  /**
   * Add a status effect to the player
   *
   * @param itemId Item ID that defines the effect
   * @param duration Duration in milliseconds (optional, can also come from ItemData)
   * @returns Generated effect ID
   */
  addStatusEffect(itemId: string, duration?: number): string {
    // Generate unique ID
    const effectId = `effect_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;

    const effect: StatusEffect = {
      id: effectId,
      itemId,
      appliedAt: Date.now(),
      duration,
      expiresAt: duration ? Date.now() + duration : undefined,
    };

    this.statusEffects.set(effectId, effect);

    // Set timer for auto-removal if duration specified
    if (duration) {
      const timer = window.setTimeout(() => {
        this.removeStatusEffect(effectId);
      }, duration);
      this.effectTimers.set(effectId, timer);
    }

    // Emit event for UI update
    this.emit('statusEffects:changed', Array.from(this.statusEffects.values()));

    logger.debug('Status effect added', { effectId, itemId, duration });
    return effectId;
  }

  /**
   * Remove a status effect
   *
   * @param effectId Effect ID to remove
   * @returns True if effect was removed, false if not found
   */
  removeStatusEffect(effectId: string): boolean {
    const effect = this.statusEffects.get(effectId);
    if (!effect) {
      return false;
    }

    // Clear timer if exists
    const timer = this.effectTimers.get(effectId);
    if (timer) {
      clearTimeout(timer);
      this.effectTimers.delete(effectId);
    }

    // Remove effect
    this.statusEffects.delete(effectId);

    // Emit event for UI update
    this.emit('statusEffects:changed', Array.from(this.statusEffects.values()));

    logger.debug('Status effect removed', { effectId, itemId: effect.itemId });
    return true;
  }

  /**
   * Get all active status effects
   *
   * @returns Array of active status effects
   */
  getStatusEffects(): StatusEffect[] {
    return Array.from(this.statusEffects.values());
  }

  /**
   * Check if player has a specific effect
   *
   * @param itemId Item ID of the effect
   * @returns True if player has this effect
   */
  hasStatusEffect(itemId: string): boolean {
    for (const effect of this.statusEffects.values()) {
      if (effect.itemId === itemId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Clear all status effects
   */
  clearAllStatusEffects(): void {
    // Clear all timers
    this.effectTimers.forEach((timer) => clearTimeout(timer));
    this.effectTimers.clear();

    // Clear effects
    this.statusEffects.clear();

    // Emit event for UI update
    this.emit('statusEffects:changed', []);

    logger.debug('All status effects cleared');
  }

  // ============================================
  // Vitals Management
  // ============================================

  /**
   * Start vitals update loop (regen/degen)
   */
  startVitalsUpdate(): void {
    if (this.vitalsUpdateInterval) {
      return; // Already running
    }

    // Update vitals every 100ms for smooth regeneration
    this.vitalsUpdateInterval = window.setInterval(() => {
      this.updateVitals();
    }, 100);

    logger.debug('Vitals update loop started');
  }

  /**
   * Stop vitals update loop
   */
  stopVitalsUpdate(): void {
    if (this.vitalsUpdateInterval) {
      clearInterval(this.vitalsUpdateInterval);
      this.vitalsUpdateInterval = undefined;
      logger.debug('Vitals update loop stopped');
    }
  }

  /**
   * Update vitals (regen/degen/extend expiry)
   */
  private updateVitals(): void {
    const now = Date.now();
    const deltaTime = (now - this.lastVitalsUpdate) / 1000; // Convert to seconds
    this.lastVitalsUpdate = now;

    let changed = false;

    for (const vital of this.vitals.values()) {
      // Check extend expiry
      if (vital.extendExpiry && now >= vital.extendExpiry) {
        vital.extended = 0;
        vital.extendExpiry = undefined;
        changed = true;
      }

      // Apply regeneration
      if (vital.regenRate > 0) {
        const maxValue = vital.max + (vital.extended || 0);
        const newValue = Math.min(maxValue, vital.current + vital.regenRate * deltaTime);
        if (newValue !== vital.current) {
          vital.current = newValue;
          changed = true;
        }
      }

      // Apply degeneration
      if (vital.degenRate > 0) {
        const newValue = Math.max(0, vital.current - vital.degenRate * deltaTime);
        if (newValue !== vital.current) {
          vital.current = newValue;
          changed = true;
        }
      }
    }

    // Emit event if vitals changed
    if (changed) {
      this.emit('vitals:changed', Array.from(this.vitals.values()));
    }
  }

  /**
   * Add or update a vital
   *
   * @param vital Vital data
   */
  setVital(vital: VitalsData): void {
    this.vitals.set(vital.type, vital);
    this.emit('vitals:changed', Array.from(this.vitals.values()));
    logger.debug('Vital set', { type: vital.type, current: vital.current, max: vital.max });
  }

  /**
   * Update vital value
   *
   * @param type Vital type
   * @param current New current value
   */
  updateVitalValue(type: string, current: number): void {
    const vital = this.vitals.get(type);
    if (!vital) {
      logger.warn('Vital not found', { type });
      return;
    }

    const maxValue = vital.max + (vital.extended || 0);
    vital.current = Math.max(0, Math.min(maxValue, current));
    this.emit('vitals:changed', Array.from(this.vitals.values()));
  }

  /**
   * Get vital by type
   *
   * @param type Vital type
   * @returns VitalsData or undefined
   */
  getVital(type: string): VitalsData | undefined {
    return this.vitals.get(type);
  }

  /**
   * Get all vitals
   *
   * @returns Array of all vitals
   */
  getVitals(): VitalsData[] {
    return Array.from(this.vitals.values());
  }

  /**
   * Remove a vital
   *
   * @param type Vital type to remove
   * @returns True if removed, false if not found
   */
  removeVital(type: string): boolean {
    const removed = this.vitals.delete(type);
    if (removed) {
      this.emit('vitals:changed', Array.from(this.vitals.values()));
      logger.debug('Vital removed', { type });
    }
    return removed;
  }

  /**
   * Clear all vitals
   */
  clearAllVitals(): void {
    this.vitals.clear();
    this.emit('vitals:changed', []);
    logger.debug('All vitals cleared');
  }

  /**
   * Calculate initial player position from worldInfo.entryPoint.area
   * Returns a random position within the area bounds
   * Falls back to (0, 64, 0) if no entryPoint is defined
   */
  private calculateInitialPlayerPosition(appContext: AppContext): Vector3 {
    const entryPoint = appContext.worldInfo?.entryPoint;

    // Fallback to default position if no entryPoint defined
    if (!entryPoint || !entryPoint.area) {
      logger.info('No entryPoint.area defined in WorldInfo, using default position (0, 64, 0)');
      return new Vector3(0, 64, 0);
    }

    const area = entryPoint.area;

    // Generate random position within area bounds (position + random offset within size)
    const randomX = area.position.x + Math.random() * area.size.x;
    const randomY = area.position.y + Math.random() * area.size.y;
    const randomZ = area.position.z + Math.random() * area.size.z;

    const position = new Vector3(randomX, randomY, randomZ);

    logger.info('Player spawned at random position within entryPoint.area', {
      position: { x: position.x, y: position.y, z: position.z },
      area: {
        position: { x: area.position.x, y: area.position.y, z: area.position.z },
        size: { x: area.size.x, y: area.size.y, z: area.size.z }
      }
    });

    return position;
  }

  /**
   * Dispose player service
   */
  dispose(): void {
    // Stop position update sender
    this.stopPositionUpdateSender();

    // Stop vitals update
    this.stopVitalsUpdate();

    // Clear status effect timers
    this.effectTimers.forEach((timer) => clearTimeout(timer));
    this.effectTimers.clear();
    this.statusEffects.clear();

    // Clear vitals
    this.vitals.clear();

    this.eventListeners.clear();

    // Close modifiers
    this.playerViewModifier?.close();
    this.underwaterViewModifier?.close();
    this.fogViewModifier?.close();
    this.playerMovementModifier?.close();

    // Dispose third-person model
    if (this.thirdPersonMesh) {
      this.thirdPersonMesh.dispose();
    }

    logger.debug('PlayerService disposed');
  }
}
