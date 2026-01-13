/**
 * Network message types
 *
 * Shortened names to reduce network traffic
 */

export enum MessageType {
  // Authentication
  LOGIN = 'login',
  LOGIN_RESPONSE = 'loginResponse',
  LOGOUT = 'logout',

  // Connection
  PING = 'p',

  // World
  WORLD_STATUS_UPDATE = 'w.su',

  // Chunks
  CHUNK_REGISTER = 'c.r',
  CHUNK_QUERY = 'c.q',
  CHUNK_UPDATE = 'c.u',

  // Blocks
  BLOCK_UPDATE = 'b.u',
  BLOCK_STATUS_UPDATE = 'b.s.u',
  ITEM_BLOCK_UPDATE = 'b.iu',
  BLOCK_INTERACTION = 'b.int',

  // Entities
  ENTITY_UPDATE = 'e.u',
  ENTITY_CHUNK_PATHWAY = 'e.p',
  ENTITY_POSITION_UPDATE = 'e.p.u',
  ENTITY_INTERACTION = 'e.int.r',

  // Animation
  ANIMATION_START = 'a.s',

  // Effects
  EFFECT_TRIGGER = 'e.t',
  EFFECT_PARAMETER_UPDATE = 'ef.p.u',

  // User/Player
  USER_MOVEMENT = 'u.m',
  PLAYER_TELEPORT = 'p.t',

  // Interaction
  INTERACTION_REQUEST = 'int.r',
  INTERACTION_RESPONSE = 'int.rs',

  // Commands (Client -> Server)
  CMD = 'cmd',
  CMD_MESSAGE = 'cmd.msg',
  CMD_RESULT = 'cmd.rs',

  // Server Commands (Server -> Client)
  SCMD = 'scmd',
  SCMD_RESULT = 'scmd.rs',
}

/**
 * Client type identifier
 */
export enum ClientType {
  WEB = 'web',
  XBOX = 'xbox',
  MOBILE = 'mobile',
  DESKTOP = 'desktop',
}

/**
 * Test enum with numeric values to verify type detection
 * This enum tests that the ts-to-java generator correctly:
 * - Detects numeric enum values
 * - Generates Java enum with int tsIndex
 * - Uses the correct numeric values (not auto-incremented integers)
 */
export enum Priority {
  LOW = 0,
  MEDIUM = 1,
  HIGH = 2,
  CRITICAL = 5,
}

/**
 * Test enum with mixed types to verify string fallback
 * This enum tests that the ts-to-java generator correctly:
 * - Detects mixed string/numeric enum values
 * - Falls back to String tsIndex for mixed types
 * - Converts all values to strings when mixing types
 */
export enum MixedEnum {
  STRING_VAL = 'text',
  NUMERIC_VAL = 42,
  ANOTHER_STRING = 'hello',
}

