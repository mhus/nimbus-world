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
  ENTITY_STATUS_UPDATE = 'e.s.u',

  // Scrawl Script Effects
  EFFECT_TRIGGER = 's.t',
  EFFECT_PARAMETER_UPDATE = 's.u',

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

  // Teams
  TEAM_DATA = 't.d',
  TEAM_STATUS = 't.s',
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
