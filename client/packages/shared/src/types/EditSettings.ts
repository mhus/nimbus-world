/**
 * Edit Settings - Configuration for world editing
 *
 * Contains palette and other editor settings.
 * Used by world-control API endpoints.
 */

import type { Block } from './Block';

/**
 * Palette block definition
 *
 * A block that can be selected from the palette for quick placement.
 * Contains complete block data and display information.
 *
 * Note: Server DTO does not include 'id' field - it's generated client-side
 */
export interface PaletteBlockDefinition {
  /**
   * Complete block definition
   * Can be placed/pasted in the world
   */
  block: Block;

  /**
   * Display name for the block (from BlockType description or custom)
   */
  name: string;

  /**
   * Icon URL or texture reference for the palette entry
   * Maps to 'icon' field in server DTO
   */
  icon?: string;
}

/**
 * World Edit Settings
 *
 * Contains all editor configuration for a specific world and user.
 * Stored in Redis and persisted via API.
 */
export interface WWorldEditSettings {
  /**
   * World ID
   */
  worldId: string;

  /**
   * User ID (optional, for user-specific settings)
   */
  userId?: string;

  /**
   * Block palette
   * List of blocks available for quick placement
   */
  palette: PaletteBlockDefinition[];

  /**
   * Last modified timestamp
   */
  lastModified?: number;
}
