/**
 * BlockType REST API DTOs
 *
 * Data Transfer Objects for block type related REST API endpoints
 */

/**
 * Block type options
 */
export interface BlockTypeOptionsDTO {
  /** Is block solid (has collision) */
  solid: boolean;

  /** Is block opaque (blocks light) */
  opaque: boolean;

  /** Is block transparent (renders with transparency) */
  transparent: boolean;

  /** Material type (affects rendering) */
  material: string;
}

/**
 * BlockType DTO (GET /api/worlds/{worldId}/blocktypes/{id})
 * BlockType definition from REST API
 *
 * Note: This is a simplified DTO for REST API responses.
 * The complete BlockType definition with modifiers is in types/BlockType.ts
 */
export interface BlockTypeDTO {
  /** Unique block type ID */
  id: number;

  /** Internal block name (unique identifier) */
  name: string;

  /** Display name (user-facing) */
  displayName: string;

  /** Block shape */
  shape: string;

  /** Texture filename */
  texture: string;

  /** Block type options */
  options: BlockTypeOptionsDTO;

  /** Block hardness (affects mining speed) */
  hardness: number;

  /** Mining time in milliseconds */
  miningtime: number;

  /** Required tool for mining */
  tool: string;

  /** Is block unbreakable */
  unbreakable: boolean;

  /** Is block solid (has collision) */
  solid: boolean;

  /** Is block transparent (renders with transparency) */
  transparent: boolean;

  /** Wind effect leafiness (0-1, affects vegetation sway) */
  windLeafiness: number;

  /** Wind stability (0-1, affects resistance to wind) */
  windStability: number;
}

/**
 * BlockType single response (GET /api/worlds/{worldId}/blocktypes/{id})
 */
export type BlockTypeSingleResponseDTO = BlockTypeDTO;

/**
 * BlockType range response (GET /api/worlds/{worldId}/blocktypes/{from}/{to})
 *
 * Array may have missing IDs if block types are not defined
 */
export type BlockTypeRangeResponseDTO = BlockTypeDTO[];

/**
 * BlockType list response with pagination
 * (GET /api/worlds/{worldId}/blocktypes?query={query}&limit={limit}&offset={offset})
 */
export interface BlockTypeListResponseDTO {
  /** Array of BlockTypes for current page */
  blockTypes: BlockTypeDTO[];

  /** Total count of BlockTypes (for calculating pages) */
  count: number;

  /** Number of items per page (max 200) */
  limit: number;

  /** Current offset (starting position) */
  offset: number;
}
