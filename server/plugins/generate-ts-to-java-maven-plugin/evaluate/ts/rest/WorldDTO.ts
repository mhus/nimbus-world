/**
 * World REST API DTOs
 *
 * Data Transfer Objects for world-related REST API endpoints
 */

/**
 * User/Owner information
 */
export interface UserDTO {
  /** User ID */
  user: string;

  /** Display name */
  displayName: string;

  /** Email address (may be empty) */
  email: string;
}

/**
 * 3D Position
 */
export interface Position3D {
  x: number;
  y: number;
  z: number;
}

/**
 * World settings
 */
export interface WorldSettingsDTO {
  /** Maximum number of players */
  maxPlayers: number;

  /** Allow guest access */
  allowGuests: boolean;

  /** Enable PvP (Player vs Player) */
  pvpEnabled: boolean;

  /** Ping interval in seconds */
  pingInterval: number;
}

/**
 * World list item (GET /api/worlds)
 * Minimal world information for world selection
 */
export interface WorldListItemDTO {
  /** Unique world ID */
  worldId: string;

  /** World name */
  name: string;

  /** World description */
  description: string;

  /** World owner information */
  owner: UserDTO;

  /** Creation timestamp (ISO 8601) */
  createdAt: string;

  /** Last update timestamp (ISO 8601) */
  updatedAt: string;
}

/**
 * World list response (GET /api/worlds)
 */
export type WorldListResponseDTO = WorldListItemDTO[];

/**
 * World detail (GET /api/worlds/{worldId})
 * Complete world metadata including boundaries, settings, and configuration
 */
export interface WorldDetailDTO {
  /** Unique world ID */
  worldId: string;

  /** World name */
  name: string;

  /** World description */
  description: string;

  /** World start position (minimum boundary) */
  start: Position3D;

  /** World end position (maximum boundary) */
  stop: Position3D;

  /** Chunk size (blocks per chunk edge) */
  chunkSize: number;

  /** Asset path (relative URL) */
  assetPath: string;

  /** Asset server port (optional, if different from main server) */
  assetPort?: number;

  /** World group ID */
  worldGroupId: string;

  /** Creation timestamp (ISO 8601) */
  createdAt: string;

  /** Last update timestamp (ISO 8601) */
  updatedAt: string;

  /** World owner information */
  owner: UserDTO;

  /** World settings */
  settings: WorldSettingsDTO;
}
