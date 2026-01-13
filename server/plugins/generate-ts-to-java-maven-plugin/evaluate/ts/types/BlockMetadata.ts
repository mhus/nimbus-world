/**
 * BlockMetadata - Additional metadata for block instances
 *
 * Contains instance-specific data that is not part of the block type definition.
 * This includes organizational data like group membership and display names.
 *
 * Note: Block modifiers have been moved to Block.modifiers for better structure.
 */

export interface BlockMetadata {
  /**
   * Unique identifier for this block instance (e.g., for items)
   */
  id?: string;

  /**
   * Display name for this block instance (e.g., "Hacke", "Schwert")
   * Used for items and special blocks that need custom names
   */
  displayName?: string;

  /**
   * Group ID for organization/categorization
   */
  groupId?: number;
}
