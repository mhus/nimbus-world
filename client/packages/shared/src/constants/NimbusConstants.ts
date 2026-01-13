/**
 * Nimbus constants
 * Central place for all magic numbers and configuration values
 */

/**
 * Chunk configuration constants
 */
export const ChunkConstants = {
  /** Default chunk size (blocks per side) */
  SIZE_DEFAULT: 16,

  /** Supported chunk sizes (must be power of 2) */
  SUPPORTED_SIZES: [8, 16, 32, 64, 128] as const,

  /** Maximum chunk size */
  SIZE_MAX: 128,

  /** Minimum chunk size */
  SIZE_MIN: 8,
} as const;

/**
 * World configuration constants
 */
export const WorldConstants = {

  /** Default world minimum (Y-axis) */
  WORLD_FROM_MIN: -512,
  WORLD_FROM_MAX: 0,
  WORLD_FROM_DEFAULT: -64,

  WORLD_TO_MIN: -512,
  WORLD_TO_MAX: 0,
  WORLD_TO_DEFAULT: -64,

  /** Default sea level */
  SEA_LEVEL_DEFAULT: 0,

  /** Default ground level */
  GROUND_LEVEL_DEFAULT: 1,

  WORLD_WIDTH_MAX: 65536,
  WORLD_WIDTH_MIN: 256,
  WORLD_WIDTH_DEFAULT: 4096,

  WORLD_LENGTH_MAX: 65536,
  WORLD_LENGTH_MIN: 256,
  WORLD_LENGTH_DEFAULT: 4096,

  DEFAULT_WORLD_STATUS: 0,

} as const;

/**
 * Block constants
 */
export const BlockConstants = {
  /** Air block ID (empty/no block) */
  AIR_BLOCK_ID: 0,

  /** Maximum block type ID (Uint16 max) */
  MAX_BLOCK_TYPE_ID: 65535,

  /** Minimum block type ID */
  MIN_BLOCK_TYPE_ID: 0,

  /** Default block status */
  DEFAULT_STATUS: 0,

  /** Maximum status value (1 byte) */
  MAX_STATUS: 255,

  /** Minimum status value (1 byte) */
  MIN_STATUS: 0,

  /** Maximum offset value (for validation, supports floats) */
  MAX_OFFSET: 1000,

  /** Minimum offset value (for validation, supports floats) */
  MIN_OFFSET: -1000,

  /** Maximum face visibility value (7 bits) */
  MAX_FACE_VISIBILITY: 127,
} as const;

/**
 * Network constants
 */
export const NetworkConstants = {
  /** Ping timeout buffer (milliseconds) */
  PING_TIMEOUT_BUFFER_MS: 10000,

  /** Default ping interval (seconds) */
  PING_INTERVAL_DEFAULT: 30,

  /** Maximum message size (10 MB) */
  MAX_MESSAGE_SIZE: 10 * 1024 * 1024,

  /** WebSocket reconnect delay (milliseconds) */
  RECONNECT_DELAY_MS: 5000,

  /** Maximum reconnect attempts */
  MAX_RECONNECT_ATTEMPTS: 5,

  /** Message ID max length */
  MESSAGE_ID_MAX_LENGTH: 100,
} as const;

/**
 * Entity constants
 */
export const EntityConstants = {

  /** Default player walk speed (blocks/second) */
  PLAYER_WALK_SPEED: 4.3,

  /** Default player sprint speed (blocks/second) */
  PLAYER_SPRINT_SPEED: 5.6,

  /** Default player crouch speed (blocks/second) */
  PLAYER_CROUCH_SPEED: 1.3,

  /** Default player jump height (blocks) */
  PLAYER_JUMP_HEIGHT: 2.25,

  /** Automatic Climb Height in Walk Mode */
  PLAYER_WALK_CLIMB_HEIGHT: 1.00,

  /** Entity ID max length */
  ENTITY_ID_MAX_LENGTH: 100,

  /** Display name max length */
  DISPLAY_NAME_MAX_LENGTH: 100,

  /** Username max length */
  USERNAME_MAX_LENGTH: 50,
} as const;

/**
 * Rendering constants
 */
export const RenderConstants = {
  /** Default render distance (chunks) */
  RENDER_DISTANCE_DEFAULT: 8,

  /** Maximum render distance (chunks) */
  RENDER_DISTANCE_MAX: 32,

  /** Default target FPS */
  TARGET_FPS: 60,

  /** Maximum chunks rendered per frame */
  MAX_CHUNKS_PER_FRAME: 3,

  /** LOD distance thresholds (as fraction of max distance) */
  LOD_THRESHOLDS: [0.25, 0.5, 0.75] as const,

  /** Maximum vertices per chunk mesh */
  MAX_VERTICES_PER_CHUNK: 100000,
} as const;

/**
 * Animation constants
 */
export const AnimationConstants = {
  /** Maximum animation duration (milliseconds) */
  MAX_DURATION: 60000,

  /** Maximum effects per animation */
  MAX_EFFECTS: 50,

  /** Maximum placeholder count */
  MAX_PLACEHOLDERS: 10,

  /** Default easing type */
  DEFAULT_EASING: 'easeInOut' as const,
} as const;

/**
 * Physics constants
 */
export const PhysicsConstants = {
  /** Gravity (blocks per second squared) */
  GRAVITY: 20,

  /** Terminal velocity (blocks per second) */
  TERMINAL_VELOCITY: 50,

  /** Air drag coefficient */
  AIR_DRAG: 0.98,

  /** Ground friction */
  GROUND_FRICTION: 0.6,

  /** Water drag */
  WATER_DRAG: 0.8,
} as const;

/**
 * Camera constants
 */
export const CameraConstants = {
  /** Near clipping plane */
  NEAR_PLANE: 0.1,

  /** Far clipping plane */
  FAR_PLANE: 1000,

  /** First-person camera settings */
  FIRST_PERSON: {
    /** Default field of view (degrees) */
    FOV_DEFAULT: 75,

    /** Minimum FOV */
    FOV_MIN: 60,

    /** Maximum FOV */
    FOV_MAX: 90,

    /** Default mouse sensitivity */
    SENSITIVITY_DEFAULT: 0.1,

    /** Maximum pitch angle (degrees) */
    PITCH_MAX: 89,

    /** Minimum pitch angle (degrees) */
    PITCH_MIN: -89,
  },

  /** Third-person camera settings */
  THIRD_PERSON: {
    /** Default field of view (degrees) */
    FOV_DEFAULT: 60,

    /** Minimum FOV */
    FOV_MIN: 45,

    /** Maximum FOV */
    FOV_MAX: 80,

    /** Default mouse sensitivity */
    SENSITIVITY_DEFAULT: 0.15,

    /** Maximum pitch angle (degrees) */
    PITCH_MAX: 89,

    /** Minimum pitch angle (degrees) */
    PITCH_MIN: -45,

    /** Default camera distance from target */
    DISTANCE_DEFAULT: 5,

    /** Minimum camera distance */
    DISTANCE_MIN: 2,

    /** Maximum camera distance */
    DISTANCE_MAX: 15,

    /** Camera height offset from target */
    HEIGHT_OFFSET: 1.5,
  },
} as const;

/**
 * Collection size limits
 */
export const LimitConstants = {
  /** Maximum blocks in update message */
  MAX_BLOCKS_PER_MESSAGE: 10000,

  /** Maximum entities in update message */
  MAX_ENTITIES_PER_MESSAGE: 1000,

  /** Maximum chunk coordinates in registration */
  MAX_CHUNK_COORDINATES: 1000,

  /** Maximum notification queue size */
  MAX_NOTIFICATION_QUEUE: 100,

  /** Maximum animation queue size */
  MAX_ANIMATION_QUEUE: 50,
} as const;

/**
 * All constants combined
 */
export const Constants = {
  Chunk: ChunkConstants,
  World: WorldConstants,
  Block: BlockConstants,
  Network: NetworkConstants,
  Entity: EntityConstants,
  Render: RenderConstants,
  Animation: AnimationConstants,
  Physics: PhysicsConstants,
  Camera: CameraConstants,
  Limits: LimitConstants,
} as const;
