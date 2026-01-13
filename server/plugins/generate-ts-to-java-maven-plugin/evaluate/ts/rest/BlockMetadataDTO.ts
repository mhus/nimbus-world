/**
 * Block Metadata REST API DTOs
 *
 * Data Transfer Objects for block metadata related REST API endpoints
 */

/**
 * Block metadata DTO (GET /api/worlds/{worldId}/blocks/{x}/{y}/{z}/metadata)
 *
 * Returns only metadata not included in the block type definition.
 * The 'groups' field is used for visual grouping to select and manipulate multiple blocks together.
 */
export interface BlockMetadataDTO {
  /** Block X coordinate */
  x: number;

  /** Block Y coordinate */
  y: number;

  /** Block Z coordinate */
  z: number;

  /** Unique block instance ID */
  id: string;

  /** Direct group IDs this block belongs to */
  groups: string[];

  /** Direct group names (corresponding to groups array) */
  groupNames: string[];

  /** Inherited group IDs (from parent blocks/structures) */
  inheritedGroups: string[];

  /** Inherited group names (corresponding to inheritedGroups array) */
  inheritedGroupNames: string[];

  /** Custom display name for this block instance */
  displayName?: string;
}

/**
 * Block metadata response (GET /api/worlds/{worldId}/blocks/{x}/{y}/{z}/metadata)
 */
export type BlockMetadataResponseDTO = BlockMetadataDTO;
